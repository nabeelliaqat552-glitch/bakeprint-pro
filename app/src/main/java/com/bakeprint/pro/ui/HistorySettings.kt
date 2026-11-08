@file:OptIn(ExperimentalLayoutApi::class)

package com.bakeprint.pro.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakeprint.pro.data.*
import com.bakeprint.pro.print.Transport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/* ================= HISTORY ================= */

@Composable
fun HistoryScreen(vm: MainViewModel, nav: Nav) {
    val hist by vm.history.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val list = hist.filter { q.isBlank() || it.productName.contains(q, true) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val groups = list.groupBy { Instant.ofEpochMilli(it.printedAt).atZone(zone).toLocalDate() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenTitle(tr("Purane Labels", "پرانے لیبل"), tr("Printing History", "پرنٹ ہسٹری"))
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = BP.Grey) },
                    placeholder = { Text(tr("Search by product", "پروڈکٹ سے تلاش کریں")) }, shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BP.Gold, unfocusedBorderColor = BP.Line,
                        focusedContainerColor = BP.White, unfocusedContainerColor = BP.White))
            }
        }
        if (list.isEmpty()) item {
            Text(tr("Nothing printed yet. Your print jobs will appear here.", "ابھی کچھ پرنٹ نہیں ہوا۔ پرنٹ کیے گئے لیبل یہاں نظر آئیں گے۔"),
                color = BP.Grey, modifier = Modifier.padding(vertical = 30.dp))
        }
        groups.forEach { (day, recs) ->
            item(key = "d$day") {
                Text(when (day) { today -> tr("TODAY", "آج"); today.minusDays(1) -> tr("YESTERDAY", "کل"); else -> Dates.format(day, s.dateFormat) },
                    style = MaterialTheme.typography.labelSmall, color = BP.Grey, modifier = Modifier.padding(top = 6.dp))
            }
            items(recs, key = { it.id }) { r ->
                val t = Instant.ofEpochMilli(r.printedAt).atZone(zone).toLocalTime()
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.productName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("MFG ${Dates.format(Dates.fromIso(r.mfg), s.dateFormat)}   EXP ${Dates.format(Dates.fromIso(r.exp), s.dateFormat)}",
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("%02d:%02d · %s · %s×%s mm".format(t.hour, t.minute, r.printerName, fmtMm(r.widthMm), fmtMm(r.heightMm)),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${r.quantity * r.copies} ${tr("pcs", "عدد")}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            BigButton(tr("Reprint", "دوبارہ"), { vm.loadFromHistory(r); vm.toast = "Old label loaded. Check the dates."; nav.go("label") },
                                icon = Icons.Rounded.Print, style = BtnStyle.Soft, height = 40)
                        }
                    }
                }
            }
        }
    }
}

/* ================= SETTINGS ================= */

@Composable
fun SettingsScreen(vm: MainViewModel, nav: Nav) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    val prodCount by vm.products.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<String?>(null) }
    var editPrinter by remember { mutableStateOf<PrinterDevice?>(null) }
    var cx by remember(s.calib) { mutableStateOf(fmtMm(s.calib.offsetXmm)) }
    var cy by remember(s.calib) { mutableStateOf(fmtMm(s.calib.offsetYmm)) }
    var cg by remember(s.calib) { mutableStateOf(fmtMm(s.calib.gapMm)) }
    var cd by remember(s.calib) { mutableStateOf(s.calib.density.toString()) }

    val today = LocalDate.now().toString()
    val exportProducts = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportProducts) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backup) }
    val importProducts = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importProducts) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restore) }
    var confirmRestore by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(tr("Settings", "سیٹنگز"), tr("Shop & Printer", "دکان اور پرنٹر"))

        MenuRow(Icons.Rounded.Storefront, tr("Bakery name", "بیکری کا نام"), s.bakeryName.ifBlank { tr("Not set yet", "ابھی نہیں لکھا") }, onClick = nav.editName,
            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = BP.Grey) })
        SoftCard(Modifier.fillMaxWidth()) {
            Text(tr("Language", "زبان"), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("English", s.lang == "en", { vm.set("lang", "en") })
                ChoiceChip("اردو", s.lang == "ur", { vm.set("lang", "ur") })
            }
            Spacer(Modifier.height(12.dp))
            Text(tr("Date format", "تاریخ کا انداز") + " · " + Dates.format(LocalDate.now(), s.dateFormat), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DMY" to "DD-MM-YYYY", "DMYs" to "DD/MM/YYYY", "YMD" to "YYYY-MM-DD").forEach { (k, l) ->
                    ChoiceChip(l, s.dateFormat == k, { vm.set("dateFormat", k) })
                }
            }
        }

        Text(tr("Printers", "پرنٹرز"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        Text(tr("Tap a printer to make it the default. Use ⋮ to change or remove it.", "ڈیفالٹ بنانے کے لیے ٹیپ کریں۔"), style = MaterialTheme.typography.bodySmall)
        printers.forEach { p ->
            MenuRow(printerIcon(p.type), p.name, printerSub(p) + if (p.type != "system") " · ${p.address}" else "", onClick = { vm.setDefaultPrinter(p.id) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p.isDefault) Pill(tr("DEFAULT", "ڈیفالٹ"), BP.Choc, BP.White)
                        if (p.type != "system") IconButton(onClick = { editPrinter = p }) { Icon(Icons.Rounded.MoreVert, null, tint = BP.Grey) }
                    }
                })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(tr("Bluetooth", "بلوٹوتھ"), { sheet = "bt" }, Modifier.weight(1f), Icons.Rounded.Bluetooth, BtnStyle.Outline)
            BigButton(tr("Wi-Fi", "وائی فائی"), { sheet = "wifi" }, Modifier.weight(1f), Icons.Rounded.Wifi, BtnStyle.Outline)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(tr("USB", "یو ایس بی"), { sheet = "usb" }, Modifier.weight(1f), Icons.Rounded.Usb, BtnStyle.Outline)
            BigButton(tr("Test print", "ٹیسٹ پرنٹ"), { vm.defaultPrinter()?.let { vm.testPrint(it) } }, Modifier.weight(1f), Icons.Rounded.Print, BtnStyle.Choc)
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = BP.Gold); Spacer(Modifier.width(8.dp))
                Text(tr("Print calibration", "کیلیبریشن"), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BPTextField(cx, { cx = it }, tr("Shift left/right mm", "دائیں/بائیں mm"), Modifier.weight(1f), numeric = true)
                BPTextField(cy, { cy = it }, tr("Shift up/down mm", "اوپر/نیچے mm"), Modifier.weight(1f), numeric = true)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BPTextField(cg, { cg = it }, tr("Gap between labels mm", "لیبلز کا فاصلہ mm"), Modifier.weight(1f), numeric = true)
                BPTextField(cd, { cd = it }, tr("Darkness 1–15", "گہرائی 1–15"), Modifier.weight(1f), numeric = true)
            }
            Spacer(Modifier.height(10.dp))
            BigButton(tr("Save calibration", "محفوظ کریں"), {
                vm.setCalibration(Calibration(cx.toFloatOrNull() ?: 0f, cy.toFloatOrNull() ?: 0f, cg.toFloatOrNull() ?: 2f,
                    (cd.toIntOrNull() ?: 8).coerceIn(1, 15), s.calib.speed))
                vm.toast = "Calibration saved"
            }, Modifier.fillMaxWidth(), style = BtnStyle.Soft, height = 48)
        }

        var help by remember { mutableStateOf(false) }
        SoftCard(Modifier.fillMaxWidth(), onClick = { help = !help }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = BP.Gold); Spacer(Modifier.width(8.dp))
                Text(tr("Printer not working?", "پرنٹر کام نہیں کر رہا؟"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (help) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = BP.Grey)
            }
            if (help) {
                Spacer(Modifier.height(8.dp))
                listOf(
                    tr("Turn the printer on and load the label roll.", "پرنٹر آن کریں اور لیبل رول لگائیں۔"),
                    tr("Pair the printer first in the phone's Bluetooth settings (PIN is usually 0000 or 1234).", "پہلے فون کی بلوٹوتھ سیٹنگ میں پرنٹر جوڑیں (PIN عام طور پر 0000 یا 1234)۔"),
                    tr("Label printers use TSPL. If nothing prints, edit the printer and switch to ESC/POS.", "لیبل پرنٹر TSPL استعمال کرتے ہیں۔ کچھ نہ چھپے تو ESC/POS آزمائیں۔"),
                    tr("Labels cut in the wrong place? Press FEED once, and check the gap value above.", "لیبل غلط جگہ کٹیں تو FEED دبائیں اور فاصلہ چیک کریں۔"),
                    tr("Small sticker printers take labels up to 58 mm wide.", "چھوٹا پرنٹر 58 mm تک لیبل لیتا ہے۔"),
                    tr("Print too light? Raise Darkness. Shifted? Use the shift values.", "ہلکا پرنٹ؟ گہرائی بڑھائیں۔"),
                    tr("Still stuck? Save a PDF or PNG from the print screen.", "پھر بھی مسئلہ؟ PDF یا PNG محفوظ کریں۔"),
                ).forEachIndexed { i, t -> Text("${i + 1}. $t", fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }

        Text(tr("Backup & Export", "بیک اپ"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        MenuRow(Icons.Rounded.FileUpload, tr("Export product data", "پروڈکٹ ڈیٹا ایکسپورٹ"), "${prodCount.size} ${tr("products · JSON file", "پروڈکٹس")}") {
            exportProducts.launch("bakeprint-products-$today.json") }
        MenuRow(Icons.Rounded.FileDownload, tr("Import product data", "پروڈکٹ ڈیٹا امپورٹ"), tr("Adds to your list", "آپ کی لسٹ میں شامل")) {
            importProducts.launch(arrayOf("application/json", "text/plain", "*/*")) }
        MenuRow(Icons.Rounded.Storage, tr("Database backup", "مکمل بیک اپ"), tr("Products, history, printers, settings", "سب کچھ")) {
            backup.launch("bakeprint-backup-$today.json") }
        MenuRow(Icons.Rounded.Restore, if (confirmRestore) tr("Tap again: this replaces current data", "دوبارہ ٹیپ کریں: موجودہ ڈیٹا بدل جائے گا") else tr("Restore backup", "بیک اپ بحال کریں"),
            tr("Replaces current data", "موجودہ ڈیٹا بدل جائے گا"), danger = confirmRestore) {
            if (confirmRestore) { confirmRestore = false; restore.launch(arrayOf("application/json", "text/plain", "*/*")) } else confirmRestore = true
        }
        Text("BakePrint Pro · " + tr("works fully offline", "مکمل آف لائن"), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
    }

    when (sheet) {
        "bt" -> BluetoothSheet(vm) { sheet = null }
        "wifi" -> PrinterConfigSheet(vm, PrinterDevice(newId(), "", "wifi", "", 9100, "tspl", 108)) { sheet = null }
        "usb" -> UsbSheet(vm) { sheet = null }
    }
    editPrinter?.let { PrinterConfigSheet(vm, it, existing = true) { editPrinter = null } }
}

/* ---------------- add / edit printer ---------------- */

@Composable
fun PrinterConfigSheet(vm: MainViewModel, start: PrinterDevice, existing: Boolean = false, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(start.name) }
    var address by remember { mutableStateOf(start.address) }
    var port by remember { mutableStateOf(start.port.toString()) }
    var size by remember { mutableIntStateOf(start.maxWidthMm) }
    var protocol by remember { mutableStateOf(start.protocol) }
    var armed by remember { mutableStateOf(false) }
    val ipOk = start.type != "wifi" || Regex("^(\\d{1,3}\\.){3}\\d{1,3}$").matches(address.trim())
    BPSheet(onDismiss) {
        SheetTitle(if (existing) tr("Printer settings", "پرنٹر سیٹنگز") else when (start.type) {
            "wifi" -> tr("Add Wi-Fi printer", "وائی فائی پرنٹر"); "usb" -> tr("Add USB printer", "یو ایس بی پرنٹر"); else -> tr("Add Bluetooth printer", "بلوٹوتھ پرنٹر") },
            if (start.type == "wifi") tr("Printer and phone must be on the same Wi-Fi", "پرنٹر اور فون ایک ہی وائی فائی پر ہوں") else start.address, onDismiss)
        BPTextField(name, { name = it }, tr("Name", "نام"), placeholder = tr("e.g. Counter printer", "مثلاً کاؤنٹر پرنٹر"))
        if (start.type == "wifi") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BPTextField(address, { address = it }, tr("IP address", "IP ایڈریس"), Modifier.weight(2f), "192.168.1.50", numeric = true)
                BPTextField(port, { port = it }, "Port", Modifier.weight(1f), numeric = true)
            }
            if (address.isNotBlank() && !ipOk) Text(tr("Write the IP like 192.168.1.50", "IP اس طرح لکھیں: 192.168.1.50"), color = BP.Danger, fontSize = 13.sp)
        }
        Text(tr("Printer size", "پرنٹر کا سائز"), fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(tr("Small · 2 inch (58 mm)", "چھوٹا · 2 انچ"), size == 58, { size = 58 })
            ChoiceChip(tr("Big · 3 inch (80 mm)", "بڑا · 3 انچ"), size == 80, { size = 80 })
            ChoiceChip(tr("Big · 4 inch (108 mm)", "بڑا · 4 انچ"), size == 108, { size = 108 })
        }
        Text(tr("Printer language", "پرنٹر کی زبان"), fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(tr("TSPL · label printer", "TSPL · لیبل پرنٹر"), protocol == "tspl", { protocol = "tspl" })
            ChoiceChip(tr("ESC/POS · receipt printer", "ESC/POS · رسید پرنٹر"), protocol == "escpos", { protocol = "escpos" })
        }
        Text(tr("Most label printers (Xprinter, TSC, Gprinter) use TSPL.", "زیادہ تر لیبل پرنٹر TSPL استعمال کرتے ہیں۔"), style = MaterialTheme.typography.bodySmall)
        BigButton(tr("Save printer", "محفوظ کریں"), {
            vm.savePrinter(start.copy(name = name.trim().ifBlank { start.name.ifBlank { "Printer" } }, address = address.trim(),
                port = port.toIntOrNull() ?: 9100, maxWidthMm = size, protocol = protocol), makeDefault = !existing)
            vm.toast = "Printer saved"; onDismiss()
        }, Modifier.fillMaxWidth(), Icons.Rounded.Check, enabled = ipOk && (start.type != "wifi" || address.isNotBlank()), height = 60)
        if (existing) BigButton(if (armed) tr("Tap again to remove", "تصدیق کے لیے دوبارہ ٹیپ کریں") else tr("Remove printer", "پرنٹر ہٹائیں"),
            { if (armed) { vm.deletePrinter(start); onDismiss() } else armed = true }, Modifier.fillMaxWidth(), Icons.Rounded.Delete, BtnStyle.Danger)
    }
}

/* ---------------- Bluetooth scan ---------------- */

@SuppressLint("MissingPermission")
@Composable
fun BluetoothSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val adapter = remember { Transport.bluetoothAdapter(ctx) }
    val found = remember { mutableStateListOf<Pair<String, String>>() }
    var scanning by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Pair<String, String>?>(null) }
    val perms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun add(name: String?, mac: String) { if (found.none { it.second == mac }) found += (name?.ifBlank { null } ?: mac) to mac }
    fun start() {
        val a = adapter ?: return
        try {
            a.bondedDevices?.forEach { add(it.name, it.address) }
            if (a.isEnabled) { scanning = a.startDiscovery() }
        } catch (e: SecurityException) { denied = true }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) start() else denied = true
    }
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { launcher.launch(perms) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    BluetoothDevice.ACTION_FOUND -> IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.let {
                        try { add(it.name, it.address) } catch (_: SecurityException) {}
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                }
            }
        }
        val f = IntentFilter().apply { addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        if (adapter != null && !adapter.isEnabled) {
            try { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (e: Exception) { launcher.launch(perms) }
        } else launcher.launch(perms)
        onDispose {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
            try { adapter?.cancelDiscovery() } catch (_: Exception) {}
        }
    }

    chosen?.let { (n, mac) ->
        PrinterConfigSheet(vm, PrinterDevice(newId(), n, "bt", mac, 0, "tspl", if (n.contains("58") || n.contains("mini", true)) 58 else 80)) { chosen = null; onDismiss() }
        return
    }
    BPSheet(onDismiss) {
        SheetTitle(tr("Bluetooth printers", "بلوٹوتھ پرنٹرز"), if (scanning) tr("Searching nearby…", "تلاش جاری ہے…") else tr("Tap your printer to add it", "اپنے پرنٹر پر ٹیپ کریں"), onDismiss)
        when {
            adapter == null -> Text(tr("This phone has no Bluetooth.", "اس فون میں بلوٹوتھ نہیں ہے۔"), color = BP.Danger)
            denied -> WarnBox(tr("Bluetooth permission is needed to find printers. Allow it in phone Settings › Apps › BakePrint Pro › Permissions.",
                "پرنٹر تلاش کرنے کے لیے بلوٹوتھ اجازت چاہیے۔ سیٹنگز میں اجازت دیں۔"))
        }
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = BP.Gold, trackColor = BP.Beige)
        found.forEach { d ->
            MenuRow(Icons.Rounded.Bluetooth, d.first, d.second, onClick = { chosen = d },
                trailing = { Pill(tr("ADD", "شامل"), BP.Beige, BP.Choc) })
        }
        if (found.isEmpty() && !scanning && !denied) Text(tr("No printers found. Turn the printer on and tap Scan again.", "کوئی پرنٹر نہیں ملا۔ پرنٹر آن کر کے دوبارہ تلاش کریں۔"), color = BP.Grey)
        BigButton(tr("Scan again", "دوبارہ تلاش"), { launcher.launch(perms) }, Modifier.fillMaxWidth(), Icons.Rounded.Refresh, BtnStyle.Outline)
    }
}

/* ---------------- USB ---------------- */

@Composable
fun UsbSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var devices by remember { mutableStateOf(Transport.usbDevices(ctx)) }
    var chosen by remember { mutableStateOf<PrinterDevice?>(null) }
    chosen?.let { PrinterConfigSheet(vm, it) { chosen = null; onDismiss() }; return }
    BPSheet(onDismiss) {
        SheetTitle(tr("USB printer", "یو ایس بی پرنٹر"), tr("Connect with a USB OTG cable", "OTG کیبل سے جوڑیں"), onDismiss)
        Text(tr("1. Connect the printer with an OTG cable.\n2. Tap Detect.\n3. Tap the printer and tap Allow when Android asks.",
            "1۔ OTG کیبل سے پرنٹر جوڑیں۔\n2۔ تلاش دبائیں۔\n3۔ پرنٹر پر ٹیپ کریں اور Allow دبائیں۔"), fontSize = 15.sp)
        devices.forEach { d ->
            val nm = try { d.productName } catch (e: Exception) { null } ?: "USB printer"
            MenuRow(Icons.Rounded.Usb, nm, "VID ${d.vendorId} · PID ${d.productId}", onClick = {
                Transport.requestUsbPermission(ctx, d)
                chosen = PrinterDevice(newId(), nm, "usb", "${d.vendorId}:${d.productId}", 0, "tspl", 108)
            })
        }
        if (devices.isEmpty()) Text(tr("No USB printer connected.", "کوئی یو ایس بی پرنٹر نہیں ملا۔"), color = BP.Grey)
        BigButton(tr("Detect", "تلاش کریں"), { devices = Transport.usbDevices(ctx) }, Modifier.fillMaxWidth(), Icons.Rounded.Usb)
    }
}
