package com.bakeprint.pro.print

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.bakeprint.pro.data.Calibration
import com.bakeprint.pro.data.PrinterDevice
import com.bakeprint.pro.label.LabelRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.math.roundToInt

/** One label type to print: a bitmap at 8 dots/mm and how many of it. */
data class PrintItem(val bitmap: Bitmap, val count: Int)

class PrintException(msg: String) : Exception(msg)

/* ------------------------------------------------------------------ */
/* Printer languages                                                  */
/* ------------------------------------------------------------------ */

object Tspl {
    /** TSPL/TSPL2 (Xprinter, TSC, Gprinter, HPRT, iDPRT label printers). BITMAP bits: 0 = black. */
    fun job(bmp: Bitmap, widthMm: Float, heightMm: Float, count: Int, cal: Calibration): ByteArray {
        val out = ByteArrayOutputStream()
        fun cmd(s: String) = out.write((s + "\r\n").toByteArray(Charsets.US_ASCII))
        cmd("SIZE ${fmt(widthMm)} mm,${fmt(heightMm)} mm")
        cmd("GAP ${fmt(cal.gapMm)} mm,0 mm")
        cmd("DIRECTION 1,0")
        cmd("REFERENCE 0,0")
        cmd("OFFSET 0 mm")
        cmd("SHIFT ${(cal.offsetYmm * 8).roundToInt()}")
        cmd("DENSITY ${cal.density.coerceIn(0, 15)}")
        cmd("SPEED ${cal.speed.coerceIn(1, 6)}")
        cmd("CLS")
        val (bpr, data) = LabelRenderer.toMono(bmp)
        for (i in data.indices) data[i] = (data[i].toInt() xor 0xFF).toByte() // TSPL: 0 prints
        val x = (cal.offsetXmm * 8).roundToInt().coerceAtLeast(0)
        out.write("BITMAP $x,0,$bpr,${bmp.height},0,".toByteArray(Charsets.US_ASCII))
        out.write(data)
        out.write("\r\n".toByteArray())
        cmd("PRINT ${count.coerceAtLeast(1)},1")
        return out.toByteArray()
    }

    fun testPage(cal: Calibration, widthMm: Float, heightMm: Float, bmp: Bitmap) = job(bmp, widthMm, heightMm, 1, cal)
    private fun fmt(f: Float) = if (f % 1f == 0f) f.toInt().toString() else "%.1f".format(java.util.Locale.US, f)
}

object EscPos {
    /** ESC/POS raster (GS v 0). Used by many 58/80 mm receipt-and-label printers. Bits: 1 = black. */
    fun job(bmp: Bitmap, count: Int, cal: Calibration): ByteArray {
        val out = ByteArrayOutputStream()
        val (bpr, data) = LabelRenderer.toMono(shift(bmp, cal))
        repeat(count.coerceAtLeast(1)) {
            out.write(byteArrayOf(0x1B, 0x40))                       // init
            out.write(byteArrayOf(0x1B, 0x61, 0x00))                 // left align
            // print in bands of 255 rows for compatibility
            var y = 0
            while (y < bmp.height) {
                val rows = minOf(255, bmp.height - y)
                out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, (bpr and 0xFF).toByte(), (bpr shr 8).toByte(),
                    (rows and 0xFF).toByte(), (rows shr 8).toByte()))
                out.write(data, y * bpr, rows * bpr)
                y += rows
            }
            out.write(byteArrayOf(0x1D, 0x0C))                       // feed to next label (label mode)
            out.write(byteArrayOf(0x1B, 0x64, 0x01))                 // small feed for receipt mode
        }
        return out.toByteArray()
    }

    private fun shift(bmp: Bitmap, cal: Calibration): Bitmap {
        val dx = (cal.offsetXmm * 8).roundToInt(); val dy = (cal.offsetYmm * 8).roundToInt()
        if (dx == 0 && dy == 0) return bmp
        val b = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { drawColor(Color.WHITE); drawBitmap(bmp, dx.toFloat(), dy.toFloat(), null) }
        return b
    }
}

/* ------------------------------------------------------------------ */
/* Transports                                                         */
/* ------------------------------------------------------------------ */

object Transport {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun bluetoothAdapter(ctx: Context): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    fun sendBluetooth(ctx: Context, mac: String, chunks: List<ByteArray>, onChunk: (Int) -> Unit) {
        val adapter = bluetoothAdapter(ctx) ?: throw PrintException("This phone has no Bluetooth.")
        if (!adapter.isEnabled) throw PrintException("Bluetooth is off. Turn it on and try again.")
        val device: BluetoothDevice = try { adapter.getRemoteDevice(mac) } catch (e: Exception) {
            throw PrintException("Printer address is not valid.") }
        try { adapter.cancelDiscovery() } catch (_: Exception) {}
        var socket: BluetoothSocket? = null
        try {
            val first = device.createRfcommSocketToServiceRecord(SPP)
            socket = first
            try {
                first.connect()
            } catch (e: Exception) {
                try { first.close() } catch (_: Exception) {}
                val second = device.createInsecureRfcommSocketToServiceRecord(SPP)
                socket = second
                second.connect()
            }
            val os = socket!!.outputStream
            chunks.forEachIndexed { i, bytes -> writeSlow(os, bytes); onChunk(i + 1) }
            os.flush()
            Thread.sleep(600)
        } catch (e: PrintException) { throw e
        } catch (e: Exception) {
            throw PrintException("Could not connect to the printer. Make sure it is on, paired, and close to the phone.")
        } finally { try { socket?.close() } catch (_: Exception) {} }
    }

    fun sendNetwork(host: String, port: Int, chunks: List<ByteArray>, onChunk: (Int) -> Unit) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 6000)
                s.soTimeout = 10000
                val os = s.getOutputStream()
                chunks.forEachIndexed { i, bytes -> os.write(bytes); os.flush(); onChunk(i + 1) }
                Thread.sleep(300)
            }
        } catch (e: Exception) {
            throw PrintException("Could not reach $host:$port. Check that the printer and phone are on the same Wi-Fi.")
        }
    }

    const val USB_PERMISSION = "com.bakeprint.pro.USB_PERMISSION"

    fun usbDevices(ctx: Context): List<UsbDevice> {
        val m = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return m.deviceList.values.filter { d -> outEndpoint(d) != null }
    }

    private fun outEndpoint(d: UsbDevice): Pair<Int, Int>? {
        for (i in 0 until d.interfaceCount) {
            val itf = d.getInterface(i)
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) return i to e
            }
        }
        return null
    }

    fun requestUsbPermission(ctx: Context, d: UsbDevice) {
        val m = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(USB_PERMISSION).setPackage(ctx.packageName), flags)
        m.requestPermission(d, pi)
    }

    fun sendUsb(ctx: Context, address: String, chunks: List<ByteArray>, onChunk: (Int) -> Unit) {
        val m = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val d = m.deviceList.values.firstOrNull { "${it.vendorId}:${it.productId}" == address }
            ?: throw PrintException("USB printer not found. Connect it with an OTG cable.")
        if (!m.hasPermission(d)) {
            requestUsbPermission(ctx, d)
            throw PrintException("Tap Allow on the USB permission message, then print again.")
        }
        val (ii, ei) = outEndpoint(d) ?: throw PrintException("This USB device is not a printer.")
        val itf = d.getInterface(ii); val ep = itf.getEndpoint(ei)
        val conn = m.openDevice(d) ?: throw PrintException("Could not open the USB printer.")
        try {
            conn.claimInterface(itf, true)
            chunks.forEachIndexed { idx, bytes ->
                var off = 0
                while (off < bytes.size) {
                    val n = minOf(16384, bytes.size - off)
                    val r = conn.bulkTransfer(ep, bytes, off, n, 5000)
                    if (r < 0) throw PrintException("USB printer stopped responding.")
                    off += r
                }
                onChunk(idx + 1)
            }
        } finally { try { conn.releaseInterface(itf) } catch (_: Exception) {}; conn.close() }
    }

    private fun writeSlow(os: OutputStream, bytes: ByteArray) {
        var off = 0
        while (off < bytes.size) {
            val n = minOf(1024, bytes.size - off)
            os.write(bytes, off, n); off += n
            Thread.sleep(8)
        }
    }
}

/* ------------------------------------------------------------------ */
/* PDF + Android print service                                         */
/* ------------------------------------------------------------------ */

object PdfMaker {
    private fun pt(mm: Float) = (mm * 72f / 25.4f).roundToInt()

    /** Each label becomes one page at its exact physical size. */
    fun write(items: List<PrintItem>, widthMm: Float, heightMm: Float, out: OutputStream) {
        val doc = PdfDocument()
        var n = 1
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        items.forEach { item ->
            repeat(item.count) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pt(widthMm), pt(heightMm), n++).create())
                page.canvas.drawBitmap(item.bitmap, null, RectF(0f, 0f, pt(widthMm).toFloat(), pt(heightMm).toFloat()), paint)
                doc.finishPage(page)
            }
        }
        doc.writeTo(out); doc.close()
    }

    fun printWithSystem(ctx: Context, file: File, widthMm: Float, heightMm: Float, jobName: String) {
        val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val mils = { mm: Float -> (mm / 25.4f * 1000f).roundToInt() }
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize("bakeprint_${widthMm}x$heightMm", "Label ${widthMm.toInt()}×${heightMm.toInt()} mm",
                mils(widthMm), mils(heightMm)))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        pm.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cs: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cs?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cs: CancellationSignal?, cb: WriteResultCallback) {
                try {
                    FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) { cb.onWriteFailed(e.message) }
            }
        }, attrs)
    }
}

/* ------------------------------------------------------------------ */
/* One entry point for the app                                         */
/* ------------------------------------------------------------------ */

object PrinterService {
    /**
     * Sends labels to a printer. Blocking: call from Dispatchers.IO.
     * onProgress(sentLabels, totalLabels).
     */
    fun print(ctx: Context, printer: PrinterDevice, items: List<PrintItem>, widthMm: Float, heightMm: Float,
              cal: Calibration, onProgress: (Int, Int) -> Unit) {
        val total = items.sumOf { it.count }
        if (printer.type == "system") {
            val f = File(ctx.cacheDir, "labels.pdf")
            FileOutputStream(f).use { PdfMaker.write(items, widthMm, heightMm, it) }
            onProgress(total, total)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                PdfMaker.printWithSystem(ctx, f, widthMm, heightMm, "BakePrint labels")
            }
            return
        }
        // Each chunk = one label design (TSPL prints `count` copies itself) or one copy (ESC/POS).
        val chunks = mutableListOf<ByteArray>()
        val chunkLabels = mutableListOf<Int>()
        items.forEach { it2 ->
            if (printer.protocol == "escpos") {
                repeat(it2.count) { chunks += EscPos.job(it2.bitmap, 1, cal); chunkLabels += 1 }
            } else {
                // split big counts so progress moves and printer buffer is not overrun
                var left = it2.count
                while (left > 0) {
                    val n = minOf(left, 10)
                    chunks += Tspl.job(it2.bitmap, widthMm, heightMm, n, cal); chunkLabels += n
                    left -= n
                }
            }
        }
        val sentAfter = chunkLabels.runningReduce { a, b -> a + b }
        val cb: (Int) -> Unit = { done -> onProgress(sentAfter[done - 1], total) }
        when (printer.type) {
            "bt" -> Transport.sendBluetooth(ctx, printer.address, chunks, cb)
            "wifi" -> Transport.sendNetwork(printer.address, printer.port, chunks, cb)
            "usb" -> Transport.sendUsb(ctx, printer.address, chunks, cb)
            else -> throw PrintException("Unknown printer type.")
        }
    }
}
