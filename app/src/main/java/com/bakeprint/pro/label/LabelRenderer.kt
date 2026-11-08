package com.bakeprint.pro.label

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.bakeprint.pro.data.LabelStyle
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything that can appear on a label. Dates are already formatted. */
data class LabelData(
    val bakery: String,
    val product: String,
    val productUr: String,
    val mfg: String,
    val exp: String,
    val mfgDate: LocalDate?,
    val code: String = "",
    val price: String = "",
    val weight: String = "",
    val storage: String = "",
)

data class LabelDesign(val id: Int, val en: String, val ur: String)

/**
 * Draws a label on an Android Canvas. The same code produces the on-screen preview,
 * the printer bitmap (8 dots/mm = 203 dpi), PDF pages and PNG files, so what you see is what prints.
 * Sizes are designed on a 50 × 30 mm base and scaled by k = min(h/30, w/50).
 */
object LabelRenderer {
    const val PRINT_DPM = 8f

    val designs = listOf(
        LabelDesign(1, "Signature", "سگنیچر"),
        LabelDesign(2, "Black Header", "بلیک ہیڈر"),
        LabelDesign(3, "Date Strip", "ڈیٹ اسٹرپ"),
        LabelDesign(4, "Table", "ٹیبل"),
        LabelDesign(5, "Monogram", "مونوگرام"),
        LabelDesign(6, "Minimal", "منیمل"),
        LabelDesign(7, "Expiry Focus", "ایکسپائری نمایاں"),
        LabelDesign(8, "Classic Frame", "کلاسک فریم"),
    )

    /* ---------- items stacked in the middle block ---------- */
    private sealed class Item {
        abstract val h: Float
        abstract val w: Float
        abstract fun scaled(f: Float): Item
        abstract fun draw(c: Canvas, x: Float, top: Float, align: Paint.Align)
    }

    private class T(val text: String, val p: Paint) : Item() {
        override val h get() = p.descent() - p.ascent()
        override val w get() = p.measureText(text)
        override fun scaled(f: Float) = T(text, Paint(p).apply { textSize = p.textSize * f })
        override fun draw(c: Canvas, x: Float, top: Float, align: Paint.Align) {
            val pp = Paint(p).apply { textAlign = align }
            c.drawText(text, x, top - p.ascent(), pp)
        }
    }

    private class Rule(val rw: Float, val th: Float, val color: Int) : Item() {
        override val h get() = th * 2.6f
        override val w get() = rw
        override fun scaled(f: Float) = this
        override fun draw(c: Canvas, x: Float, top: Float, align: Paint.Align) {
            val left = when (align) { Paint.Align.LEFT -> x; Paint.Align.RIGHT -> x - rw; else -> x - rw / 2 }
            val y = top + th * 1.6f
            c.drawRect(left, y, left + rw, y + th, Paint().apply { color = this@Rule.color })
        }
    }

    private class Pair2(val k: String, val kp: Paint, val v: String, val vp: Paint, val gap: Float) : Item() {
        override val h get() = max(kp.descent() - kp.ascent(), vp.descent() - vp.ascent())
        override val w get() = kp.measureText(k) + gap + vp.measureText(v)
        override fun scaled(f: Float) = Pair2(k, Paint(kp).apply { textSize = kp.textSize * f }, v,
            Paint(vp).apply { textSize = vp.textSize * f }, gap * f)
        override fun draw(c: Canvas, x: Float, top: Float, align: Paint.Align) {
            val start = when (align) { Paint.Align.LEFT -> x; Paint.Align.RIGHT -> x - w; else -> x - w / 2 }
            val base = top + max(-kp.ascent(), -vp.ascent())
            c.drawText(k, start, base, Paint(kp).apply { textAlign = Paint.Align.LEFT })
            c.drawText(v, start + kp.measureText(k) + gap, base, Paint(vp).apply { textAlign = Paint.Align.LEFT })
        }
    }

    /* ---------- helpers ---------- */
    fun typeface(font: String, weight: Int): Typeface {
        val family = if (font == "modern") "sans-serif-condensed" else "sans-serif"
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, false)
        else Typeface.create(family, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun paint(tf: Typeface, size: Float, color: Int, spacing: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color; letterSpacing = spacing }

    private fun fit(p: Paint, text: String, desired: Float, maxW: Float): Paint {
        p.textSize = desired
        if (text.isNotEmpty() && maxW > 0) {
            val w = p.measureText(text)
            if (w > maxW) p.textSize = desired * maxW / w
        }
        return p
    }

    private fun h(p: Paint) = p.descent() - p.ascent()

    private fun text(c: Canvas, s: String, p: Paint, x: Float, top: Float, align: Paint.Align) {
        c.drawText(s, x, top - p.ascent(), Paint(p).apply { textAlign = align })
    }

    private fun block(c: Canvas, itemsIn: List<Item>, l: Float, r: Float, t: Float, b: Float,
                      align: Paint.Align, vpos: String, gap: Float) {
        if (itemsIn.isEmpty() || b <= t) return
        var items = itemsIn
        var total = items.sumOf { it.h.toDouble() }.toFloat() + gap * (items.size - 1)
        if (total > b - t && total > 0) {
            val f = (b - t) / total
            items = items.map { it.scaled(f) }
            total = items.sumOf { it.h.toDouble() }.toFloat() + gap * f * (items.size - 1)
        }
        val g = if (total > b - t) 0f else gap * min(1f, (b - t) / max(total, 1f))
        var y = when (vpos) { "top" -> t; "bottom" -> b - total; else -> t + (b - t - total) / 2 }
        val x = when (align) { Paint.Align.LEFT -> l; Paint.Align.RIGHT -> r; else -> (l + r) / 2 }
        items.forEach { it.draw(c, x, y, align); y += it.h + g }
    }

    private fun extras(d: LabelData, s: LabelStyle): List<String> {
        val bits = mutableListOf<String>()
        if (s.showPrice && d.price.isNotBlank()) bits += "Rs ${d.price.trim()}"
        if (s.showWeight && d.weight.isNotBlank()) bits += d.weight.trim()
        if (s.showBatch) bits += "Batch ${d.code}" + (d.mfgDate?.let {
            "-%02d%02d%02d".format(it.dayOfMonth, it.monthValue, it.year % 100) } ?: "")
        return listOfNotNull(
            bits.joinToString("  ·  ").ifBlank { null },
            if (s.showStorage) d.storage.ifBlank { null } else null,
            s.extraText.ifBlank { null },
        )
    }

    /* ---------- main entry ---------- */
    fun render(d: LabelData, design: Int, widthMm: Float, heightMm: Float, s: LabelStyle, dpm: Float, logo: Bitmap?): Bitmap {
        val W = (widthMm * dpm).roundToInt().coerceAtLeast(16)
        val H = (heightMm * dpm).roundToInt().coerceAtLeast(16)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val Wf = W.toFloat(); val Hf = H.toFloat()

        val k = min(heightMm / 30f, widthMm / 50f)
        val u = dpm * k
        val ink = if (s.textColor != 0L) s.textColor.toInt() else 0xFF1E1712.toInt()
        val paper = if (s.bgColor != 0L) s.bgColor.toInt() else Color.WHITE
        c.drawColor(paper)

        val ex = extras(d, s)
        val fm = s.fontScale / 100f * (1f - 0.1f * ex.size)
        val gs = if (s.spacing < 0) 1f else s.spacing
        val brand = d.bakery.ifBlank { "BAKERY NAME" }.uppercase()
        val prod = if (s.showProduct) d.product else ""
        val ur = if (s.showProduct && s.urduName) d.productUr else ""
        val monoW = if (design == 5) min(widthMm * .3f, heightMm * .72f) * dpm else 0f
        val avail = Wf - 4.4f * u - monoW

        val wProd = if (s.weight > 0) s.weight else 700
        val tfBold = typeface(s.font, 700)
        val tfProd = typeface(s.font, wProd)
        val tfMed = typeface(s.font, 500)

        val brandP = fit(paint(tfBold, 0f, ink, if (design == 6) .3f else .16f), brand, 2.3f * u * fm, avail)
        val prodP = fit(paint(tfProd, 0f, ink, -.005f), prod, 4.4f * u * fm * (if (ur.isNotEmpty()) .9f else 1f), avail)
        val urP = fit(paint(Typeface.DEFAULT_BOLD, 0f, ink), ur, 2.6f * u * fm, avail)
        val exP = paint(tfMed, 1.7f * u * fm, ink)
        ex.maxByOrNull { it.length }?.let { fit(exP, it, 1.7f * u * fm, avail) }
        val longestDate = if (d.mfg.length >= d.exp.length) d.mfg else d.exp
        fun valPaint(colW: Float, mul: Float = 1f) = fit(paint(tfBold, 0f, ink), longestDate, 2.9f * u * fm * mul, colW)
        fun keyPaint(v: Paint) = paint(tfBold, min(1.75f * u * fm, v.textSize * .72f), ink, .16f).apply { alpha = 190 }

        val align = when (s.align) {
            "left" -> Paint.Align.LEFT; "right" -> Paint.Align.RIGHT; "center" -> Paint.Align.CENTER
            else -> if (design in listOf(3, 5, 7)) Paint.Align.LEFT else Paint.Align.CENTER
        }
        val vpos = if (s.position == "d") "center" else s.position

        fun middle(extraBefore: List<Item> = emptyList(), extraAfter: List<Item> = emptyList()): List<Item> {
            val list = mutableListOf<Item>()
            list += extraBefore
            if (prod.isNotEmpty()) list += T(prod, prodP)
            if (ur.isNotEmpty()) list += T(ur, urP)
            list += extraAfter
            ex.forEach { list += T(it, exP) }
            return list
        }

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ink }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = ink }

        /** Outer border. `def` = design default thickness in u (0 = none). */
        fun border(def: Float, radius: Float) {
            when (s.border) {
                "none" -> return
                "d" -> {
                    if (def <= 0f) return
                    val sw = def * u; stroke.strokeWidth = sw; stroke.pathEffect = null
                    c.drawRoundRect(RectF(sw / 2, sw / 2, Wf - sw / 2, Hf - sw / 2), radius * u, radius * u, stroke)
                }
                else -> {
                    val sw = max(1f, s.borderMm * dpm); stroke.strokeWidth = sw
                    stroke.pathEffect = if (s.border == "dashed") DashPathEffect(floatArrayOf(sw * 3, sw * 2), 0f) else null
                    c.drawRoundRect(RectF(sw / 2, sw / 2, Wf - sw / 2, Hf - sw / 2), radius * u, radius * u, stroke)
                    if (s.border == "double") {
                        val i = sw * 2.5f
                        c.drawRoundRect(RectF(i, i, Wf - i, Hf - i), radius * u * .6f, radius * u * .6f, stroke)
                    }
                    stroke.pathEffect = null
                }
            }
        }
        fun hline(x1: Float, x2: Float, y: Float, th: Float = .22f * u) = c.drawRect(x1, y, x2, y + th, fill)
        fun vline(x: Float, y1: Float, y2: Float, th: Float = .22f * u) = c.drawRect(x - th / 2, y1, x + th / 2, y2, fill)

        /** Brand row with optional logo and side decoration ("lines" or "diamonds"). Returns its height. */
        fun brandRow(l: Float, r: Float, top: Float, deco: String, color: Int = ink, al: Paint.Align = Paint.Align.CENTER): Float {
            val bp = Paint(brandP).apply { this.color = color }
            val bh = h(bp)
            val lh = if (logo != null) bh * 1.6f else 0f
            val lw = if (logo != null) logo.width * lh / logo.height else 0f
            val gap = if (logo != null) u else 0f
            val tw = bp.measureText(brand)
            val gw = min(lw + gap + tw, r - l)
            val rowH = max(bh, lh)
            val gx = when (al) { Paint.Align.LEFT -> l; Paint.Align.RIGHT -> r - gw; else -> (l + r - gw) / 2 }
            if (logo != null) c.drawBitmap(logo, null, RectF(gx, top + (rowH - lh) / 2, gx + lw, top + (rowH + lh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            text(c, brand, bp, gx + lw + gap, top + (rowH - bh) / 2, Paint.Align.LEFT)
            val cy = top + rowH / 2
            if (deco == "lines") {
                val g = u
                if (gx - g - l > u) hline(l, gx - g, cy - .11f * u)
                if (r - (gx + gw + g) > u) hline(gx + gw + g, r, cy - .11f * u)
            } else if (deco == "diamonds") {
                val dd = .9f * u
                listOf(gx - 1.6f * u, gx + gw + 1.6f * u).forEach { x ->
                    c.save(); c.rotate(45f, x, cy); c.drawRect(x - dd / 2, cy - dd / 2, x + dd / 2, cy + dd / 2, fill); c.restore()
                }
            }
            return rowH
        }

        /** Two date columns (MFG | EXP). Returns top y of the block. */
        fun dateCols(l: Float, r: Float, bottom: Float, lineTop: Boolean, divider: Boolean, al: Paint.Align, padTop: Float): Float {
            val colW = (r - l) / 2 - u
            val vp = valPaint(colW); val kp = keyPaint(vp)
            val bh = padTop + h(kp) + .35f * u + h(vp)
            val top = bottom - bh
            if (lineTop) hline(l, r, top)
            if (divider) vline((l + r) / 2, top + .4f * u, bottom)
            listOf(Triple(l, (l + r) / 2, "MFG" to d.mfg), Triple((l + r) / 2, r, "EXP" to d.exp)).forEachIndexed { i, (a, b, kv) ->
                val x = when (al) { Paint.Align.LEFT -> a + (if (i == 1) .8f * u else 0f); Paint.Align.RIGHT -> b; else -> (a + b) / 2 }
                text(c, kv.first, kp, x, top + padTop, al)
                text(c, kv.second, vp, x, top + padTop + h(kp) + .35f * u, al)
            }
            return top
        }

        when (design) {
            2 -> { // Black header
                val hh = h(brandP) + 2.3f * u
                c.drawRect(0f, 0f, Wf, hh, fill)
                brandRow(2f * u, Wf - 2f * u, 1.15f * u, "none", paper)
                val dTop = dateCols(2f * u, Wf - 2f * u, Hf - 1.5f * u, true, true, Paint.Align.CENTER, .8f * u)
                block(c, middle(), 2f * u, Wf - 2f * u, hh + .9f * u, dTop - .6f * u, align, vpos, .4f * u * gs)
                border(0f, 1f)
            }
            3 -> { // Date strip
                val half = Wf / 2 - 1.6f * u
                val vp = valPaint(half * .72f, .9f); val kp = keyPaint(vp).apply { color = paper; alpha = 215 }
                vp.color = paper
                val sh = h(vp) + 2.2f * u
                c.drawRect(0f, Hf - sh, Wf, Hf, fill)
                listOf(0f to "MFG" to d.mfg, Wf / 2 to "EXP" to d.exp).forEach { (pk, v) ->
                    val (x0, key) = pk
                    val item = Pair2(key, kp, v, vp, .7f * u)
                    item.draw(c, x0 + Wf / 4, Hf - sh + (sh - item.h) / 2, Paint.Align.CENTER)
                }
                c.drawRect(Wf / 2 - .11f * u, Hf - sh + .5f * u, Wf / 2 + .11f * u, Hf - .5f * u, Paint(fill).apply { color = paper; alpha = 140 })
                val items = mutableListOf<Item>(T(brand, brandP))
                items += middle()
                block(c, items, 2.2f * u, Wf - 2.2f * u, 1.6f * u, Hf - sh - .9f * u, align, vpos, .4f * u * gs)
                border(0f, 1f)
            }
            4 -> { // Table
                val sw = .4f * u
                val hh = h(brandP) + 1.7f * u
                c.drawRect(0f, 0f, Wf, hh, fill)
                brandRow(1.6f * u, Wf - 1.6f * u, .85f * u, "none", paper)
                val vp = valPaint((Wf - 3.2f * u) * .55f, 1.05f); val kp = keyPaint(vp).apply { alpha = 255 }
                val rowH = h(vp) + 1.3f * u
                val r2 = Hf - sw - rowH; val r1 = r2 - rowH
                listOf(r1 to ("MFG DATE" to d.mfg), r2 to ("EXP DATE" to d.exp)).forEach { (top, kv) ->
                    hline(0f, Wf, top)
                    text(c, kv.first, kp, 1.6f * u, top + (rowH - h(kp)) / 2, Paint.Align.LEFT)
                    text(c, kv.second, vp, Wf - 1.6f * u, top + (rowH - h(vp)) / 2, Paint.Align.RIGHT)
                }
                block(c, middle(), 1.6f * u, Wf - 1.6f * u, hh + .7f * u, r1 - .7f * u, align, vpos, .4f * u * gs)
                border(.4f, .7f)
            }
            5 -> { // Monogram
                if (logo != null) {
                    val lw = monoW - 1.6f * u; val sc = min(lw / logo.width, (Hf - 1.6f * u) / logo.height)
                    val bw = logo.width * sc; val bh = logo.height * sc
                    c.drawBitmap(logo, null, RectF((monoW - bw) / 2, (Hf - bh) / 2, (monoW + bw) / 2, (Hf + bh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                    vline(monoW, 0f, Hf)
                } else {
                    c.drawRect(0f, 0f, monoW, Hf, fill)
                    val ini = brand.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.ifBlank { "B" }
                    val ip = fit(paint(typeface(s.font, 900), 0f, paper, .03f), ini, monoW * .4f, monoW - 1.6f * u)
                    text(c, ini, ip, monoW / 2, (Hf - h(ip)) / 2, Paint.Align.CENTER)
                }
                val l = monoW + 1.7f * u; val r = Wf - 1.7f * u
                val bh = brandRow(l, r, 1.3f * u, "none", ink, Paint.Align.LEFT)
                val dTop = dateCols(l, r, Hf - 1.3f * u, true, false, Paint.Align.LEFT, .7f * u)
                block(c, middle(), l, r, 1.3f * u + bh + .6f * u, dTop - .6f * u, align, vpos, .4f * u * gs)
                border(.3f, 1.2f)
            }
            6 -> { // Minimal
                val pad = 2.1f * u
                val bh = brandRow(pad, Wf - pad, pad, "none")
                val dTop = dateCols(pad, Wf - pad, Hf - pad, false, false, Paint.Align.CENTER, 0f)
                val rule = Rule(7f * u, .3f * u, ink)
                block(c, middle(extraAfter = listOf(rule)), pad, Wf - pad, pad + bh + .9f * u, dTop - .9f * u, align, vpos, .4f * u * gs)
                border(0f, 1f)
            }
            7 -> { // Expiry focus
                val l = 1.8f * u; val r = Wf - 1.8f * u; val t = 1.5f * u; val b = Hf - 1.5f * u
                val bh = brandRow(l, r, t, "none", ink, Paint.Align.LEFT)
                hline(l, r, t + bh + .6f * u)
                val ekp = keyPaint(valPaint(r - l)).apply { color = paper; alpha = 220 }
                val evp = valPaint(r - l - ekp.measureText("EXP") - 4f * u, 1.2f).apply { color = paper }
                val ebH = h(evp) + 1.6f * u
                c.drawRoundRect(RectF(l, b - ebH, r, b), .8f * u, .8f * u, fill)
                text(c, "EXP", ekp, l + 1.4f * u, b - ebH + (ebH - h(ekp)) / 2, Paint.Align.LEFT)
                text(c, d.exp, evp, r - 1.4f * u, b - ebH + (ebH - h(evp)) / 2, Paint.Align.RIGHT)
                val mv = valPaint((r - l) * .6f, .8f); val mk = keyPaint(mv)
                val mfgRow = Pair2("MFG", mk, d.mfg, mv, .6f * u)
                block(c, middle(extraAfter = listOf(mfgRow)), l, r, t + bh + 1.4f * u, b - ebH - .7f * u, align, vpos, .4f * u * gs)
                border(.3f, 1.2f)
            }
            8 -> { // Classic frame
                val pad = 2.5f * u
                if (s.border == "d") {
                    stroke.strokeWidth = .18f * u
                    c.drawRoundRect(RectF(u, u, Wf - u, Hf - u), .4f * u, .4f * u, stroke)
                }
                val bh = brandRow(pad + 2.2f * u, Wf - pad - 2.2f * u, pad, "diamonds")
                val dTop = dateCols(pad, Wf - pad, Hf - pad, false, false, Paint.Align.CENTER, 0f)
                block(c, middle(), pad, Wf - pad, pad + bh + .7f * u, dTop - .7f * u, align, vpos, .4f * u * gs)
                border(.45f, .8f)
            }
            else -> { // 1 Signature
                val sw = .35f * u
                val l = 2f * u + sw; val r = Wf - 2f * u - sw; val t = 1.9f * u + sw; val b = Hf - 1.9f * u - sw
                val bh = brandRow(l, r, t, "lines")
                val dTop = dateCols(l, r, b, true, true, Paint.Align.CENTER, .9f * u)
                block(c, middle(), l, r, t + bh + .9f * u * gs, dTop - .9f * u * gs, align, vpos, .4f * u * gs)
                border(.35f, 1.6f)
            }
        }
        return bmp
    }

    /** Convert to 1-bit rows for thermal printers. Returns packed bytes, MSB first, 1 = black. */
    fun toMono(src: Bitmap, threshold: Int = 150): Pair<Int, ByteArray> {
        val w = src.width; val hgt = src.height
        val bytesPerRow = (w + 7) / 8
        val out = ByteArray(bytesPerRow * hgt)
        val row = IntArray(w)
        for (y in 0 until hgt) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = row[x]
                val a = (p ushr 24) and 0xFF
                val lum = if (a < 128) 255 else ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (lum < threshold) {
                    val i = y * bytesPerRow + x / 8
                    out[i] = (out[i].toInt() or (0x80 ushr (x % 8))).toByte()
                }
            }
        }
        return bytesPerRow to out
    }
}
