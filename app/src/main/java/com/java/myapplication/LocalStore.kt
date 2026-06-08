package com.java.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val TYPE_EXPENSE = "支出"
const val TYPE_INCOME = "收入"

const val CYCLE_NATURAL_MONTH = "自然月"
const val CYCLE_CUSTOM = "自定义周期"
const val ADJUST_ALLOWANCE = "补贴"
const val ADJUST_DEDUCTION = "扣款"
const val ADJUST_BONUS = "奖金"
const val ADJUST_FINE = "罚款"
const val ADJUST_REIMBURSE = "报销"
const val HOLIDAY_LEGAL = "法定节假日"
const val HOLIDAY_WORKDAY = "调休工作日"

private fun nowMs(): Long = System.currentTimeMillis()
fun dayText(ms: Long = nowMs()): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ms))
fun monthText(ms: Long = nowMs()): String = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date(ms))
fun timeText(ms: Long = nowMs()): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ms))
fun dateTimeText(ms: Long = nowMs()): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))
fun yuan(v: Double): String = "¥" + String.format(Locale.CHINA, "%.2f", v)

data class WorkRecord(
    val id: Long = 0,
    val date: String = dayText(),
    val startTime: String = "09:00",
    val endTime: String = "18:00",
    val breakHours: Double = 1.0,
    val hourlyRate: Double = 20.0,
    val overtime: Boolean = false,
    val note: String = "",
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
    val shiftType: String = "白班",
    val workType: String = "正常上班",
    val autoOvertimeMultiplier: Double = 1.0,
    val manualOvertimeMultiplier: Double? = null,
    val overtimeHours: Double = 0.0,
    val normalHours: Double = 0.0,
    val allowance: Double = 0.0,
    val deduction: Double = 0.0,
    val compLeaveHours: Double = 0.0,
    val isHoliday: Boolean = false,
    val holidayName: String = ""
) {
    val hours: Double get() = ((minutes(endTime) - minutes(startTime)).coerceAtLeast(0) / 60.0 - breakHours).coerceAtLeast(0.0)
    val effectiveNormalHours: Double get() = if (normalHours > 0.0 || overtimeHours > 0.0) normalHours else if (overtime) 0.0 else hours
    val effectiveOvertimeHours: Double get() = if (normalHours > 0.0 || overtimeHours > 0.0) overtimeHours else if (overtime || effectiveMultiplier > 1.0) hours else 0.0
    val effectiveMultiplier: Double get() = manualOvertimeMultiplier ?: autoOvertimeMultiplier
    val wage: Double get() = effectiveNormalHours * hourlyRate + effectiveOvertimeHours * hourlyRate * effectiveMultiplier + allowance - deduction
}

data class SalarySettings(
    val id: Long = 1,
    val baseSalary: Double = 0.0,
    val defaultHourlyRate: Double = 20.0,
    val standardDayHours: Double = 8.0,
    val paidDaysPerMonth: Double = 21.75,
    val cycleType: String = CYCLE_NATURAL_MONTH,
    val customStartDay: Int = 1,
    val customEndDay: Int = 31,
    val socialBase: Double = 0.0,
    val housingBase: Double = 0.0,
    val pensionRate: Double = 0.08,
    val medicalRate: Double = 0.02,
    val unemploymentRate: Double = 0.005,
    val housingRate: Double = 0.07,
    val taxThreshold: Double = 5000.0,
    val specialDeduction: Double = 0.0,
    val otherPreTaxDeduction: Double = 0.0,
    val cumulativeTaxEnabled: Boolean = false,
    val manualTax: Double? = null,
    val defaultAllowance: Double = 0.0,
    val defaultDeduction: Double = 0.0
)

data class SalaryAdjustment(val id: Long = 0, val type: String = ADJUST_ALLOWANCE, val amount: Double = 0.0, val date: String = dayText(), val period: String = monthText(), val note: String = "", val createdAt: Long = nowMs(), val updatedAt: Long = nowMs())
data class HolidayRecord(val id: Long = 0, val date: String = dayText(), val name: String = "", val type: String = HOLIDAY_LEGAL, val multiplier: Double = 3.0, val enabled: Boolean = true)

data class SalarySummary(
    val period: String,
    val startDate: String,
    val endDate: String,
    val totalHours: Double,
    val normalHours: Double,
    val overtimeHours: Double,
    val weekdayOvertimeHours: Double,
    val weekendOvertimeHours: Double,
    val holidayOvertimeHours: Double,
    val baseSalary: Double,
    val normalWage: Double,
    val overtimeWage: Double,
    val allowance: Double,
    val bonus: Double,
    val deduction: Double,
    val socialSecurity: Double,
    val housingFund: Double,
    val taxableIncome: Double,
    val tax: Double,
    val grossSalary: Double,
    val netSalary: Double,
    val compLeaveHours: Double,
    val shiftStats: Map<String, Pair<Int, Double>>
)

data class SalaryPeriod(val label: String, val start: String, val end: String)

data class AccountRecord(val id: Long = 0, val type: String = TYPE_EXPENSE, val amount: Double = 0.0, val category: String = "餐饮", val account: String = "默认账户", val dateTime: String = dateTimeText(), val note: String = "", val createdAt: Long = nowMs(), val updatedAt: Long = nowMs())
data class NoteRecord(val id: Long = 0, val title: String = "", val content: String = "", val tags: String = "", val pinned: Boolean = false, val createdAt: Long = nowMs(), val updatedAt: Long = nowMs())
data class TodoRecord(val id: Long = 0, val title: String = "", val description: String = "", val dueDate: String = dayText(), val priority: String = "中", val done: Boolean = false, val doneAt: Long = 0, val createdAt: Long = nowMs(), val updatedAt: Long = nowMs())

fun minutes(text: String): Int { val p = text.split(":"); return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) }

fun autoMultiplierForDate(date: String, holidays: List<HolidayRecord> = emptyList(), overtime: Boolean = false): Double {
    val h = holidays.firstOrNull { it.enabled && it.date == date }
    if (h != null) return if (h.type == HOLIDAY_WORKDAY) if (overtime) 1.5 else 1.0 else h.multiplier
    val d = parseLocalDate(date) ?: return if (overtime) 1.5 else 1.0
    val dow = Calendar.getInstance(Locale.CHINA).apply { time = d }.get(Calendar.DAY_OF_WEEK)
    return when (dow) { Calendar.SATURDAY, Calendar.SUNDAY -> 2.0; else -> if (overtime) 1.5 else 1.0 }
}

fun salaryPeriodOf(date: String, settings: SalarySettings): SalaryPeriod {
    if (settings.cycleType != CYCLE_CUSTOM) return SalaryPeriod(date.take(7), date.take(7) + "-01", monthEnd(date.take(7)))
    val d = parseLocalDate(date) ?: return SalaryPeriod(date.take(7), date.take(7) + "-01", monthEnd(date.take(7)))
    val cal = Calendar.getInstance(Locale.CHINA).apply { time = d }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val labelCal = cal.clone() as Calendar
    if (day >= settings.customStartDay) labelCal.add(Calendar.MONTH, 1)
    val label = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(labelCal.time)
    val startCal = labelCal.clone() as Calendar; startCal.add(Calendar.MONTH, -1); startCal.set(Calendar.DAY_OF_MONTH, settings.customStartDay.coerceIn(1, startCal.getActualMaximum(Calendar.DAY_OF_MONTH)))
    val endCal = labelCal.clone() as Calendar; endCal.set(Calendar.DAY_OF_MONTH, settings.customEndDay.coerceIn(1, endCal.getActualMaximum(Calendar.DAY_OF_MONTH)))
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    return SalaryPeriod(label, fmt.format(startCal.time), fmt.format(endCal.time))
}

fun calculateSalary(records: List<WorkRecord>, settings: SalarySettings, adjustments: List<SalaryAdjustment> = emptyList(), period: SalaryPeriod = salaryPeriodOf(dayText(), settings)): SalarySummary {
    val rs = records.filter { it.date in period.start..period.end }
    val normalHours = rs.sumOf { it.effectiveNormalHours }
    val overtimeHours = rs.sumOf { it.effectiveOvertimeHours }
    val normalWage = rs.sumOf { it.effectiveNormalHours * it.hourlyRate }
    val overtimeWage = rs.sumOf { it.effectiveOvertimeHours * it.hourlyRate * it.effectiveMultiplier }
    val rowAllowance = rs.sumOf { it.allowance }
    val rowDeduction = rs.sumOf { it.deduction }
    val ads = adjustments.filter { it.date in period.start..period.end || it.period == period.label }
    val allowance = rowAllowance + ads.filter { it.type in listOf(ADJUST_ALLOWANCE, ADJUST_REIMBURSE) }.sumOf { it.amount }
    val bonus = ads.filter { it.type == ADJUST_BONUS }.sumOf { it.amount }
    val deduction = rowDeduction + ads.filter { it.type in listOf(ADJUST_DEDUCTION, ADJUST_FINE) }.sumOf { it.amount }
    val social = settings.socialBase * (settings.pensionRate + settings.medicalRate + settings.unemploymentRate)
    val housing = settings.housingBase * settings.housingRate
    val gross = settings.baseSalary + normalWage + overtimeWage + allowance + bonus - deduction
    val taxable = (gross - social - housing - settings.taxThreshold - settings.specialDeduction - settings.otherPreTaxDeduction).coerceAtLeast(0.0)
    val tax = settings.manualTax ?: personalTax(taxable)
    val weekdayOt = rs.filter { it.effectiveMultiplier == 1.5 }.sumOf { it.effectiveOvertimeHours }
    val weekendOt = rs.filter { it.effectiveMultiplier == 2.0 }.sumOf { it.effectiveOvertimeHours }
    val holidayOt = rs.filter { it.effectiveMultiplier >= 3.0 }.sumOf { it.effectiveOvertimeHours }
    val shiftStats = rs.groupBy { it.shiftType.ifBlank { "未分类" } }.mapValues { it.value.size to it.value.sumOf { r -> r.hours } }
    return SalarySummary(period.label, period.start, period.end, rs.sumOf { it.hours }, normalHours, overtimeHours, weekdayOt, weekendOt, holidayOt, settings.baseSalary, normalWage, overtimeWage, allowance, bonus, deduction, social, housing, taxable, tax, gross, gross - social - housing - tax, rs.sumOf { it.compLeaveHours }, shiftStats)
}

fun personalTax(taxable: Double): Double { val (rate, quick) = when { taxable <= 3000 -> 0.03 to 0.0; taxable <= 12000 -> 0.10 to 210.0; taxable <= 25000 -> 0.20 to 1410.0; taxable <= 35000 -> 0.25 to 2660.0; taxable <= 55000 -> 0.30 to 4410.0; taxable <= 80000 -> 0.35 to 7160.0; else -> 0.45 to 15160.0 }; return (taxable * rate - quick).coerceAtLeast(0.0) }
private fun parseLocalDate(text: String): Date? = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { isLenient = false }.parse(text) }.getOrNull()
private fun monthEnd(month: String): String { val d = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse("$month-01") ?: return "$month-31"; val c = Calendar.getInstance(Locale.CHINA).apply { time = d }; return String.format(Locale.CHINA, "%s-%02d", month, c.getActualMaximum(Calendar.DAY_OF_MONTH)) }

class LocalStore(context: Context) : SQLiteOpenHelper(context, "personal_local_book.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) { createCoreTables(db); createSalaryTables(db); seedDefaults(db) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) { migrateWorkV2(db); createSalaryTables(db); seedDefaults(db) } }
    private fun createCoreTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS work_records(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, startTime TEXT, endTime TEXT, breakHours REAL, hourlyRate REAL, overtime INTEGER, note TEXT, createdAt INTEGER, updatedAt INTEGER, shiftType TEXT DEFAULT '白班', workType TEXT DEFAULT '正常上班', autoOvertimeMultiplier REAL DEFAULT 1.0, manualOvertimeMultiplier REAL, overtimeHours REAL DEFAULT 0, normalHours REAL DEFAULT 0, allowance REAL DEFAULT 0, deduction REAL DEFAULT 0, compLeaveHours REAL DEFAULT 0, isHoliday INTEGER DEFAULT 0, holidayName TEXT DEFAULT '')""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS account_records(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, amount REAL, category TEXT, account TEXT, dateTime TEXT, note TEXT, createdAt INTEGER, updatedAt INTEGER)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, content TEXT, tags TEXT, pinned INTEGER, createdAt INTEGER, updatedAt INTEGER)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS todos(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, description TEXT, dueDate TEXT, priority TEXT, done INTEGER, doneAt INTEGER, createdAt INTEGER, updatedAt INTEGER)""")
    }
    private fun migrateWorkV2(db: SQLiteDatabase) { listOf("shiftType TEXT DEFAULT '白班'", "workType TEXT DEFAULT '正常上班'", "autoOvertimeMultiplier REAL DEFAULT 1.0", "manualOvertimeMultiplier REAL", "overtimeHours REAL DEFAULT 0", "normalHours REAL DEFAULT 0", "allowance REAL DEFAULT 0", "deduction REAL DEFAULT 0", "compLeaveHours REAL DEFAULT 0", "isHoliday INTEGER DEFAULT 0", "holidayName TEXT DEFAULT ''").forEach { runCatching { db.execSQL("ALTER TABLE work_records ADD COLUMN $it") } } }
    private fun createSalaryTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS salary_settings(id INTEGER PRIMARY KEY, baseSalary REAL, defaultHourlyRate REAL, standardDayHours REAL, paidDaysPerMonth REAL, cycleType TEXT, customStartDay INTEGER, customEndDay INTEGER, socialBase REAL, housingBase REAL, pensionRate REAL, medicalRate REAL, unemploymentRate REAL, housingRate REAL, taxThreshold REAL, specialDeduction REAL, otherPreTaxDeduction REAL, cumulativeTaxEnabled INTEGER, manualTax REAL, defaultAllowance REAL, defaultDeduction REAL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS salary_adjustments(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, amount REAL, date TEXT, period TEXT, note TEXT, createdAt INTEGER, updatedAt INTEGER)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS holiday_records(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT UNIQUE, name TEXT, type TEXT, multiplier REAL, enabled INTEGER)""")
    }
    private fun seedDefaults(db: SQLiteDatabase) { val count = db.rawQuery("SELECT COUNT(*) FROM salary_settings", null).use { it.moveToFirst(); it.getInt(0) }; if (count == 0) db.insert("salary_settings", null, SalarySettings().values()) }

    fun allWork(): List<WorkRecord> = readableDatabase.rawQuery("SELECT * FROM work_records ORDER BY date DESC,id DESC", null).use { c -> buildList { while (c.moveToNext()) add(c.work()) } }
    fun saveWork(r: WorkRecord) { val v = r.values().apply { put("createdAt", if (r.id == 0L) nowMs() else r.createdAt); put("updatedAt", nowMs()) }; if (r.id == 0L) writableDatabase.insert("work_records", null, v) else writableDatabase.update("work_records", v, "id=?", arrayOf(r.id.toString())) }
    fun deleteWork(id: Long) { writableDatabase.delete("work_records", "id=?", arrayOf(id.toString())) }

    fun salarySettings(): SalarySettings = readableDatabase.rawQuery("SELECT * FROM salary_settings WHERE id=1", null).use { if (it.moveToFirst()) it.salarySettings() else SalarySettings() }
    fun saveSalarySettings(s: SalarySettings) { writableDatabase.insertWithOnConflict("salary_settings", null, s.copy(id = 1).values(), SQLiteDatabase.CONFLICT_REPLACE) }
    fun allSalaryAdjustments(): List<SalaryAdjustment> = readableDatabase.rawQuery("SELECT * FROM salary_adjustments ORDER BY date DESC,id DESC", null).use { c -> buildList { while (c.moveToNext()) add(c.adjustment()) } }
    fun saveSalaryAdjustment(a: SalaryAdjustment) { val v = a.values().apply { put("createdAt", if (a.id == 0L) nowMs() else a.createdAt); put("updatedAt", nowMs()) }; if (a.id == 0L) writableDatabase.insert("salary_adjustments", null, v) else writableDatabase.update("salary_adjustments", v, "id=?", arrayOf(a.id.toString())) }
    fun deleteSalaryAdjustment(id: Long) { writableDatabase.delete("salary_adjustments", "id=?", arrayOf(id.toString())) }
    fun allHolidays(): List<HolidayRecord> = readableDatabase.rawQuery("SELECT * FROM holiday_records ORDER BY date DESC", null).use { c -> buildList { while (c.moveToNext()) add(c.holiday()) } }
    fun saveHoliday(h: HolidayRecord) { val v = h.values(); if (h.id == 0L) writableDatabase.insertWithOnConflict("holiday_records", null, v, SQLiteDatabase.CONFLICT_REPLACE) else writableDatabase.update("holiday_records", v, "id=?", arrayOf(h.id.toString())) }
    fun deleteHoliday(id: Long) { writableDatabase.delete("holiday_records", "id=?", arrayOf(id.toString())) }

    fun allAccounts(): List<AccountRecord> = readableDatabase.rawQuery("SELECT * FROM account_records ORDER BY dateTime DESC,id DESC", null).use { c -> buildList { while (c.moveToNext()) add(c.account()) } }
    fun saveAccount(r: AccountRecord) { val v = ContentValues().apply { put("type", r.type); put("amount", r.amount); put("category", r.category); put("account", r.account); put("dateTime", r.dateTime); put("note", r.note); put("createdAt", if (r.id == 0L) nowMs() else r.createdAt); put("updatedAt", nowMs()) }; if (r.id == 0L) writableDatabase.insert("account_records", null, v) else writableDatabase.update("account_records", v, "id=?", arrayOf(r.id.toString())) }
    fun deleteAccount(id: Long) { writableDatabase.delete("account_records", "id=?", arrayOf(id.toString())) }
    fun allNotes(): List<NoteRecord> = readableDatabase.rawQuery("SELECT * FROM notes ORDER BY pinned DESC,updatedAt DESC", null).use { c -> buildList { while (c.moveToNext()) add(c.note()) } }
    fun saveNote(r: NoteRecord) { val v = ContentValues().apply { put("title", r.title.ifBlank { "无标题笔记" }); put("content", r.content); put("tags", r.tags); put("pinned", if (r.pinned) 1 else 0); put("createdAt", if (r.id == 0L) nowMs() else r.createdAt); put("updatedAt", nowMs()) }; if (r.id == 0L) writableDatabase.insert("notes", null, v) else writableDatabase.update("notes", v, "id=?", arrayOf(r.id.toString())) }
    fun deleteNote(id: Long) { writableDatabase.delete("notes", "id=?", arrayOf(id.toString())) }
    fun allTodos(): List<TodoRecord> = readableDatabase.rawQuery("SELECT * FROM todos ORDER BY done ASC,dueDate ASC,id DESC", null).use { c -> buildList { while (c.moveToNext()) add(c.todo()) } }
    fun saveTodo(r: TodoRecord) { val doneAt = if (r.done && r.doneAt == 0L) nowMs() else if (!r.done) 0L else r.doneAt; val v = ContentValues().apply { put("title", r.title.ifBlank { "未命名待办" }); put("description", r.description); put("dueDate", r.dueDate); put("priority", r.priority); put("done", if (r.done) 1 else 0); put("doneAt", doneAt); put("createdAt", if (r.id == 0L) nowMs() else r.createdAt); put("updatedAt", nowMs()) }; if (r.id == 0L) writableDatabase.insert("todos", null, v) else writableDatabase.update("todos", v, "id=?", arrayOf(r.id.toString())) }
    fun deleteTodo(id: Long) { writableDatabase.delete("todos", "id=?", arrayOf(id.toString())) }

    fun exportToJson(context: Context): File { val root = JSONObject().apply { put("version", 2); put("exportedAt", dateTimeText()); put("workRecords", JSONArray(allWork().map { it.json() })); put("accountRecords", JSONArray(allAccounts().map { it.json() })); put("notes", JSONArray(allNotes().map { it.json() })); put("todos", JSONArray(allTodos().map { it.json() })); put("salarySettings", salarySettings().json()); put("salaryAdjustments", JSONArray(allSalaryAdjustments().map { it.json() })); put("holidayRecords", JSONArray(allHolidays().map { it.json() })) }; val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }; return File(dir, "personal_book_${System.currentTimeMillis()}.json").apply { writeText(root.toString(2)) } }
    fun importJson(text: String, overwrite: Boolean) { val root = JSONObject(text); writableDatabase.beginTransaction(); try { if (overwrite) listOf("work_records", "account_records", "notes", "todos", "salary_adjustments", "holiday_records").forEach { writableDatabase.delete(it, null, null) }; root.optJSONArray("workRecords")?.let { arr -> for (i in 0 until arr.length()) saveWork(arr.getJSONObject(i).workFromJson()) }; root.optJSONArray("accountRecords")?.let { arr -> for (i in 0 until arr.length()) saveAccount(arr.getJSONObject(i).accountFromJson()) }; root.optJSONArray("notes")?.let { arr -> for (i in 0 until arr.length()) saveNote(arr.getJSONObject(i).noteFromJson()) }; root.optJSONArray("todos")?.let { arr -> for (i in 0 until arr.length()) saveTodo(arr.getJSONObject(i).todoFromJson()) }; root.optJSONObject("salarySettings")?.let { saveSalarySettings(it.salarySettingsFromJson()) }; root.optJSONArray("salaryAdjustments")?.let { arr -> for (i in 0 until arr.length()) saveSalaryAdjustment(arr.getJSONObject(i).adjustmentFromJson()) }; root.optJSONArray("holidayRecords")?.let { arr -> for (i in 0 until arr.length()) saveHoliday(arr.getJSONObject(i).holidayFromJson()) }; writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() } }
}

private fun Cursor.has(name: String) = getColumnIndex(name) >= 0
private fun Cursor.s(name: String, def: String = "") = if (has(name)) getString(getColumnIndexOrThrow(name)) ?: def else def
private fun Cursor.l(name: String, def: Long = 0L) = if (has(name)) getLong(getColumnIndexOrThrow(name)) else def
private fun Cursor.d(name: String, def: Double = 0.0) = if (has(name)) getDouble(getColumnIndexOrThrow(name)) else def
private fun Cursor.i(name: String, def: Int = 0) = if (has(name)) getInt(getColumnIndexOrThrow(name)) else def
private fun Cursor.b(name: String, def: Boolean = false) = if (has(name)) getInt(getColumnIndexOrThrow(name)) == 1 else def
private fun Cursor.nullableDouble(name: String): Double? = if (has(name) && !isNull(getColumnIndexOrThrow(name))) getDouble(getColumnIndexOrThrow(name)) else null
private fun Cursor.work() = WorkRecord(l("id"), s("date"), s("startTime"), s("endTime"), d("breakHours"), d("hourlyRate", 20.0), b("overtime"), s("note"), l("createdAt"), l("updatedAt"), s("shiftType", "白班"), s("workType", "正常上班"), d("autoOvertimeMultiplier", 1.0), nullableDouble("manualOvertimeMultiplier"), d("overtimeHours"), d("normalHours"), d("allowance"), d("deduction"), d("compLeaveHours"), b("isHoliday"), s("holidayName"))
private fun Cursor.account() = AccountRecord(l("id"), s("type"), d("amount"), s("category"), s("account"), s("dateTime"), s("note"), l("createdAt"), l("updatedAt"))
private fun Cursor.note() = NoteRecord(l("id"), s("title"), s("content"), s("tags"), b("pinned"), l("createdAt"), l("updatedAt"))
private fun Cursor.todo() = TodoRecord(l("id"), s("title"), s("description"), s("dueDate"), s("priority"), b("done"), l("doneAt"), l("createdAt"), l("updatedAt"))
private fun Cursor.salarySettings() = SalarySettings(1, d("baseSalary"), d("defaultHourlyRate",20.0), d("standardDayHours",8.0), d("paidDaysPerMonth",21.75), s("cycleType", CYCLE_NATURAL_MONTH), i("customStartDay",1), i("customEndDay",31), d("socialBase"), d("housingBase"), d("pensionRate",0.08), d("medicalRate",0.02), d("unemploymentRate",0.005), d("housingRate",0.07), d("taxThreshold",5000.0), d("specialDeduction"), d("otherPreTaxDeduction"), b("cumulativeTaxEnabled"), nullableDouble("manualTax"), d("defaultAllowance"), d("defaultDeduction"))
private fun Cursor.adjustment() = SalaryAdjustment(l("id"), s("type", ADJUST_ALLOWANCE), d("amount"), s("date"), s("period"), s("note"), l("createdAt"), l("updatedAt"))
private fun Cursor.holiday() = HolidayRecord(l("id"), s("date"), s("name"), s("type", HOLIDAY_LEGAL), d("multiplier",3.0), b("enabled", true))

private fun WorkRecord.values() = ContentValues().apply { put("date", date); put("startTime", startTime); put("endTime", endTime); put("breakHours", breakHours); put("hourlyRate", hourlyRate); put("overtime", if (overtime) 1 else 0); put("note", note); put("shiftType", shiftType); put("workType", workType); put("autoOvertimeMultiplier", autoOvertimeMultiplier); if (manualOvertimeMultiplier == null) putNull("manualOvertimeMultiplier") else put("manualOvertimeMultiplier", manualOvertimeMultiplier); put("overtimeHours", overtimeHours); put("normalHours", normalHours); put("allowance", allowance); put("deduction", deduction); put("compLeaveHours", compLeaveHours); put("isHoliday", if (isHoliday) 1 else 0); put("holidayName", holidayName) }
private fun SalarySettings.values() = ContentValues().apply { put("id", id); put("baseSalary", baseSalary); put("defaultHourlyRate", defaultHourlyRate); put("standardDayHours", standardDayHours); put("paidDaysPerMonth", paidDaysPerMonth); put("cycleType", cycleType); put("customStartDay", customStartDay); put("customEndDay", customEndDay); put("socialBase", socialBase); put("housingBase", housingBase); put("pensionRate", pensionRate); put("medicalRate", medicalRate); put("unemploymentRate", unemploymentRate); put("housingRate", housingRate); put("taxThreshold", taxThreshold); put("specialDeduction", specialDeduction); put("otherPreTaxDeduction", otherPreTaxDeduction); put("cumulativeTaxEnabled", if (cumulativeTaxEnabled) 1 else 0); if (manualTax == null) putNull("manualTax") else put("manualTax", manualTax); put("defaultAllowance", defaultAllowance); put("defaultDeduction", defaultDeduction) }
private fun SalaryAdjustment.values() = ContentValues().apply { put("type", type); put("amount", amount); put("date", date); put("period", period); put("note", note) }
private fun HolidayRecord.values() = ContentValues().apply { put("date", date); put("name", name); put("type", type); put("multiplier", multiplier); put("enabled", if (enabled) 1 else 0) }

private fun WorkRecord.json() = JSONObject().put("date", date).put("startTime", startTime).put("endTime", endTime).put("breakHours", breakHours).put("hourlyRate", hourlyRate).put("overtime", overtime).put("note", note).put("shiftType", shiftType).put("workType", workType).put("autoOvertimeMultiplier", autoOvertimeMultiplier).put("manualOvertimeMultiplier", manualOvertimeMultiplier).put("overtimeHours", overtimeHours).put("normalHours", normalHours).put("allowance", allowance).put("deduction", deduction).put("compLeaveHours", compLeaveHours).put("isHoliday", isHoliday).put("holidayName", holidayName)
private fun AccountRecord.json() = JSONObject().put("type", type).put("amount", amount).put("category", category).put("account", account).put("dateTime", dateTime).put("note", note)
private fun NoteRecord.json() = JSONObject().put("title", title).put("content", content).put("tags", tags).put("pinned", pinned)
private fun TodoRecord.json() = JSONObject().put("title", title).put("description", description).put("dueDate", dueDate).put("priority", priority).put("done", done)
private fun SalarySettings.json() = JSONObject().put("baseSalary", baseSalary).put("defaultHourlyRate", defaultHourlyRate).put("standardDayHours", standardDayHours).put("paidDaysPerMonth", paidDaysPerMonth).put("cycleType", cycleType).put("customStartDay", customStartDay).put("customEndDay", customEndDay).put("socialBase", socialBase).put("housingBase", housingBase).put("pensionRate", pensionRate).put("medicalRate", medicalRate).put("unemploymentRate", unemploymentRate).put("housingRate", housingRate).put("taxThreshold", taxThreshold).put("specialDeduction", specialDeduction).put("otherPreTaxDeduction", otherPreTaxDeduction).put("cumulativeTaxEnabled", cumulativeTaxEnabled).put("manualTax", manualTax).put("defaultAllowance", defaultAllowance).put("defaultDeduction", defaultDeduction)
private fun SalaryAdjustment.json() = JSONObject().put("type", type).put("amount", amount).put("date", date).put("period", period).put("note", note)
private fun HolidayRecord.json() = JSONObject().put("date", date).put("name", name).put("type", type).put("multiplier", multiplier).put("enabled", enabled)
private fun JSONObject.workFromJson() = WorkRecord(date = optString("date", dayText()), startTime = optString("startTime", "09:00"), endTime = optString("endTime", "18:00"), breakHours = optDouble("breakHours", 1.0), hourlyRate = optDouble("hourlyRate", 20.0), overtime = optBoolean("overtime", false), note = optString("note", ""), shiftType = optString("shiftType", "白班"), workType = optString("workType", "正常上班"), autoOvertimeMultiplier = optDouble("autoOvertimeMultiplier", 1.0), manualOvertimeMultiplier = if (isNull("manualOvertimeMultiplier")) null else optDouble("manualOvertimeMultiplier"), overtimeHours = optDouble("overtimeHours", 0.0), normalHours = optDouble("normalHours", 0.0), allowance = optDouble("allowance", 0.0), deduction = optDouble("deduction", 0.0), compLeaveHours = optDouble("compLeaveHours", 0.0), isHoliday = optBoolean("isHoliday", false), holidayName = optString("holidayName", ""))
private fun JSONObject.accountFromJson() = AccountRecord(type = optString("type", TYPE_EXPENSE), amount = optDouble("amount", 0.0), category = optString("category", "其他"), account = optString("account", "默认账户"), dateTime = optString("dateTime", dateTimeText()), note = optString("note", ""))
private fun JSONObject.noteFromJson() = NoteRecord(title = optString("title", "无标题笔记"), content = optString("content", ""), tags = optString("tags", ""), pinned = optBoolean("pinned", false))
private fun JSONObject.todoFromJson() = TodoRecord(title = optString("title", "未命名待办"), description = optString("description", ""), dueDate = optString("dueDate", dayText()), priority = optString("priority", "中"), done = optBoolean("done", false))
private fun JSONObject.salarySettingsFromJson() = SalarySettings(baseSalary = optDouble("baseSalary", 0.0), defaultHourlyRate = optDouble("defaultHourlyRate", 20.0), standardDayHours = optDouble("standardDayHours", 8.0), paidDaysPerMonth = optDouble("paidDaysPerMonth", 21.75), cycleType = optString("cycleType", CYCLE_NATURAL_MONTH), customStartDay = optInt("customStartDay", 1), customEndDay = optInt("customEndDay", 31), socialBase = optDouble("socialBase", 0.0), housingBase = optDouble("housingBase", 0.0), pensionRate = optDouble("pensionRate", 0.08), medicalRate = optDouble("medicalRate", 0.02), unemploymentRate = optDouble("unemploymentRate", 0.005), housingRate = optDouble("housingRate", 0.07), taxThreshold = optDouble("taxThreshold", 5000.0), specialDeduction = optDouble("specialDeduction", 0.0), otherPreTaxDeduction = optDouble("otherPreTaxDeduction", 0.0), cumulativeTaxEnabled = optBoolean("cumulativeTaxEnabled", false), manualTax = if (isNull("manualTax")) null else optDouble("manualTax"), defaultAllowance = optDouble("defaultAllowance", 0.0), defaultDeduction = optDouble("defaultDeduction", 0.0))
private fun JSONObject.adjustmentFromJson() = SalaryAdjustment(type = optString("type", ADJUST_ALLOWANCE), amount = optDouble("amount", 0.0), date = optString("date", dayText()), period = optString("period", monthText()), note = optString("note", ""))
private fun JSONObject.holidayFromJson() = HolidayRecord(date = optString("date", dayText()), name = optString("name", ""), type = optString("type", HOLIDAY_LEGAL), multiplier = optDouble("multiplier", 3.0), enabled = optBoolean("enabled", true))
