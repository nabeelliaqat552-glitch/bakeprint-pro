package com.bakeprint.pro.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(12)

/* ---------------- Dates ---------------- */

object Dates {
    /** DMY = DD-MM-YYYY, DMYs = DD/MM/YYYY, YMD = YYYY-MM-DD */
    fun format(d: LocalDate?, fmt: String): String {
        if (d == null) return "— — —"
        val dd = "%02d".format(d.dayOfMonth); val mm = "%02d".format(d.monthValue); val y = d.year.toString()
        return when (fmt) {
            "YMD" -> "$y-$mm-$dd"
            "DMYs" -> "$dd/$mm/$y"
            else -> "$dd-$mm-$y"
        }
    }

    fun hint(fmt: String) = when (fmt) { "YMD" -> "YYYY-MM-DD"; "DMYs" -> "DD/MM/YYYY"; else -> "DD-MM-YYYY" }

    /** Accepts DD-MM-YYYY, DD/MM/YYYY, DD.MM.YYYY and YYYY-MM-DD. */
    fun parse(text: String): LocalDate? {
        val t = text.trim()
        val ymd = Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$").find(t)
        val dmy = Regex("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$").find(t)
        return try {
            when {
                ymd != null -> LocalDate.of(ymd.groupValues[1].toInt(), ymd.groupValues[2].toInt(), ymd.groupValues[3].toInt())
                dmy != null -> LocalDate.of(dmy.groupValues[3].toInt(), dmy.groupValues[2].toInt(), dmy.groupValues[1].toInt())
                else -> null
            }
        } catch (e: Exception) { null }
    }

    fun iso(d: LocalDate) = d.toString()
    fun fromIso(s: String?): LocalDate? = try { if (s.isNullOrBlank()) null else LocalDate.parse(s) } catch (e: Exception) { null }
}

/* ---------------- Label style (Advanced mode) ---------------- */

data class LabelStyle(
    val font: String = "simple",          // simple, modern
    val fontScale: Int = 100,             // 70..150 %
    val weight: Int = 0,                  // 0 = design default, 500, 700, 900
    val textColor: Long = 0,              // 0 = default (near black)
    val bgColor: Long = 0,                // 0 = default (white)
    val align: String = "d",              // d, left, center, right
    val position: String = "d",           // d, top, center, bottom
    val border: String = "d",             // d, none, solid, dashed, double
    val borderMm: Float = 0.5f,
    val spacing: Float = -1f,             // -1 = auto, else multiplier 0..3
    val logoPath: String = "",
    val extraText: String = "",
    val showProduct: Boolean = true,
    val urduName: Boolean = false,
    val showPrice: Boolean = false,
    val showWeight: Boolean = false,
    val showBatch: Boolean = false,
    val showStorage: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("font", font); put("fontScale", fontScale); put("weight", weight)
        put("textColor", textColor); put("bgColor", bgColor); put("align", align)
        put("position", position); put("border", border); put("borderMm", borderMm.toDouble())
        put("spacing", spacing.toDouble()); put("logoPath", logoPath); put("extraText", extraText)
        put("showProduct", showProduct); put("urduName", urduName); put("showPrice", showPrice)
        put("showWeight", showWeight); put("showBatch", showBatch); put("showStorage", showStorage)
    }.toString()

    companion object {
        fun fromJson(s: String?): LabelStyle {
            if (s.isNullOrBlank()) return LabelStyle()
            return try {
                val o = JSONObject(s); val d = LabelStyle()
                LabelStyle(
                    font = o.optString("font", d.font),
                    fontScale = o.optInt("fontScale", d.fontScale),
                    weight = o.optInt("weight", d.weight),
                    textColor = o.optLong("textColor", d.textColor),
                    bgColor = o.optLong("bgColor", d.bgColor),
                    align = o.optString("align", d.align),
                    position = o.optString("position", d.position),
                    border = o.optString("border", d.border),
                    borderMm = o.optDouble("borderMm", d.borderMm.toDouble()).toFloat(),
                    spacing = o.optDouble("spacing", d.spacing.toDouble()).toFloat(),
                    logoPath = o.optString("logoPath", ""),
                    extraText = o.optString("extraText", ""),
                    showProduct = o.optBoolean("showProduct", true),
                    urduName = o.optBoolean("urduName", false),
                    showPrice = o.optBoolean("showPrice", false),
                    showWeight = o.optBoolean("showWeight", false),
                    showBatch = o.optBoolean("showBatch", false),
                    showStorage = o.optBoolean("showStorage", false),
                )
            } catch (e: Exception) { LabelStyle() }
        }
    }
}

data class Calibration(
    val offsetXmm: Float = 0f,
    val offsetYmm: Float = 0f,
    val gapMm: Float = 2f,
    val density: Int = 8,     // 1..15
    val speed: Int = 4,       // 1..6
)

data class AppSettings(
    val bakeryName: String = "",
    val lang: String = "en",
    val dateFormat: String = "DMY",
    val lastProductId: String = "",
    val design: Int = 1,
    val widthMm: Float = 50f,
    val heightMm: Float = 30f,
    val quantity: Int = 20,
    val style: LabelStyle = LabelStyle(),
    val calib: Calibration = Calibration(),
) {
    companion object {
        fun from(list: List<Setting>): AppSettings {
            val m = list.associate { it.key to it.value }
            val d = AppSettings()
            return AppSettings(
                bakeryName = m["bakeryName"] ?: d.bakeryName,
                lang = m["lang"] ?: d.lang,
                dateFormat = m["dateFormat"] ?: d.dateFormat,
                lastProductId = m["lastProductId"] ?: "",
                design = m["design"]?.toIntOrNull()?.coerceIn(1, 8) ?: 1,
                widthMm = m["widthMm"]?.toFloatOrNull() ?: d.widthMm,
                heightMm = m["heightMm"]?.toFloatOrNull() ?: d.heightMm,
                quantity = m["quantity"]?.toIntOrNull() ?: d.quantity,
                style = LabelStyle.fromJson(m["style"]),
                calib = Calibration(
                    offsetXmm = m["calX"]?.toFloatOrNull() ?: 0f,
                    offsetYmm = m["calY"]?.toFloatOrNull() ?: 0f,
                    gapMm = m["calGap"]?.toFloatOrNull() ?: 2f,
                    density = m["calDensity"]?.toIntOrNull() ?: 8,
                    speed = m["calSpeed"]?.toIntOrNull() ?: 4,
                ),
            )
        }
    }
}

/* ---------------- Built-in catalogue ---------------- */

object Seed {
    private val ur = mapOf(
        "Chocolate Cake" to "چاکلیٹ کیک", "Vanilla Cake" to "ونیلا کیک", "Pineapple Cake" to "پائن ایپل کیک",
        "Red Velvet Cake" to "ریڈ ویلویٹ کیک", "Black Forest Cake" to "بلیک فاریسٹ کیک", "Fresh Cream Cake" to "فریش کریم کیک",
        "Cheese Cake" to "چیز کیک", "Cupcakes" to "کپ کیکس", "White Bread" to "سفید ڈبل روٹی", "Brown Bread" to "براؤن بریڈ",
        "Milk Bread" to "ملک بریڈ", "Sandwich Bread" to "سینڈوچ بریڈ", "Burger Buns" to "برگر بن", "Hot Dog Buns" to "ہاٹ ڈاگ بن",
        "Butter Cookies" to "بٹر کوکیز", "Chocolate Cookies" to "چاکلیٹ کوکیز", "Coconut Biscuits" to "ناریل بسکٹ",
        "Zeera Biscuits" to "زیرہ بسکٹ", "Peanut Cookies" to "مونگ پھلی کوکیز", "Chocolate Pastry" to "چاکلیٹ پیسٹری",
        "Pineapple Pastry" to "پائن ایپل پیسٹری", "Donuts" to "ڈونٹس", "Brownies" to "براؤنیز", "Muffins" to "مفنز",
        "Pizza" to "پیزا", "Patties" to "پیٹیز", "Samosa" to "سموسہ", "Sandwiches" to "سینڈوچ", "Croissants" to "کروسان",
        "Rusk" to "رس", "Fresh Milk" to "تازہ دودھ", "Yogurt (Dahi)" to "دہی", "Butter" to "مکھن", "Fresh Cream" to "بالائی",
        "Cheese" to "چیز", "Paneer" to "پنیر", "Lassi" to "لسی", "Desi Ghee" to "دیسی گھی",
    )

    fun categories() = listOf(
        Category("cakes", "Cakes", "کیکس", "cake", "CK", true, 1),
        Category("bread", "Bread", "بریڈ", "bread", "BR", true, 2),
        Category("biscuits", "Biscuits", "بسکٹ", "cookie", "BS", true, 3),
        Category("pastries", "Pastries", "پیسٹریز", "donut", "PS", true, 4),
        Category("snacks", "Snacks", "اسنیکس", "samosa", "SN", true, 5),
        Category("dairy", "Dairy", "ڈیری", "milk", "DR", true, 6),
    )

    fun products(): List<Product> {
        val data = linkedMapOf(
            "cakes" to listOf("Chocolate Cake" to 3, "Vanilla Cake" to 3, "Pineapple Cake" to 3, "Red Velvet Cake" to 3,
                "Black Forest Cake" to 2, "Fresh Cream Cake" to 2, "Cheese Cake" to 4, "Cupcakes" to 3),
            "bread" to listOf("White Bread" to 4, "Brown Bread" to 4, "Milk Bread" to 3, "Sandwich Bread" to 4,
                "Burger Buns" to 3, "Hot Dog Buns" to 3),
            "biscuits" to listOf("Butter Cookies" to 30, "Chocolate Cookies" to 30, "Coconut Biscuits" to 30,
                "Zeera Biscuits" to 45, "Peanut Cookies" to 30),
            "pastries" to listOf("Chocolate Pastry" to 2, "Pineapple Pastry" to 2, "Donuts" to 2, "Brownies" to 5, "Muffins" to 4),
            "snacks" to listOf("Pizza" to 1, "Patties" to 2, "Samosa" to 1, "Sandwiches" to 1, "Croissants" to 2, "Rusk" to 60),
            "dairy" to listOf("Fresh Milk" to 2, "Yogurt (Dahi)" to 3, "Butter" to 15, "Fresh Cream" to 5, "Cheese" to 20,
                "Paneer" to 4, "Lassi" to 2, "Desi Ghee" to 90),
        )
        val prefix = categories().associate { it.id to it.prefix }
        val cold = setOf("Black Forest Cake", "Fresh Cream Cake", "Cheese Cake", "Chocolate Pastry", "Pineapple Pastry",
            "Fresh Milk", "Yogurt (Dahi)", "Butter", "Fresh Cream", "Cheese", "Paneer", "Lassi")
        val favs = setOf("Chocolate Cake", "White Bread", "Patties", "Zeera Biscuits")
        var t = System.currentTimeMillis()
        return data.flatMap { (cat, items) ->
            items.mapIndexed { i, (name, shelf) ->
                Product(
                    id = newId(), name = name, nameUr = ur[name] ?: "", categoryId = cat,
                    code = "${prefix[cat]}-${"%03d".format(i + 1)}", shelfLifeDays = shelf,
                    storage = when {
                        name == "Desi Ghee" -> "Store in a cool, dry place"
                        name in cold -> "Keep refrigerated at 2–8 °C"
                        else -> ""
                    },
                    favourite = name in favs, createdAt = t++,
                )
            }
        }
    }

    fun printers() = listOf(
        PrinterDevice("system", "Phone printing / PDF", "system", "Android Print Service", 0, "pdf", 210, true),
    )
}

/* ---------------- Backup (JSON) ---------------- */

object Backup {
    fun categoryJson(c: Category) = JSONObject().apply {
        put("id", c.id); put("name", c.name); put("nameUr", c.nameUr); put("icon", c.icon)
        put("prefix", c.prefix); put("system", c.system); put("sort", c.sort)
    }
    fun productJson(p: Product) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("nameUr", p.nameUr); put("categoryId", p.categoryId)
        put("code", p.code); put("shelfLifeDays", p.shelfLifeDays); put("mfgOffsetDays", p.mfgOffsetDays)
        put("expiryMode", p.expiryMode); put("storage", p.storage); put("price", p.price); put("weight", p.weight)
        put("favourite", p.favourite); put("createdAt", p.createdAt)
    }
    fun category(o: JSONObject) = Category(o.getString("id"), o.getString("name"), o.optString("nameUr"),
        o.optString("icon", "box"), o.optString("prefix", "OT"), o.optBoolean("system"), o.optInt("sort"))
    fun product(o: JSONObject) = Product(o.optString("id", newId()), o.getString("name"), o.optString("nameUr"),
        o.optString("categoryId", "cakes"), o.optString("code"), o.optInt("shelfLifeDays", 3), o.optInt("mfgOffsetDays"),
        o.optString("expiryMode", "auto"), o.optString("storage"), o.optString("price"), o.optString("weight"),
        o.optBoolean("favourite"), o.optLong("createdAt", System.currentTimeMillis()))

    fun productsExport(cats: List<Category>, prods: List<Product>): String = JSONObject().apply {
        put("app", "BakePrint Pro"); put("kind", "products"); put("version", 1)
        put("categories", JSONArray(cats.map { categoryJson(it) }))
        put("products", JSONArray(prods.map { productJson(it) }))
    }.toString(2)

    fun fullExport(cats: List<Category>, prods: List<Product>, hist: List<PrintRecord>, prns: List<PrinterDevice>, sets: List<Setting>): String =
        JSONObject().apply {
            put("app", "BakePrint Pro"); put("kind", "full"); put("version", 1)
            put("categories", JSONArray(cats.map { categoryJson(it) }))
            put("products", JSONArray(prods.map { productJson(it) }))
            put("history", JSONArray(hist.map { h ->
                JSONObject().apply {
                    put("productId", h.productId ?: ""); put("productName", h.productName); put("mfg", h.mfg); put("exp", h.exp)
                    put("quantity", h.quantity); put("copies", h.copies); put("printedAt", h.printedAt)
                    put("printerName", h.printerName); put("widthMm", h.widthMm.toDouble()); put("heightMm", h.heightMm.toDouble())
                    put("design", h.design)
                }
            }))
            put("printers", JSONArray(prns.map { p ->
                JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("type", p.type); put("address", p.address); put("port", p.port)
                    put("protocol", p.protocol); put("maxWidthMm", p.maxWidthMm); put("isDefault", p.isDefault)
                }
            }))
            put("settings", JSONObject().apply { sets.forEach { put(it.key, it.value) } })
        }.toString(2)

    data class Full(val cats: List<Category>, val prods: List<Product>, val hist: List<PrintRecord>,
                    val prns: List<PrinterDevice>, val sets: List<Setting>)

    fun <T> JSONArray?.mapObj(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

    fun parseFull(text: String): Full? = try {
        val o = JSONObject(text)
        if (o.optString("kind") != "full") null else Full(
            o.optJSONArray("categories").mapObj { category(it) },
            o.optJSONArray("products").mapObj { product(it) },
            o.optJSONArray("history").mapObj {
                PrintRecord(0, it.optString("productId").ifBlank { null }, it.getString("productName"), it.getString("mfg"),
                    it.getString("exp"), it.optInt("quantity", 1), it.optInt("copies", 1), it.optLong("printedAt"),
                    it.optString("printerName"), it.optDouble("widthMm", 50.0).toFloat(), it.optDouble("heightMm", 30.0).toFloat(),
                    it.optInt("design", 1))
            },
            o.optJSONArray("printers").mapObj {
                PrinterDevice(it.getString("id"), it.getString("name"), it.getString("type"), it.optString("address"),
                    it.optInt("port", 9100), it.optString("protocol", "tspl"), it.optInt("maxWidthMm", 80), it.optBoolean("isDefault"))
            },
            o.optJSONObject("settings")?.let { s -> s.keys().asSequence().map { Setting(it, s.getString(it)) }.toList() } ?: emptyList(),
        )
    } catch (e: Exception) { null }

    /** Accepts a products export or a full backup. */
    fun parseProducts(text: String): Pair<List<Category>, List<Product>>? = try {
        val o = JSONObject(text)
        val prods = o.optJSONArray("products") ?: return null
        o.optJSONArray("categories").mapObj { category(it) } to prods.mapObj { product(it) }
    } catch (e: Exception) { null }
}
