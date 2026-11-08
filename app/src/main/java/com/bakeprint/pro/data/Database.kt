package com.bakeprint.pro.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val nameUr: String = "",
    val icon: String = "box",
    val prefix: String = "OT",
    val system: Boolean = false,
    val sort: Int = 0,
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String,
    val name: String,
    val nameUr: String = "",
    val categoryId: String,
    val code: String,
    val shelfLifeDays: Int,
    /** 0 = today, -1 = yesterday */
    val mfgOffsetDays: Int = 0,
    /** "auto" = expiry from shelf life, "ask" = worker sets it every time */
    val expiryMode: String = "auto",
    val storage: String = "",
    val price: String = "",
    val weight: String = "",
    val favourite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class PrintRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String?,
    val productName: String,
    /** ISO yyyy-MM-dd */
    val mfg: String,
    val exp: String,
    val quantity: Int,
    val copies: Int,
    val printedAt: Long,
    val printerName: String,
    val widthMm: Float,
    val heightMm: Float,
    val design: Int,
)

@Entity(tableName = "printers")
data class PrinterDevice(
    @PrimaryKey val id: String,
    val name: String,
    /** bt, wifi, usb, system */
    val type: String,
    /** BT MAC, IP address, or USB "vendorId:productId" */
    val address: String,
    val port: Int = 9100,
    /** tspl or escpos */
    val protocol: String = "tspl",
    val maxWidthMm: Int = 80,
    val isDefault: Boolean = false,
)

@Entity(tableName = "settings")
data class Setting(@PrimaryKey val key: String, val value: String)

@Dao
interface AppDao {
    @Query("SELECT * FROM categories ORDER BY sort, name")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM products ORDER BY createdAt")
    fun products(): Flow<List<Product>>

    @Query("SELECT * FROM history ORDER BY printedAt DESC")
    fun history(): Flow<List<PrintRecord>>

    @Query("SELECT * FROM printers ORDER BY isDefault DESC, name")
    fun printers(): Flow<List<PrinterDevice>>

    @Query("SELECT * FROM settings")
    fun settings(): Flow<List<Setting>>

    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM products") suspend fun allProducts(): List<Product>
    @Query("SELECT * FROM history") suspend fun allHistory(): List<PrintRecord>
    @Query("SELECT * FROM printers") suspend fun allPrinters(): List<PrinterDevice>
    @Query("SELECT * FROM settings") suspend fun allSettings(): List<Setting>
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int

    @Upsert suspend fun upsertCategory(c: Category)
    @Upsert suspend fun upsertCategories(c: List<Category>)
    @Upsert suspend fun upsertProduct(p: Product)
    @Upsert suspend fun upsertProducts(p: List<Product>)
    @Upsert suspend fun upsertPrinter(p: PrinterDevice)
    @Upsert suspend fun upsertPrinters(p: List<PrinterDevice>)
    @Upsert suspend fun upsertSetting(s: Setting)
    @Upsert suspend fun upsertSettings(s: List<Setting>)
    @Insert suspend fun insertHistory(r: List<PrintRecord>)

    @Delete suspend fun deleteProduct(p: Product)
    @Delete suspend fun deleteCategory(c: Category)
    @Delete suspend fun deletePrinter(p: PrinterDevice)
    @Query("DELETE FROM history WHERE id = :id") suspend fun deleteHistory(id: Long)

    @Query("UPDATE printers SET isDefault = (id = :id)")
    suspend fun setDefaultPrinter(id: String)

    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM products") suspend fun clearProducts()
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM printers") suspend fun clearPrinters()
    @Query("DELETE FROM settings") suspend fun clearSettings()

    @Transaction
    suspend fun replaceAll(
        cats: List<Category>, prods: List<Product>, hist: List<PrintRecord>,
        prns: List<PrinterDevice>, sets: List<Setting>,
    ) {
        clearCategories(); clearProducts(); clearHistory(); clearPrinters(); clearSettings()
        upsertCategories(cats); upsertProducts(prods); insertHistory(hist)
        upsertPrinters(prns); upsertSettings(sets)
    }
}

@Database(
    entities = [Category::class, Product::class, PrintRecord::class, PrinterDevice::class, Setting::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "bakeprint.db")
                .build().also { instance = it }
        }
    }
}
