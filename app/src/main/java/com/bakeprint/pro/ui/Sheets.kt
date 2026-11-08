@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bakeprint.pro.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakeprint.pro.data.*
import com.bakeprint.pro.label.LabelRenderer
import java.time.LocalDate
import kotlin.math.min

/** Navigation + shared actions passed to every screen. */
class Nav(
    val go: (String) -> Unit,
    val print: (List<LabelJob>) -> Unit,
    val bulk: () -> Unit,
    val editName: () -> Unit,
)

/* ---------------- label image (exact render) ---------------- */

@Composable
fun LabelImage(vm: MainViewModel, p: Product?, mfg: LocalDate, exp: LocalDate?, maxW: Dp, maxH: Dp,
               design: Int? = null, shadow: Boolean = true) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val density = LocalDensity.current.density
    val dpm = min(maxW.value * density / s.widthMm, maxH.value * density / s.heightMm).coerceAtLeast(1f)
    val img = remember(p, mfg, exp, s, design, dpm) { vm.render(p, mfg, exp, dpm, design, s = s).asImageBitmap() }
    Image(img, null, Modifier
        .size((img.width / density).dp, (img.height / density).dp)
        .then(if (shadow) Modifier.shadow(5.dp, RoundedCornerShape(3.dp)) else Modifier))
}

fun pickDate(ctx: Context, initial: LocalDate?, onPick: (LocalDate) -> Unit) {
    val d = initial ?: LocalDate.now()
    DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

@Composable
fun SheetTitle(title: String, sub: String? = null, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, tr("Close", "بند کریں"), tint = BP.Grey) }
    }
}

@Composable
fun BPSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BP.Ivory,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
        )
    }
}

/* ---------------- bakery name ---------------- */

@Composable
fun NameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = BP.Ivory,
        title = { Text(tr("Bakery Name", "بیکری کا نام"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Bakery Name Likhein. It prints at the top of every label.", "بیکری کا نام لکھیں۔ یہ ہر لیبل کے اوپر آئے گا۔"),
                    style = MaterialTheme.typography.bodyMedium, color = BP.Grey)
                BPTextField(name, { name = it.take(40) }, tr("Bakery name", "بیکری کا نام"), placeholder = tr("e.g. Madina Bakers", "مثلاً مدینہ بیکرز"))
            }
        },
        confirmButton = { BigButton(tr("Save", "محفوظ کریں"), { if (name.isNotBlank()) onSave(name.trim()) }, enabled = name.isNotBlank()) },
    )
}

/* ---------------- date ---------------- */

@Composable
fun DateSheet(vm: MainViewModel, isMfg: Boolean, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val p = vm.product(vm.selProductId)
    var value by remember { mutableStateOf(if (isMfg) vm.mfg else vm.exp) }
    var typed by remember { mutableStateOf(value?.let { Dates.format(it, s.dateFormat) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val quick = if (isMfg) listOf(
        tr("Today", "آج") to LocalDate.now(), tr("Yesterday", "کل") to LocalDate.now().minusDays(1),
        tr("Tomorrow", "آنے والا کل") to LocalDate.now().plusDays(1))
    else buildList {
        if (p != null) add(tr("Shelf life (${p.shelfLifeDays} days)", "شیلف لائف (${p.shelfLifeDays} دن)") to vm.mfg.plusDays(p.shelfLifeDays.toLong()))
        add(tr("+1 day", "+1 دن") to (value ?: vm.mfg).plusDays(1))
        add(tr("−1 day", "−1 دن") to (value ?: vm.mfg).minusDays(1))
    }
    val errBefore = tr("Expiry cannot be before the manufacturing date.", "ایکسپائری تیاری سے پہلے نہیں ہو سکتی۔")
    val errFormat = tr("Date not recognised. Write it like ${Dates.hint(s.dateFormat)}", "تاریخ درست نہیں۔ ایسے لکھیں: ${Dates.hint(s.dateFormat)}")
    BPSheet(onDismiss) {
        SheetTitle(if (isMfg) tr("Manufacturing Date (MFG)", "تیاری کی تاریخ") else tr("Expiry Date (EXP)", "ختم ہونے کی تاریخ"),
            if (isMfg) tr("Banne ki tareekh", "") else tr("Kharab hone ki tareekh", ""), onDismiss)
        Text(Dates.format(value, s.dateFormat), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { (label, d) -> ChoiceChip(label, value == d, { value = d; typed = Dates.format(d, s.dateFormat); error = null }) }
        }
        BigButton(tr("Pick from calendar", "کیلنڈر سے چنیں"), { pickDate(ctx, value) { value = it; typed = Dates.format(it, s.dateFormat); error = null } },
            Modifier.fillMaxWidth(), Icons.Rounded.Event, BtnStyle.Outline)
        BPTextField(typed, { typed = it; Dates.parse(it)?.let { d -> value = d; error = null } },
            tr("Or type the date", "یا تاریخ لکھیں"), placeholder = Dates.hint(s.dateFormat))
        Text(error ?: tr("Accepted: DD-MM-YYYY, DD/MM/YYYY, YYYY-MM-DD", "قبول: DD-MM-YYYY, DD/MM/YYYY, YYYY-MM-DD"),
            color = if (error != null) BP.Danger else BP.Grey, fontSize = 12.sp)
        BigButton(tr("Save date", "محفوظ کریں"), {
            val d = Dates.parse(typed) ?: value
            when {
                d == null -> error = errFormat
                !isMfg && d.isBefore(vm.mfg) -> error = errBefore
                else -> { if (isMfg) vm.setMfgDate(d) else vm.setExpDate(d); onDismiss() }
            }
        }, Modifier.fillMaxWidth(), Icons.Rounded.Check, height = 60)
    }
}

/* ---------------- product picker ---------------- */

@Composable
fun ProductPickerSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val prods by vm.products.collectAsStateWithLifecycle()
    val ur = LocalUr.current
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BP.Ivory) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle(tr("Select product", "پروڈکٹ منتخب کریں"), tr("Product Select Karein", ""), onDismiss)
            BPTextField(q, { q = it }, tr("Search", "تلاش"))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { c ->
                val list = prods.filter { it.categoryId == c.id && (q.isBlank() || it.name.contains(q, true) || it.code.contains(q, true) || it.nameUr.contains(q)) }
                if (list.isNotEmpty()) {
                    item(key = "h" + c.id) { Text((if (ur) c.nameUr.ifBlank { c.name } else c.name).uppercase(), style = MaterialTheme.typography.labelSmall, color = BP.Grey, modifier = Modifier.padding(top = 6.dp)) }
                    items(list, key = { it.id }) { p ->
                        SoftCard(Modifier.fillMaxWidth(), onClick = { vm.selectProduct(p); onDismiss() }, padding = PaddingValues(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBadge(categoryIcon(c.icon), 42)
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(tr("${p.shelfLifeDays} days shelf life", "${p.shelfLifeDays} دن"), style = MaterialTheme.typography.bodySmall)
                                }
                                if (vm.selProductId == p.id) Icon(Icons.Rounded.CheckCircle, null, tint = BP.Gold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- product form ---------------- */

@Composable
fun ProductFormSheet(vm: MainViewModel, existing: Product?, presetCategory: String?, onDismiss: () -> Unit) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val ur = LocalUr.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameUr by remember { mutableStateOf(existing?.nameUr ?: "") }
    var cat by remember { mutableStateOf(existing?.categoryId ?: presetCategory ?: cats.firstOrNull()?.id ?: "cakes") }
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var shelf by remember { mutableIntStateOf(existing?.shelfLifeDays ?: 3) }
    var mfgOff by remember { mutableIntStateOf(existing?.mfgOffsetDays ?: 0) }
    var expMode by remember { mutableStateOf(existing?.expiryMode ?: "auto") }
    var storage by remember { mutableStateOf(existing?.storage ?: "") }
    var price by remember { mutableStateOf(existing?.price ?: "") }
    var weight by remember { mutableStateOf(existing?.weight ?: "") }
    var err by remember { mutableStateOf(false) }
    BPSheet(onDismiss) {
        SheetTitle(if (existing == null) tr("Add Product", "نئی پروڈکٹ") else tr("Edit Product", "پروڈکٹ میں تبدیلی"), null, onDismiss)
        BPTextField(name, { name = it; err = false }, tr("Product name", "پروڈکٹ کا نام"), placeholder = tr("e.g. Almond Cake", "مثلاً Almond Cake"))
        if (err) Text(tr("Please write the product name.", "پروڈکٹ کا نام لکھیں۔"), color = BP.Danger, fontSize = 13.sp)
        BPTextField(nameUr, { nameUr = it }, tr("Urdu name (optional)", "اردو نام (اختیاری)"), placeholder = "مثلاً بادام کیک", rtl = true)
        Text(tr("Category", "کیٹیگری"), fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { c -> ChoiceChip(if (ur) c.nameUr.ifBlank { c.name } else c.name, cat == c.id, { cat = c.id }, icon = categoryIcon(c.icon)) }
        }
        Text(tr("Shelf life (days)", "شیلف لائف (دن)"), fontWeight = FontWeight.Bold)
        Stepper(shelf, { shelf = it }, min = 0, max = 999)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BPTextField(price, { price = it }, tr("Price Rs (optional)", "قیمت (اختیاری)"), Modifier.weight(1f), "850", numeric = true)
            BPTextField(weight, { weight = it }, tr("Weight (optional)", "وزن (اختیاری)"), Modifier.weight(1f), "500 g")
        }
        BPTextField(code, { code = it }, tr("Product code", "پروڈکٹ کوڈ"), placeholder = tr("Leave empty for automatic", "خالی چھوڑیں تو خودکار"))
        Text(tr("Default manufacturing date", "تیاری کی تاریخ"), fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(tr("Today", "آج"), mfgOff == 0, { mfgOff = 0 })
            ChoiceChip(tr("Yesterday", "کل"), mfgOff == -1, { mfgOff = -1 })
        }
        Text(tr("Expiry setting", "ایکسپائری"), fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(tr("Auto from shelf life", "شیلف لائف سے خودکار"), expMode == "auto", { expMode = "auto" })
            ChoiceChip(tr("I set it each time", "ہر بار خود لگاؤں گا"), expMode == "ask", { expMode = "ask" })
        }
        BPTextField(storage, { storage = it }, tr("Storage instructions (optional)", "رکھنے کی ہدایات (اختیاری)"),
            placeholder = tr("e.g. Keep refrigerated at 2–8 °C", "مثلاً فریج میں رکھیں"), singleLine = false)
        BigButton(tr("Save product", "محفوظ کریں"), {
            if (name.isBlank()) { err = true; return@BigButton }
            val finalCode = code.trim().ifBlank { if (existing != null && existing.categoryId == cat) existing.code else vm.nextCode(cat) }
            vm.saveProduct((existing ?: Product(id = newId(), name = "", categoryId = cat, code = "", shelfLifeDays = 3)).copy(
                name = name.trim(), nameUr = nameUr.trim(), categoryId = cat, code = finalCode, shelfLifeDays = shelf,
                mfgOffsetDays = mfgOff, expiryMode = expMode, storage = storage.trim(), price = price.trim(), weight = weight.trim()))
            vm.toast = if (existing == null) "Product added" else "Product updated"
            onDismiss()
        }, Modifier.fillMaxWidth(), Icons.Rounded.Check, height = 60)
    }
}

/* ---------------- category ---------------- */

@Composable
fun CategorySheet(vm: MainViewModel, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var nameUr by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("box") }
    BPSheet(onDismiss) {
        SheetTitle(tr("New category", "نئی کیٹیگری"), null, onDismiss)
        BPTextField(name, { name = it }, tr("Category name", "نام"), placeholder = tr("e.g. Sweets", "مثلاً Sweets"))
        BPTextField(nameUr, { nameUr = it }, tr("Urdu name (optional)", "اردو نام (اختیاری)"), rtl = true)
        Text(tr("Icon", "آئیکن"), fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categoryIconKeys.forEach { k ->
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(BP.White)
                    .border(2.dp, if (icon == k) BP.Choc else BP.Line, RoundedCornerShape(14.dp)).clickable { icon = k },
                    contentAlignment = Alignment.Center) { Icon(categoryIcon(k), null, tint = BP.Gold) }
            }
        }
        BigButton(tr("Create category", "بنائیں"), {
            if (name.isNotBlank()) vm.addCategory(name.trim(), nameUr.trim(), icon) { onCreated(it) }
        }, Modifier.fillMaxWidth(), Icons.Rounded.Check, enabled = name.isNotBlank(), height = 60)
    }
}

/* ---------------- print sheet ---------------- */

@Composable
fun PrintSheet(vm: MainViewModel, jobs: List<LabelJob>, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    var printerId by remember { mutableStateOf(vm.defaultPrinter()?.id) }
    var copies by remember { mutableIntStateOf(1) }
    val printer = printers.firstOrNull { it.id == printerId } ?: printers.firstOrNull()
    val total = jobs.sumOf { it.qty } * copies
    val one = jobs.singleOrNull()
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportPdf(uri, jobs, copies)
    }
    val pngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) vm.exportPng(uri, jobs.first())
    }
    BPSheet(onDismiss) {
        SheetTitle(tr("Print Labels", "لیبل پرنٹ کریں"),
            (one?.product?.name ?: "${jobs.size} ${tr("products", "پروڈکٹس")}") + " · ${fmtMm(s.widthMm)}×${fmtMm(s.heightMm)} mm", onDismiss)
        if (one != null) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BP.Beige).padding(16.dp), contentAlignment = Alignment.Center) {
                LabelImage(vm, one.product, one.mfg, one.exp, 260.dp, 150.dp)
            }
        }
        Text(tr("Printer", "پرنٹر"), fontWeight = FontWeight.Bold)
        printers.forEach { p ->
            Surface(onClick = { printerId = p.id }, shape = RoundedCornerShape(16.dp), color = BP.White,
                border = BorderStroke(1.5.dp, if (p.id == printer?.id) BP.Gold else BP.White)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = p.id == printer?.id, onClick = { printerId = p.id }, colors = RadioButtonDefaults.colors(selectedColor = BP.Choc))
                    IconBadge(printerIcon(p.type), 40)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text(printerSub(p), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (printer != null && printer.type != "system" && s.widthMm > printer.maxWidthMm) {
            WarnBox(tr("This label is ${fmtMm(s.widthMm)} mm wide but ${printer.name} prints up to ${printer.maxWidthMm} mm. Choose a smaller size or a bigger printer.",
                "یہ لیبل ${fmtMm(s.widthMm)} mm چوڑا ہے، یہ پرنٹر ${printer.maxWidthMm} mm تک پرنٹ کرتا ہے۔ چھوٹا سائز یا بڑا پرنٹر چنیں۔"))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Number of copies", "کاپیاں"), fontWeight = FontWeight.Bold)
                Text(tr("Each copy repeats the full quantity", "ہر کاپی پوری تعداد دہراتی ہے"), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(tr("Total labels", "کل لیبل"), style = MaterialTheme.typography.bodySmall)
                Text("$total", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Stepper(copies, { copies = it }, 1, 20)
        BigButton(tr("PRINT $total LABELS", "$total لیبل پرنٹ کریں"), { if (printer != null) vm.print(jobs, printer, copies) },
            Modifier.fillMaxWidth(), Icons.Rounded.Print, enabled = printer != null, height = 62)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(tr("Save PDF", "PDF محفوظ"), { pdfLauncher.launch("labels-${LocalDate.now()}.pdf") }, Modifier.weight(1f), Icons.Rounded.PictureAsPdf, BtnStyle.Outline)
            BigButton(tr("Save PNG", "PNG محفوظ"), { pngLauncher.launch("label-${LocalDate.now()}.png") }, Modifier.weight(1f), Icons.Rounded.Image, BtnStyle.Outline)
        }
    }
}

fun fmtMm(f: Float): String = if (f % 1f == 0f) f.toInt().toString() else "%.1f".format(f)

fun printerIcon(type: String) = when (type) {
    "bt" -> Icons.Rounded.Bluetooth; "wifi" -> Icons.Rounded.Wifi; "usb" -> Icons.Rounded.Usb; else -> Icons.Rounded.PhoneAndroid
}

@Composable
fun printerSub(p: PrinterDevice): String {
    val kind = when {
        p.type == "system" -> tr("Phone printing / PDF", "فون پرنٹنگ / PDF")
        p.maxWidthMm <= 60 -> tr("Small · up to ${p.maxWidthMm} mm", "چھوٹا · ${p.maxWidthMm} mm تک")
        else -> tr("Big · up to ${p.maxWidthMm} mm", "بڑا · ${p.maxWidthMm} mm تک")
    }
    val lang = if (p.type == "system") "" else " · " + if (p.protocol == "escpos") "ESC/POS" else "TSPL"
    return kind + lang
}

@Composable
fun WarnBox(text: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BP.WarnBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, fontSize = 13.5.sp, color = BP.Choc)
        content()
    }
}

/* ---------------- print progress ---------------- */

@Composable
fun PrintProgressDialog(st: PrintState, onClose: () -> Unit) {
    val target = if (st.total > 0) st.done.toFloat() / st.total else 0f
    val prog by animateFloatAsState(target, tween(300), label = "p")
    AlertDialog(
        onDismissRequest = { if (st.finished || st.error != null) onClose() }, containerColor = BP.Ivory,
        confirmButton = {
            if (st.finished || st.error != null) BigButton(tr("Done", "ٹھیک ہے"), onClose, Modifier.fillMaxWidth())
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    st.error != null -> {
                        Box(Modifier.size(84.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFFF4E2DC)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PriorityHigh, null, tint = BP.Danger, modifier = Modifier.size(44.dp))
                        }
                        Text(tr("Not printed", "پرنٹ نہیں ہوا"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(st.error, textAlign = TextAlign.Center, color = BP.Choc)
                        Text(tr("Tip: you can still Save PDF or PNG from the print screen.", "آپ PDF یا PNG محفوظ کر سکتے ہیں۔"), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    st.finished -> {
                        val sc by animateFloatAsState(1f, tween(400), label = "s")
                        Box(Modifier.size((96 * sc).dp).clip(CircleShape).background(BP.Choc), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = BP.Gold, modifier = Modifier.size(56.dp))
                        }
                        Text(if (st.systemDialog) tr("Sent to phone printing", "پرنٹ اسکرین کھل گئی") else tr("${st.total} labels printed", "${st.total} لیبل پرنٹ ہو گئے"),
                            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        Text(st.printerName + " · " + tr("Saved to history", "ہسٹری میں محفوظ"), style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { prog }, modifier = Modifier.size(110.dp), color = BP.Gold, trackColor = BP.Beige, strokeWidth = 8.dp)
                            Text("${st.done} / ${st.total}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        Text(tr("Sending to ${st.printerName}…", "${st.printerName} کو بھیجا جا رہا ہے…"), textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
}

/* ---------------- bulk print ---------------- */

private data class BulkRow(val mfg: LocalDate, val exp: LocalDate, val qty: Int, val expManual: Boolean = false)

@Composable
fun BulkSheet(vm: MainViewModel, onDismiss: () -> Unit, onPrint: (List<LabelJob>) -> Unit) {
    val prods by vm.products.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val rows = remember { mutableStateMapOf<String, BulkRow>() }
    var q by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    val chosen = prods.filter { rows.containsKey(it.id) }
    val jobs = chosen.map { p -> rows.getValue(p.id).let { LabelJob(p, it.mfg, it.exp, it.qty) } }
    val total = jobs.sumOf { it.qty }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BP.Ivory) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle(if (preview) tr("Combined preview", "مکمل پیش نظارہ") else tr("Bulk Print", "بلک پرنٹ"),
                if (preview) "${chosen.size} ${tr("products", "پروڈکٹس")} · $total ${tr("labels", "لیبل")}"
                else tr("Tick products, set dates and quantity", "پروڈکٹس چنیں، تاریخ اور تعداد لکھیں"), onDismiss)
            if (!preview) BPTextField(q, { q = it }, tr("Search", "تلاش"))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (preview) {
                items(jobs, key = { it.product.id }) { j ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BP.Beige).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelImage(vm, j.product, j.mfg, j.exp, 170.dp, 100.dp)
                        Column(Modifier.weight(1f)) {
                            Text(j.product.name, fontWeight = FontWeight.Bold)
                            Text("× ${j.qty}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            } else {
                items(prods.filter { q.isBlank() || it.name.contains(q, true) }, key = { it.id }) { p ->
                    val r = rows[p.id]
                    Surface(shape = RoundedCornerShape(16.dp), color = BP.White, border = BorderStroke(1.5.dp, if (r != null) BP.Gold else BP.White)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth().clickable {
                                if (r != null) rows.remove(p.id) else {
                                    val m = LocalDate.now().plusDays(p.mfgOffsetDays.toLong())
                                    rows[p.id] = BulkRow(m, m.plusDays(p.shelfLifeDays.toLong()), s.quantity)
                                }
                            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Checkbox(checked = r != null, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = BP.Choc))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold)
                                    Text(tr("${p.shelfLifeDays} days", "${p.shelfLifeDays} دن"), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (r != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BigButton("MFG " + Dates.format(r.mfg, s.dateFormat), {
                                        pickDate(ctx, r.mfg) { d -> rows[p.id] = r.copy(mfg = d, exp = if (r.expManual) r.exp else d.plusDays(p.shelfLifeDays.toLong())) }
                                    }, Modifier.weight(1f), style = BtnStyle.Soft, height = 46)
                                    BigButton("EXP " + Dates.format(r.exp, s.dateFormat), {
                                        pickDate(ctx, r.exp) { d -> rows[p.id] = r.copy(exp = d, expManual = true) }
                                    }, Modifier.weight(1f), style = BtnStyle.Soft, height = 46)
                                }
                                Stepper(r.qty, { rows[p.id] = r.copy(qty = it) }, 1, 999, 5)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (preview) {
                BigButton(tr("Back", "واپس"), { preview = false }, Modifier.weight(1f), style = BtnStyle.Outline)
                BigButton(tr("Print all", "سب پرنٹ"), { onPrint(jobs) }, Modifier.weight(1f), Icons.Rounded.Print)
            } else {
                BigButton(tr("Preview ${chosen.size} products · $total labels", "پیش نظارہ · $total لیبل"), { preview = true },
                    Modifier.fillMaxWidth(), Icons.Rounded.Visibility, enabled = chosen.isNotEmpty(), height = 60)
            }
        }
    }
}
