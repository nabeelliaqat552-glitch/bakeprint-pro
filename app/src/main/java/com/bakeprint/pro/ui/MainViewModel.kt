package com.bakeprint.pro.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bakeprint.pro.data.*
import com.bakeprint.pro.label.LabelData
import com.bakeprint.pro.label.LabelRenderer
import com.bakeprint.pro.print.PdfMaker
import com.bakeprint.pro.print.PrintException
import com.bakeprint.pro.print.PrintItem
import com.bakeprint.pro.print.PrinterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** One product's labels in a print job. */
data class LabelJob(val product: Product, val mfg: LocalDate, val exp: LocalDate, val qty: Int)

data class PrintState(
    val printerName: String,
    val done: Int = 0,
    val total: Int = 0,
    val finished: Boolean = false,
    val error: String? = null,
    val systemDialog: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val ctx get() = getApplication<Application>()

    val categories: StateFlow<List<Category>> = dao.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val products: StateFlow<List<Product>> = dao.products().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<PrintRecord>> = dao.history().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val printers: StateFlow<List<PrinterDevice>> = dao.printers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings> = dao.settings().map { AppSettings.from(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    var ready by mutableStateOf(false); private set
    var toast by mutableStateOf<String?>(null)

    /* ---------- current label (Create Label screen) ---------- */
    var selProductId by mutableStateOf<String?>(null); private set
    var mfg by mutableStateOf(LocalDate.now()); private set
    var exp by mutableStateOf<LocalDate?>(null); private set
    var expManual by mutableStateOf(false); private set
    var ackKey by mutableStateOf(""); private set
    var qty by mutableIntStateOf(20)

    var printState by mutableStateOf<PrintState?>(null)

    init {
        viewModelScope.launch {
            if (dao.categoryCount() == 0) {
                dao.upsertCategories(Seed.categories())
                dao.upsertProducts(Seed.products())
                dao.upsertPrinters(Seed.printers())
            }
            val s = AppSettings.from(dao.allSettings())
            qty = s.quantity
            dao.allProducts().firstOrNull { it.id == s.lastProductId }?.let { selectProduct(it, remember = false) }
            ready = true
        }
    }

    fun product(id: String?) = products.value.firstOrNull { it.id == id }
    fun category(id: String) = categories.value.firstOrNull { it.id == id }

    /* ---------- settings ---------- */
    fun set(key: String, value: String) = viewModelScope.launch { dao.upsertSetting(Setting(key, value)) }
    fun updateStyle(f: (LabelStyle) -> LabelStyle) = set("style", f(settings.value.style).toJson())
    fun setSize(w: Float, h: Float) = viewModelScope.launch {
        dao.upsertSettings(listOf(Setting("widthMm", w.toString()), Setting("heightMm", h.toString())))
    }
    fun setCalibration(c: Calibration) = viewModelScope.launch {
        dao.upsertSettings(listOf(Setting("calX", c.offsetXmm.toString()), Setting("calY", c.offsetYmm.toString()),
            Setting("calGap", c.gapMm.toString()), Setting("calDensity", c.density.toString()), Setting("calSpeed", c.speed.toString())))
    }

    /* ---------- label session ---------- */
    fun selectProduct(p: Product, remember: Boolean = true) {
        selProductId = p.id
        mfg = LocalDate.now().plusDays(p.mfgOffsetDays.toLong())
        expManual = false
        exp = if (p.expiryMode == "ask") null else mfg.plusDays(p.shelfLifeDays.toLong())
        ackKey = ""
        if (remember) set("lastProductId", p.id)
    }

    fun setMfgDate(d: LocalDate) {
        mfg = d
        val p = product(selProductId)
        if (p != null && !expManual && p.expiryMode != "ask") exp = d.plusDays(p.shelfLifeDays.toLong())
    }

    fun setExpDate(d: LocalDate) {
        exp = d
        val p = product(selProductId)
        expManual = !(p != null && p.expiryMode != "ask" && d == mfg.plusDays(p.shelfLifeDays.toLong()))
        ackKey = "$mfg|$d"
    }

    /** Manual expiry no longer matches the shelf life after a manufacturing-date change. */
    fun expiryDrift(): LocalDate? {
        val p = product(selProductId) ?: return null
        val e = exp ?: return null
        if (!expManual) return null
        val auto = mfg.plusDays(p.shelfLifeDays.toLong())
        return if (auto != e && ackKey != "$mfg|$e") auto else null
    }

    fun useShelfLife() { val p = product(selProductId) ?: return; exp = mfg.plusDays(p.shelfLifeDays.toLong()); expManual = false }
    fun keepManualExpiry() { ackKey = "$mfg|$exp" }

    fun loadFromHistory(r: PrintRecord) {
        val p = product(r.productId)
        if (p != null) { selProductId = p.id; set("lastProductId", p.id) }
        Dates.fromIso(r.mfg)?.let { mfg = it }
        exp = Dates.fromIso(r.exp); expManual = true; ackKey = "$mfg|$exp"
        qty = r.quantity
        viewModelScope.launch {
            dao.upsertSettings(listOf(Setting("widthMm", r.widthMm.toString()), Setting("heightMm", r.heightMm.toString()),
                Setting("design", r.design.toString())))
        }
    }

    /* ---------- rendering ---------- */
    private var logoCache: Pair<String, Bitmap?>? = null
    fun logo(path: String): Bitmap? {
        if (path.isBlank()) return null
        logoCache?.let { if (it.first == path) return it.second }
        val b = try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
        logoCache = path to b
        return b
    }

    fun labelData(p: Product?, m: LocalDate, e: LocalDate?, s: AppSettings = settings.value) = LabelData(
        bakery = s.bakeryName,
        product = p?.name ?: "",
        productUr = p?.nameUr ?: "",
        mfg = Dates.format(m, s.dateFormat),
        exp = Dates.format(e, s.dateFormat),
        mfgDate = m,
        code = p?.code ?: "",
        price = p?.price ?: "",
        weight = p?.weight ?: "",
        storage = p?.storage ?: "",
    )

    fun render(p: Product?, m: LocalDate, e: LocalDate?, dpm: Float, design: Int? = null,
               w: Float? = null, h: Float? = null, s: AppSettings = settings.value): Bitmap =
        LabelRenderer.render(labelData(p, m, e, s), design ?: s.design, w ?: s.widthMm, h ?: s.heightMm, s.style, dpm, logo(s.style.logoPath))

    /* ---------- products & categories ---------- */
    fun saveProduct(p: Product) = viewModelScope.launch {
        dao.upsertProduct(p)
        if (p.id == selProductId && !expManual) {
            if (p.expiryMode != "ask") exp = mfg.plusDays(p.shelfLifeDays.toLong())
        }
    }
    fun toggleFavourite(p: Product) = viewModelScope.launch { dao.upsertProduct(p.copy(favourite = !p.favourite)) }
    fun duplicate(p: Product) = viewModelScope.launch {
        dao.upsertProduct(p.copy(id = newId(), name = p.name + " (Copy)", code = nextCode(p.categoryId), favourite = false,
            createdAt = System.currentTimeMillis()))
    }
    fun deleteProduct(p: Product) = viewModelScope.launch {
        dao.deleteProduct(p)
        if (selProductId == p.id) selProductId = null
    }
    fun nextCode(catId: String): String {
        val c = category(catId)
        val pre = c?.prefix?.ifBlank { null } ?: "OT"
        val n = products.value.filter { it.code.startsWith("$pre-") }.mapNotNull { it.code.substringAfter("-").toIntOrNull() }.maxOrNull() ?: 0
        return "$pre-${"%03d".format(n + 1)}"
    }
    fun addCategory(name: String, ur: String, icon: String, onDone: (String) -> Unit) = viewModelScope.launch {
        val id = "c" + newId()
        val pre = name.filter { it.isLetter() && it.code < 128 }.take(2).uppercase().ifBlank { "OT" }
        dao.upsertCategory(Category(id, name, ur.ifBlank { name }, icon, pre, false, 100 + categories.value.size))
        onDone(id)
    }
    fun deleteCategory(c: Category) = viewModelScope.launch { dao.deleteCategory(c) }

    /* ---------- printers ---------- */
    fun savePrinter(p: PrinterDevice, makeDefault: Boolean) = viewModelScope.launch {
        dao.upsertPrinter(p)
        if (makeDefault) dao.setDefaultPrinter(p.id)
    }
    fun setDefaultPrinter(id: String) = viewModelScope.launch { dao.setDefaultPrinter(id) }
    fun deletePrinter(p: PrinterDevice) = viewModelScope.launch { dao.deletePrinter(p) }
    fun defaultPrinter(): PrinterDevice? = printers.value.firstOrNull { it.isDefault } ?: printers.value.firstOrNull()

    /* ---------- printing ---------- */
    fun print(jobs: List<LabelJob>, printer: PrinterDevice, copies: Int) {
        val s = settings.value
        val total = jobs.sumOf { it.qty } * copies
        printState = PrintState(printer.name, 0, total)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val items = jobs.map { j ->
                        PrintItem(render(j.product, j.mfg, j.exp, if (printer.type == "system") 12f else LabelRenderer.PRINT_DPM, s = s), j.qty * copies)
                    }
                    PrinterService.print(ctx, printer, items, s.widthMm, s.heightMm, s.calib) { done, tot ->
                        printState = printState?.copy(done = done, total = tot)
                    }
                }
                val now = System.currentTimeMillis()
                dao.insertHistory(jobs.mapIndexed { i, j ->
                    PrintRecord(0, j.product.id, j.product.name, Dates.iso(j.mfg), Dates.iso(j.exp), j.qty, copies, now + i,
                        printer.name, s.widthMm, s.heightMm, s.design)
                })
                set("quantity", (jobs.firstOrNull()?.qty ?: qty).toString())
                printState = printState?.copy(done = total, finished = true, systemDialog = printer.type == "system")
            } catch (e: PrintException) {
                printState = printState?.copy(error = e.message)
            } catch (e: Exception) {
                printState = printState?.copy(error = "Printing failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun testPrint(printer: PrinterDevice) {
        val p = product(selProductId) ?: products.value.firstOrNull() ?: return
        val s = settings.value
        printState = PrintState(printer.name, 0, 1)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bmp = render(p, LocalDate.now(), LocalDate.now().plusDays(p.shelfLifeDays.toLong()), LabelRenderer.PRINT_DPM)
                    PrinterService.print(ctx, printer, listOf(PrintItem(bmp, 1)), s.widthMm, s.heightMm, s.calib) { _, _ -> }
                }
                printState = printState?.copy(done = 1, finished = true, systemDialog = printer.type == "system")
            } catch (e: Exception) {
                printState = printState?.copy(error = e.message ?: "Test print failed")
            }
        }
    }

    /* ---------- files ---------- */
    fun exportPdf(uri: Uri, jobs: List<LabelJob>, copies: Int) = viewModelScope.launch {
        val s = settings.value
        val ok = withContext(Dispatchers.IO) {
            try {
                val items = jobs.map { PrintItem(render(it.product, it.mfg, it.exp, 12f, s = s), it.qty * copies) }
                ctx.contentResolver.openOutputStream(uri)?.use { PdfMaker.write(items, s.widthMm, s.heightMm, it) } != null
            } catch (e: Exception) { false }
        }
        toast = if (ok) "PDF saved" else "Could not save the PDF"
    }

    fun exportPng(uri: Uri, job: LabelJob) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                val bmp = render(job.product, job.mfg, job.exp, 12f)
                ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
            } catch (e: Exception) { false }
        }
        toast = if (ok) "PNG saved" else "Could not save the image"
    }

    fun exportProducts(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                val text = Backup.productsExport(dao.allCategories(), dao.allProducts())
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
            } catch (e: Exception) { false }
        }
        toast = if (ok) "Products exported" else "Export failed"
    }

    fun backup(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                val text = Backup.fullExport(dao.allCategories(), dao.allProducts(), dao.allHistory(), dao.allPrinters(), dao.allSettings())
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
            } catch (e: Exception) { false }
        }
        toast = if (ok) "Backup saved" else "Backup failed"
    }

    private fun readText(uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) { null }

    fun importProducts(uri: Uri) = viewModelScope.launch {
        val parsed = withContext(Dispatchers.IO) { readText(uri)?.let { Backup.parseProducts(it) } }
        if (parsed == null) { toast = "This file has no product data"; return@launch }
        val (cats, prods) = parsed
        val existingCats = dao.allCategories().map { it.id }.toSet()
        cats.filter { it.id !in existingCats }.forEach { dao.upsertCategory(it) }
        val catIds = dao.allCategories().map { it.id }.toSet()
        val existing = dao.allProducts()
        var n = 0
        prods.forEach { p ->
            if (existing.none { it.name.equals(p.name, true) && it.categoryId == p.categoryId }) {
                val cat = if (p.categoryId in catIds) p.categoryId else catIds.first()
                dao.upsertProduct(p.copy(id = newId(), categoryId = cat, code = p.code.ifBlank { nextCode(cat) }))
                n++
            }
        }
        toast = "$n products imported"
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        val full = withContext(Dispatchers.IO) { readText(uri)?.let { Backup.parseFull(it) } }
        if (full == null) { toast = "This file is not a BakePrint backup"; return@launch }
        dao.replaceAll(full.cats, full.prods, full.hist, full.prns.ifEmpty { Seed.printers() }, full.sets)
        selProductId = null
        toast = "Backup restored"
    }

    fun saveLogo(uri: Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) {
            try {
                val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
                val scale = minOf(1f, 600f / maxOf(bmp.width, bmp.height))
                val small = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
                val f = File(ctx.filesDir, "logo_${System.currentTimeMillis()}.png")
                f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
                f.absolutePath
            } catch (e: Exception) { null }
        }
        if (path == null) toast = "Could not read that image" else updateStyle { it.copy(logoPath = path) }
    }
}
