@file:OptIn(ExperimentalLayoutApi::class)

package com.bakeprint.pro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakeprint.pro.data.*
import java.time.LocalDate
import java.time.ZoneId

/* ================= HOME ================= */

@Composable
fun HomeScreen(vm: MainViewModel, nav: Nav) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val prods by vm.products.collectAsStateWithLifecycle()
    val hist by vm.history.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val todayCount = hist.filter { java.time.Instant.ofEpochMilli(it.printedAt).atZone(ZoneId.systemDefault()).toLocalDate() == today }
        .sumOf { it.quantity * it.copies }
    val favs = prods.filter { it.favourite }
    val last = vm.product(s.lastProductId)
    val hour = java.time.LocalTime.now().hour

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo()
            Column(Modifier.weight(1f)) {
                Text("BAKEPRINT PRO", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 1.6.sp)
                Text(tr("Smart Bakery Label Maker", "سمارٹ بیکری لیبل میکر"), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { vm.set("lang", if (s.lang == "ur") "en" else "ur") }, shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = BP.White, contentColor = BP.Choc)) {
                Text(if (s.lang == "ur") "English" else "اردو", fontWeight = FontWeight.Bold)
            }
        }
        Column {
            Eyebrow(tr(if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening", "السلام علیکم"))
            if (s.bakeryName.isBlank()) {
                TextButton(onClick = nav.editName, contentPadding = PaddingValues(0.dp)) {
                    Text(tr("Add your bakery name", "بیکری کا نام لکھیں"), style = MaterialTheme.typography.headlineLarge, color = BP.Gold)
                }
            } else Text(s.bakeryName, style = MaterialTheme.typography.headlineLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(Modifier.weight(1f), prods.size, tr("Total products", "کل پروڈکٹس"))
            Stat(Modifier.weight(1f), todayCount, tr("Labels printed today", "آج کے لیبل"))
            Stat(Modifier.weight(1f), favs.size, tr("Favourite products", "پسندیدہ"))
        }
        if (last != null) {
            Surface(shape = RoundedCornerShape(26.dp), color = BP.Choc) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Eyebrow(tr("Quick Print", "فوری پرنٹ"))
                    Text(last.name, color = BP.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    val m = today.plusDays(last.mfgOffsetDays.toLong())
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("MFG " + Dates.format(m, s.dateFormat), color = BP.Beige, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("EXP " + Dates.format(m.plusDays(last.shelfLifeDays.toLong()), s.dateFormat), color = BP.Beige, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${fmtMm(s.widthMm)}×${fmtMm(s.heightMm)} mm", color = BP.Beige, fontSize = 13.sp)
                    }
                    BigButton(tr("Print ${s.quantity} labels", "${s.quantity} لیبل پرنٹ کریں"), {
                        nav.print(listOf(LabelJob(last, m, m.plusDays(last.shelfLifeDays.toLong()), s.quantity)))
                    }, Modifier.fillMaxWidth(), Icons.Rounded.Print, BtnStyle.Gold)
                }
            }
        }
        val def = printers.firstOrNull { it.isDefault } ?: printers.firstOrNull()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), Icons.Rounded.GridView, tr("Product Selection", "پروڈکٹ منتخب کریں"), tr("Product Select Karein", "")) { nav.go("products") }
            Tile(Modifier.weight(1f), Icons.Rounded.LocalOffer, tr("Label Designer", "لیبل ڈیزائنر"), tr("Label Banayein", "")) { nav.go("label") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), Icons.Rounded.History, tr("Printing History", "پرنٹ ہسٹری"), tr("Purane Labels", "")) { nav.go("history") }
            Tile(Modifier.weight(1f), Icons.Rounded.Print, tr("Printer Settings", "پرنٹر سیٹنگز"), def?.name ?: "") { nav.go("settings") }
        }
        SoftCard(Modifier.fillMaxWidth(), onClick = nav.bulk) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Rounded.Layers)
                Column(Modifier.weight(1f)) {
                    Text(tr("Bulk Print", "بلک پرنٹ"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(tr("Many products in one go", "ایک ساتھ کئی پروڈکٹس"), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = BP.Grey)
            }
        }
        if (favs.isNotEmpty()) {
            Text(tr("Favourites", "پسندیدہ پروڈکٹس"), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                favs.forEach { p -> ChoiceChip(p.name, false, { vm.selectProduct(p); nav.go("label") }, icon = Icons.Rounded.Star) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun Logo(size: Int = 46) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp)).background(BP.Choc), contentAlignment = Alignment.Center) {
        Box(Modifier.size((size * .62f).dp, (size * .42f).dp).border(1.6.dp, BP.Gold, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            Text("B", color = BP.Ivory, fontWeight = FontWeight.ExtraBold, fontSize = (size * .3f).sp)
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, n: Int, label: String) {
    SoftCard(modifier, padding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
        Text("$n", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
}

@Composable
private fun Tile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    SoftCard(modifier.heightIn(min = 124.dp), onClick = onClick, padding = PaddingValues(16.dp)) {
        IconBadge(icon)
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, lineHeight = 19.sp)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ================= PRODUCTS ================= */

@Composable
fun ProductsScreen(vm: MainViewModel, nav: Nav) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val prods by vm.products.collectAsStateWithLifecycle()
    val ur = LocalUr.current
    var openCat by rememberSaveable2<String?>(null)
    var q by remember { mutableStateOf("") }
    var form by remember { mutableStateOf<Pair<Product?, String?>?>(null) }
    var menuFor by remember { mutableStateOf<Product?>(null) }
    var newCat by remember { mutableStateOf(false) }
    var confirmDelCat by remember { mutableStateOf(false) }
    val catName = { c: Category -> if (ur) c.nameUr.ifBlank { c.name } else c.name }

    BackHandler(enabled = openCat != null) { openCat = null }

    val onPick = { p: Product -> vm.selectProduct(p); nav.go("label") }

    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val c = openCat?.let { id -> cats.firstOrNull { it.id == id } }
        if (c != null) {
            val list = prods.filter { it.categoryId == c.id }
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedButton(onClick = { openCat = null }, shape = CircleShape, colors = ButtonDefaults.outlinedButtonColors(containerColor = BP.White, contentColor = BP.Choc)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(tr("All categories", "تمام کیٹیگریز"), fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CatCircle(c.icon, 58)
                        Column {
                            Text(catName(c), style = MaterialTheme.typography.headlineMedium)
                            Text("${list.size} ${tr("products", "پروڈکٹس")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    AddProductButton { form = null to c.id }
                    if (list.isEmpty()) {
                        Text(tr("No products in this category yet.", "اس کیٹیگری میں ابھی کوئی پروڈکٹ نہیں۔"), color = BP.Grey)
                        if (!c.system) BigButton(if (confirmDelCat) tr("Tap again to delete", "تصدیق کے لیے دوبارہ ٹیپ کریں") else tr("Delete category", "کیٹیگری ختم کریں"),
                            { if (confirmDelCat) { vm.deleteCategory(c); openCat = null; confirmDelCat = false } else confirmDelCat = true },
                            icon = Icons.Rounded.Delete, style = BtnStyle.Danger)
                    }
                }
            }
            items(list, key = { it.id }, span = { GridItemSpan(2) }) { p -> ProductRow(p, c, { onPick(p) }, { vm.toggleFavourite(p) }, { menuFor = p }) }
        } else {
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ScreenTitle(tr("Product Select Karein", "پروڈکٹ منتخب کریں"), tr("Choose a product", "پروڈکٹ چنیں"))
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = BP.Grey) },
                        placeholder = { Text(tr("Search products or codes", "پروڈکٹ یا کوڈ تلاش کریں")) },
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BP.Gold, unfocusedBorderColor = BP.Line,
                            focusedContainerColor = BP.White, unfocusedContainerColor = BP.White))
                    AddProductButton { form = null to null }
                }
            }
            if (q.isNotBlank()) {
                val r = prods.filter { it.name.contains(q, true) || it.code.contains(q, true) || it.nameUr.contains(q) }
                if (r.isEmpty()) item(span = { GridItemSpan(2) }) {
                    Text(tr("No product found for “$q”", "“$q” کی کوئی پروڈکٹ نہیں ملی"), color = BP.Grey, modifier = Modifier.padding(20.dp))
                }
                items(r, key = { it.id }, span = { GridItemSpan(2) }) { p ->
                    ProductRow(p, cats.firstOrNull { it.id == p.categoryId }, { onPick(p) }, { vm.toggleFavourite(p) }, { menuFor = p })
                }
            } else {
                items(cats, key = { it.id }) { cat ->
                    val list = prods.filter { it.categoryId == cat.id }
                    CategoryCard(catName(cat), cat.icon, list.size, list.take(3).joinToString(" · ") { it.name }) { openCat = cat.id; confirmDelCat = false }
                }
                item {
                    Surface(onClick = { newCat = true }, shape = RoundedCornerShape(24.dp), color = BP.Ivory,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, BP.Grey.copy(alpha = .45f)), modifier = Modifier.heightIn(min = 170.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.Add, null, tint = BP.Grey, modifier = Modifier.size(28.dp))
                            Text(tr("New category", "نئی کیٹیگری"), fontWeight = FontWeight.Bold, color = BP.Grey)
                            Text(tr("Unlimited", "لامحدود"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(12.dp)) }
    }

    form?.let { (p, catId) -> ProductFormSheet(vm, p, catId) { form = null } }
    menuFor?.let { p -> ProductMenuSheet(vm, p, onDismiss = { menuFor = null }, onEdit = { menuFor = null; form = p to null }, onLabel = { menuFor = null; onPick(p) }) }
    if (newCat) CategorySheet(vm, { newCat = false }) { id -> newCat = false; openCat = id }
}

/** rememberSaveable for nullable String without extra imports elsewhere. */
@Composable
private fun <T> rememberSaveable2(initial: T): MutableState<T> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }

@Composable
private fun AddProductButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BP.Choc, contentColor = BP.White)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(BP.Gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = BP.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(tr("ADD PRODUCT", "پروڈکٹ شامل کریں"), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, fontSize = 16.sp)
    }
}

@Composable
private fun CatCircle(icon: String, size: Int = 54) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(BP.Ivory).border(1.dp, BP.Gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(icon), null, tint = BP.Gold, modifier = Modifier.size((size * .5f).dp))
    }
}

@Composable
private fun CategoryCard(name: String, icon: String, count: Int, sample: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = BP.White, shadowElevation = 1.5.dp, modifier = Modifier.heightIn(min = 170.dp)) {
        Box {
            Box(Modifier.align(Alignment.TopEnd).size(96.dp).clip(RoundedCornerShape(bottomStart = 96.dp)).background(
                Brush.linearGradient(listOf(BP.Beige, BP.Beige2))))
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CatCircle(icon)
                Spacer(Modifier.height(2.dp))
                Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("$count ${tr("ITEMS", "آئٹمز")}", color = BP.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = .8.sp)
                Text(sample.ifBlank { tr("Empty", "خالی") }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProductRow(p: Product, c: Category?, onPick: () -> Unit, onFav: () -> Unit, onMore: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onPick, color = BP.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(categoryIcon(c?.icon ?: "box"))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.code} · ${tr("Shelf life", "مدت")} ${p.shelfLifeDays} ${tr(if (p.shelfLifeDays == 1) "day" else "days", "دن")}",
                            style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
            IconButton(onClick = onFav) {
                Icon(if (p.favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr("Favourite", "پسندیدہ"), tint = if (p.favourite) BP.Gold else BP.Grey)
            }
            IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, tr("More", "مزید"), tint = BP.Grey) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductMenuSheet(vm: MainViewModel, p: Product, onDismiss: () -> Unit, onEdit: () -> Unit, onLabel: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    BPSheet(onDismiss) {
        SheetTitle(p.name, "${p.code} · ${p.shelfLifeDays} ${tr("days", "دن")}" + (if (p.storage.isNotBlank()) " · ${p.storage}" else ""), onDismiss)
        MenuRow(Icons.Rounded.LocalOffer, tr("Make label", "لیبل بنائیں"), onClick = onLabel)
        MenuRow(Icons.Rounded.Edit, tr("Edit", "تبدیل کریں"), onClick = onEdit)
        MenuRow(Icons.Rounded.ContentCopy, tr("Duplicate", "کاپی بنائیں")) { vm.duplicate(p); vm.toast = "Duplicated"; onDismiss() }
        MenuRow(if (p.favourite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
            if (p.favourite) tr("Remove from favourites", "پسندیدہ سے ہٹائیں") else tr("Add to favourites", "پسندیدہ میں شامل")) { vm.toggleFavourite(p); onDismiss() }
        MenuRow(Icons.Rounded.Delete, if (armed) tr("Tap again to delete", "تصدیق کے لیے دوبارہ ٹیپ کریں") else tr("Delete", "ختم کریں"), danger = true) {
            if (armed) { vm.deleteProduct(p); vm.toast = "Product deleted"; onDismiss() } else armed = true
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, sub: String? = null, danger: Boolean = false,
            trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = BP.White) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon, 40, tint = if (danger) BP.Danger else BP.Choc)
            Column(Modifier.weight(1f)) {
                Text(text, fontWeight = FontWeight.Bold, color = if (danger) BP.Danger else BP.Choc)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
    }
}
