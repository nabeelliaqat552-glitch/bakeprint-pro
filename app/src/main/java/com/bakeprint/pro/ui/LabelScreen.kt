@file:OptIn(ExperimentalLayoutApi::class)

package com.bakeprint.pro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakeprint.pro.data.*
import com.bakeprint.pro.label.LabelRenderer
import java.time.temporal.ChronoUnit

val SMALL_SIZES = listOf(30f to 20f, 40f to 25f, 40f to 30f, 50f to 30f, 50f to 40f, 58f to 40f)
val BIG_SIZES = listOf(60f to 40f, 70f to 50f, 80f to 50f, 100f to 50f, 100f to 70f)

@Composable
fun LabelScreen(vm: MainViewModel, nav: Nav) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val p = vm.product(vm.selProductId)
    var advanced by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var dateSheet by remember { mutableStateOf<Boolean?>(null) } // true = mfg, false = exp
    var customSize by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var designY by remember { mutableIntStateOf(0) }
    val screenW = LocalConfiguration.current.screenWidthDp

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp).padding(bottom = 84.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle(tr("Label Banayein", "لیبل بنائیں"), tr("Create Label", "لیبل ڈیزائنر")) {
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(BP.Beige).padding(4.dp)) {
                    SegBtn(tr("Easy", "آسان"), !advanced) { advanced = false }
                    SegBtn(tr("Advanced", "ایڈوانس"), advanced) { advanced = true }
                }
            }
            // live preview
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.verticalGradient(listOf(BP.Beige, BP.Beige2)))
                .padding(horizontal = 16.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (p != null) Pill(p.name, BP.White, BP.Choc)
                LabelImage(vm, p, vm.mfg, vm.exp, (screenW - 72).coerceAtMost(340).dp, 210.dp)
                Text("${fmtMm(s.widthMm)} × ${fmtMm(s.heightMm)} mm · ${(s.widthMm * 8).toInt()} × ${(s.heightMm * 8).toInt()} dots @ 203 dpi",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (!advanced) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Modifier.weight(1f), Icons.Rounded.Edit, tr("Edit Name", "نام بدلیں"), nav.editName)
                    SmallAction(Modifier.weight(1f), Icons.Rounded.Palette, tr("Change Design", "ڈیزائن بدلیں")) {
                        scope.launch { scroll.animateScrollTo((scroll.value + designY - 260).coerceAtLeast(0)) }
                    }
                    SmallAction(Modifier.weight(1f), Icons.Rounded.Tune, tr("Advanced Settings", "ایڈوانس")) { advanced = true }
                }
                EasySteps(vm, s, p, onPick = { picker = true }, onDate = { dateSheet = it }, onCustom = { customSize = true },
                    onEditName = nav.editName, onDesignY = { designY = it })
            } else {
                AdvancedPanel(vm, s)
            }
        }
        // sticky print button
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, BP.Ivory, BP.Ivory))).padding(16.dp)) {
            BigButton(tr("PRINT LABEL · Print Karein", "پرنٹ کریں"), {
                val e = vm.exp
                if (p != null && e != null) nav.print(listOf(LabelJob(p, vm.mfg, e, vm.qty)))
            }, Modifier.fillMaxWidth(), Icons.Rounded.Print, enabled = p != null && vm.exp != null, height = 62)
        }
    }

    if (picker) ProductPickerSheet(vm) { picker = false }
    dateSheet?.let { DateSheet(vm, it) { dateSheet = null } }
    if (customSize) CustomSizeDialog(s.widthMm, s.heightMm, { customSize = false }) { w, h -> vm.setSize(w, h); customSize = false }
}


@Composable
private fun SegBtn(text: String, on: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (on) BP.White else Color.Transparent,
        shadowElevation = if (on) 2.dp else 0.dp) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp,
            color = if (on) BP.Choc else BP.Grey)
    }
}

@Composable
private fun SmallAction(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(68.dp), shape = RoundedCornerShape(16.dp), color = BP.White,
        border = BorderStroke(1.dp, BP.Line)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(4.dp)) {
            Icon(icon, null, tint = BP.Gold, modifier = Modifier.size(20.dp))
            Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, hint: String, value: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
                     body: (@Composable ColumnScope.() -> Unit)? = null) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(BP.Choc), contentAlignment = Alignment.Center) {
                Text("$n", color = BP.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (hint.isNotBlank()) Text(hint, color = BP.Gold, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            }
            value?.invoke()
        }
        if (body != null) { Spacer(Modifier.height(12.dp)); body() }
    }
}

@Composable
private fun ValueText(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = BP.Choc) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp))
        Icon(icon, null, tint = BP.Grey, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EasySteps(vm: MainViewModel, s: AppSettings, p: Product?, onPick: () -> Unit, onDate: (Boolean) -> Unit,
                      onCustom: () -> Unit, onEditName: () -> Unit, onDesignY: (Int) -> Unit) {
    val ur = LocalUr.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepCard(1, tr("Product", "پروڈکٹ منتخب کریں"), tr("Product Select Karein", ""), {
            ValueText(p?.name ?: tr("Choose", "چنیں"), Icons.AutoMirrored.Rounded.KeyboardArrowRight, if (p == null) BP.Gold else BP.Choc)
        }, onPick) {
            if (p != null) SwitchRow(tr("Also show Urdu name", "اردو نام بھی دکھائیں") + if (p.nameUr.isNotBlank()) "  ${p.nameUr}" else "",
                s.style.urduName, { v -> vm.updateStyle { it.copy(urduName = v, showProduct = if (v) true else it.showProduct) } })
        }
        StepCard(2, tr("Bakery Name", "بیکری کا نام"), tr("Bakery Name Likhein", ""), {
            ValueText(s.bakeryName.ifBlank { tr("Write", "لکھیں") }, Icons.Rounded.Edit, if (s.bakeryName.isBlank()) BP.Gold else BP.Choc)
        }, onEditName)
        StepCard(3, tr("Manufacturing Date (MFG)", "تیاری کی تاریخ"), tr("Banne ki tareekh", ""), {
            ValueText(Dates.format(vm.mfg, s.dateFormat), Icons.Rounded.Event)
        }, { onDate(true) })
        val e = vm.exp
        val days = e?.let { ChronoUnit.DAYS.between(vm.mfg, it) }
        StepCard(4, tr("Expiry Date (EXP)", "ختم ہونے کی تاریخ"), "", {
            ValueText(Dates.format(e, s.dateFormat), Icons.Rounded.Event)
        }, { onDate(false) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e == null) Text(tr("Set the expiry date", "ایکسپائری کی تاریخ لگائیں"), color = BP.Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                else {
                    if (vm.expManual) Pill(tr("MANUAL", "دستی"), BP.WarnBg, Color(0xFF8A6430)) else Pill(tr("AUTO", "خودکار"), Color(0xFFE7EDE1), BP.Ok)
                    Text("$days ${tr(if (days == 1L) "day" else "days", "دن")}", style = MaterialTheme.typography.bodySmall)
                }
            }
            vm.expiryDrift()?.let { auto ->
                Spacer(Modifier.height(8.dp))
                WarnBox(tr("Expiry was set by hand. Shelf life says ${Dates.format(auto, s.dateFormat)}.",
                    "ایکسپائری ہاتھ سے لگائی گئی ہے۔ شیلف لائف کے مطابق ${Dates.format(auto, s.dateFormat)}")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton(tr("Use shelf life", "شیلف لائف لگائیں"), { vm.useShelfLife() }, Modifier.weight(1f), height = 44)
                        BigButton(tr("Keep mine", "یہی رکھیں"), { vm.keepManualExpiry() }, Modifier.weight(1f), style = BtnStyle.Outline, height = 44)
                    }
                }
            }
        }
        Box(Modifier.onGloballyPositioned { onDesignY(it.positionInRoot().y.toInt()) }) {
            StepCard(5, tr("Choose Design", "ڈیزائن منتخب کریں"), tr("Design Select Karein", "")) {
                DesignGrid(vm, s, p)
            }
        }
        StepCard(6, tr("Label Size", "لیبل سائز"), tr("Label ka size", "")) {
            SizeGroup(tr("Small sticker printer · 2 inch", "چھوٹا اسٹیکر پرنٹر · 2 انچ"), SMALL_SIZES, s) { w, h -> vm.setSize(w, h) }
            Spacer(Modifier.height(10.dp))
            SizeGroup(tr("Big thermal printer · 3–4 inch", "بڑا تھرمل پرنٹر · 3–4 انچ"), BIG_SIZES, s, custom = onCustom) { w, h -> vm.setSize(w, h) }
        }
        StepCard(7, tr("Print Quantity", "کتنے لیبل؟"), tr("Kitnay Labels Chahiye?", "")) {
            Stepper(vm.qty, { vm.qty = it })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 50, 100).forEach { n ->
                    Surface(onClick = { vm.qty = n }, shape = RoundedCornerShape(12.dp), color = if (vm.qty == n) BP.Choc else BP.Ivory,
                        modifier = Modifier.weight(1f).height(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("$n", fontWeight = FontWeight.Bold, color = if (vm.qty == n) BP.White else BP.Choc)
                        }
                    }
                }
            }
        }
        if (ur) Spacer(Modifier.height(4.dp))
    }
}


@Composable
private fun DesignGrid(vm: MainViewModel, s: AppSettings, p: Product?) {
    val ur = LocalUr.current
    val screenW = LocalConfiguration.current.screenWidthDp
    val cell = ((screenW - 32 - 28 - 16) / 3).coerceAtLeast(80)
    LabelRenderer.designs.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            row.forEach { d ->
                val on = s.design == d.id
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) BP.White else BP.Ivory)
                    .border(2.dp, if (on) BP.Gold else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { vm.set("design", d.id.toString()) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(60.dp), contentAlignment = Alignment.Center) {
                        LabelImage(vm, p, vm.mfg, vm.exp, (cell - 12).dp, 56.dp, design = d.id, shadow = false)
                    }
                    Text(if (ur) d.ur else d.en, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) BP.Choc else BP.Grey,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SizeGroup(title: String, sizes: List<Pair<Float, Float>>, s: AppSettings, custom: (() -> Unit)? = null, onPick: (Float, Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Print, null, tint = BP.Gold, modifier = Modifier.size(16.dp))
        Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = BP.Grey)
    }
    Spacer(Modifier.height(8.dp))
    val isCustom = (SMALL_SIZES + BIG_SIZES).none { it.first == s.widthMm && it.second == s.heightMm }
    val cells: List<Pair<Float, Float>?> = sizes + if (custom != null) listOf(null) else emptyList()
    cells.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            row.forEach { sz ->
                val on = if (sz == null) isCustom else s.widthMm == sz.first && s.heightMm == sz.second
                Surface(onClick = { if (sz == null) custom?.invoke() else onPick(sz.first, sz.second) },
                    modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp),
                    color = if (on) BP.White else BP.Ivory, border = BorderStroke(2.dp, if (on) BP.Gold else Color.Transparent)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (sz == null) {
                            Text(if (isCustom) "${fmtMm(s.widthMm)}×${fmtMm(s.heightMm)}" else tr("Custom", "اپنا سائز"), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text(tr("Enter mm", "mm لکھیں"), fontSize = 10.5.sp, color = BP.Grey)
                        } else {
                            Text("${fmtMm(sz.first)}×${fmtMm(sz.second)}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("mm", fontSize = 10.5.sp, color = BP.Grey)
                        }
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun CustomSizeDialog(w0: Float, h0: Float, onDismiss: () -> Unit, onSave: (Float, Float) -> Unit) {
    var w by remember { mutableStateOf(fmtMm(w0)) }
    var h by remember { mutableStateOf(fmtMm(h0)) }
    val wf = w.toFloatOrNull(); val hf = h.toFloatOrNull()
    val ok = wf != null && hf != null && wf in 20f..150f && hf in 15f..120f
    AlertDialog(onDismissRequest = onDismiss, containerColor = BP.Ivory,
        title = { Text(tr("Custom size", "اپنا سائز"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BPTextField(w, { w = it }, tr("Width (mm)", "چوڑائی (mm)"), Modifier.weight(1f), numeric = true)
                    BPTextField(h, { h = it }, tr("Height (mm)", "اونچائی (mm)"), Modifier.weight(1f), numeric = true)
                }
                Text(tr("Width 20–150 mm · Height 15–120 mm", "چوڑائی 20–150 · اونچائی 15–120"), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { BigButton(tr("Use this size", "یہ سائز لگائیں"), { if (ok) onSave(wf!!, hf!!) }, enabled = ok) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ"), color = BP.Grey) } })
}

/* ================= ADVANCED ================= */

@Composable
private fun Ctl(title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, color = BP.Gold, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Opts(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, label) -> ChoiceChip(label, k == selected, { onPick(k) }) }
    }
}

@Composable
private fun Swatches(colors: List<Pair<Long, String>>, selected: Long, onPick: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { (c, name) ->
            Box(Modifier.size(40.dp).clip(CircleShape)
                .background(if (c == 0L) Color.White else Color(c.toInt()))
                .border(if (c == selected) 3.dp else 1.5.dp, if (c == selected) BP.Gold else BP.Line, CircleShape)
                .clickable { onPick(c) }, contentAlignment = Alignment.Center) {
                if (c == 0L) Text("A", fontWeight = FontWeight.Bold, color = BP.Grey, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AdvancedPanel(vm: MainViewModel, s: AppSettings) {
    val st = s.style
    val p = vm.product(vm.selProductId)
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.saveLogo(uri) }
    var extra by remember(st.extraText) { mutableStateOf(st.extraText) }
    var wTxt by remember(s.widthMm) { mutableStateOf(fmtMm(s.widthMm)) }
    var hTxt by remember(s.heightMm) { mutableStateOf(fmtMm(s.heightMm)) }
    val d = tr("Default", "ڈیفالٹ")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Ctl(tr("Font style", "فونٹ")) { Opts(listOf("simple" to tr("Simple", "سادہ"), "modern" to tr("Modern", "ماڈرن")), st.font) { v -> vm.updateStyle { it.copy(font = v) } } }
        Ctl(tr("Font size", "فونٹ سائز"), "${st.fontScale}%") {
            Slider(st.fontScale.toFloat(), { v -> vm.updateStyle { it.copy(fontScale = (v / 5).toInt() * 5) } }, valueRange = 70f..150f,
                colors = SliderDefaults.colors(thumbColor = BP.Choc, activeTrackColor = BP.Choc, inactiveTrackColor = BP.Beige2))
        }
        Ctl(tr("Font weight", "موٹائی")) {
            Opts(listOf("0" to d, "500" to "Medium", "700" to "Bold", "900" to "Extra Bold"), st.weight.toString()) { v -> vm.updateStyle { it.copy(weight = v.toInt()) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                Ctl(tr("Text colour", "ٹیکسٹ رنگ")) {
                    Swatches(listOf(0L to d, 0xFF000000 to "Black", 0xFF493629 to "Brown"), st.textColor) { v -> vm.updateStyle { it.copy(textColor = v) } }
                }
            }
            Box(Modifier.weight(1f)) {
                Ctl(tr("Background", "پس منظر")) {
                    Swatches(listOf(0L to d, 0xFFF7F3EC to "Ivory", 0xFFE6D5BC to "Kraft"), st.bgColor) { v -> vm.updateStyle { it.copy(bgColor = v) } }
                }
            }
        }
        Ctl(tr("Text alignment", "ترتیب")) {
            Opts(listOf("d" to d, "left" to tr("Left", "بائیں"), "center" to tr("Centre", "درمیان"), "right" to tr("Right", "دائیں")), st.align) { v -> vm.updateStyle { it.copy(align = v) } }
        }
        Ctl(tr("Text position", "جگہ")) {
            Opts(listOf("d" to d, "top" to tr("Top", "اوپر"), "center" to tr("Middle", "درمیان"), "bottom" to tr("Bottom", "نیچے")), st.position) { v -> vm.updateStyle { it.copy(position = v) } }
        }
        Ctl(tr("Border style", "بارڈر"), if (st.border != "d" && st.border != "none") "${"%.1f".format(st.borderMm)} mm" else null) {
            Opts(listOf("d" to d, "none" to tr("None", "کوئی نہیں"), "solid" to "Solid", "dashed" to "Dashed", "double" to "Double"), st.border) { v -> vm.updateStyle { it.copy(border = v) } }
            Spacer(Modifier.height(6.dp))
            Text(tr("Border thickness", "بارڈر موٹائی"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Slider(st.borderMm, { v -> vm.updateStyle { it.copy(borderMm = (v * 10).toInt() / 10f, border = if (it.border == "d" || it.border == "none") "solid" else it.border) } },
                valueRange = .2f..1.6f, colors = SliderDefaults.colors(thumbColor = BP.Choc, activeTrackColor = BP.Choc, inactiveTrackColor = BP.Beige2))
        }
        Ctl(tr("Line spacing", "فاصلہ"), if (st.spacing < 0) tr("Auto", "خودکار") else "×${"%.1f".format(st.spacing)}") {
            Slider(if (st.spacing < 0) 1f else st.spacing, { v -> vm.updateStyle { it.copy(spacing = (v * 10).toInt() / 10f) } }, valueRange = 0f..3f,
                colors = SliderDefaults.colors(thumbColor = BP.Choc, activeTrackColor = BP.Choc, inactiveTrackColor = BP.Beige2))
        }
        Ctl(tr("Label size (mm)", "لیبل سائز (mm)")) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BPTextField(wTxt, { wTxt = it; it.toFloatOrNull()?.takeIf { v -> v in 20f..150f }?.let { v -> vm.setSize(v, s.heightMm) } },
                    tr("Width", "چوڑائی"), Modifier.weight(1f), numeric = true)
                BPTextField(hTxt, { hTxt = it; it.toFloatOrNull()?.takeIf { v -> v in 15f..120f }?.let { v -> vm.setSize(s.widthMm, v) } },
                    tr("Height", "اونچائی"), Modifier.weight(1f), numeric = true)
            }
        }
        Ctl(tr("Logo (optional)", "لوگو (اختیاری)")) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(if (st.logoPath.isBlank()) tr("Choose image", "تصویر چنیں") else tr("Change logo", "لوگو بدلیں"),
                    { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    Modifier.weight(1f), Icons.Rounded.Image, BtnStyle.Soft, height = 46)
                if (st.logoPath.isNotBlank()) BigButton(tr("Remove", "ہٹائیں"), { vm.updateStyle { it.copy(logoPath = "") } }, style = BtnStyle.Outline, height = 46)
            }
        }
        Ctl(tr("Additional text (optional)", "اضافی متن (اختیاری)")) {
            BPTextField(extra, { extra = it.take(40); vm.updateStyle { s2 -> s2.copy(extraText = extra) } }, tr("Text", "متن"),
                placeholder = tr("e.g. Keep refrigerated", "مثلاً فریج میں رکھیں"))
            SwitchRow(tr("Show product name on label", "لیبل پر پروڈکٹ کا نام"), st.showProduct, { v -> vm.updateStyle { it.copy(showProduct = v) } })
            SwitchRow(tr("Add Urdu product name", "اردو نام بھی"), st.urduName, { v -> vm.updateStyle { it.copy(urduName = v, showProduct = v || it.showProduct) } })
        }
        Ctl(tr("Extra details on label (optional)", "لیبل پر مزید معلومات (اختیاری)")) {
            val miss = tr("Not set for this product. Add it in Edit Product.", "اس پروڈکٹ میں نہیں لکھا۔")
            SwitchRow(tr("Price", "قیمت"), st.showPrice, { v -> vm.updateStyle { it.copy(showPrice = v) } }, if (st.showPrice && p != null && p.price.isBlank()) miss else null)
            SwitchRow(tr("Weight", "وزن"), st.showWeight, { v -> vm.updateStyle { it.copy(showWeight = v) } }, if (st.showWeight && p != null && p.weight.isBlank()) miss else null)
            SwitchRow(tr("Batch number", "بیچ نمبر"), st.showBatch, { v -> vm.updateStyle { it.copy(showBatch = v) } })
            SwitchRow(tr("Storage instructions", "رکھنے کی ہدایات"), st.showStorage, { v -> vm.updateStyle { it.copy(showStorage = v) } }, if (st.showStorage && p != null && p.storage.isBlank()) miss else null)
            Text(tr("Price, weight and storage come from each product. Batch = product code + MFG date.", "قیمت، وزن اور ہدایات ہر پروڈکٹ سے آتی ہیں۔"),
                style = MaterialTheme.typography.bodySmall)
        }
        BigButton(tr("Reset to design default", "ڈیفالٹ پر واپس"), {
            vm.updateStyle { LabelStyle(urduName = it.urduName) }; vm.toast = "Design reset"
        }, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
    }
}
