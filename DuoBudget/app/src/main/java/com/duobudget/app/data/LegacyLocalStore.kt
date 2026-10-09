package com.duobudget.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.duobudget.app.model.ChangeLogEntry
import com.duobudget.app.model.FamilyProfile
import com.duobudget.app.model.Goal
import com.duobudget.app.model.MoneyTransaction
import com.duobudget.app.model.Payer
import com.duobudget.app.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.LocalDateTime
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted local-first storage for DuoBudget v0.3.
 *
 * The complete local state is encrypted with AES/GCM before it reaches disk. The AES key is
 * generated inside Android Keystore and is non-exportable. v0.2 plaintext SQLite and v0.1
 * SharedPreferences are migrated once and removed after a successful encrypted write.
 */
class LegacyLocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val atomicFile = AtomicFile(File(appContext.filesDir, VAULT_FILE))
    private var state: VaultState

    init {
        ensureKey()
        state = synchronized(lock) {
            if (atomicFile.baseFile.exists()) {
                runCatching { readVault() }.getOrElse {
                    // Do not silently destroy a vault if decryption fails.
                    throw IllegalStateException("Не удалось открыть защищённое хранилище DuoBudget", it)
                }
            } else {
                val migrated = migrateLegacyData()
                writeVault(migrated)
                removeLegacyPlaintext()
                migrated
            }
        }
    }

    fun loadTransactions(includeDeleted: Boolean = false): List<MoneyTransaction> = synchronized(lock) {
        state.transactions
            .asSequence()
            .filter { includeDeleted || !it.deleted }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    fun findTransaction(id: String): MoneyTransaction? = synchronized(lock) {
        state.transactions.firstOrNull { it.id == id }
    }

    fun upsertTransaction(tx: MoneyTransaction, action: String = "Сохранено") = mutate { current ->
        val items = current.transactions.toMutableList()
        val index = items.indexOfFirst { it.id == tx.id }
        if (index >= 0) items[index] = tx else items.add(tx)
        current.copy(
            transactions = items,
            changes = appendChange(current.changes, tx.id, action, "${tx.category}: ${tx.amount} ₽")
        )
    }

    fun mergeRemoteTransactions(remote: List<MoneyTransaction>): Int = synchronized(lock) {
        var changed = 0
        val items = state.transactions.toMutableList()
        remote.forEach { incoming ->
            val index = items.indexOfFirst { it.id == incoming.id }
            val local = if (index >= 0) items[index] else null
            val shouldApply = local == null || incoming.version > local.version ||
                (incoming.version == local.version && incoming.updatedAt.isAfter(local.updatedAt))
            if (shouldApply) {
                if (index >= 0) items[index] = incoming else items.add(incoming)
                changed++
            }
        }
        if (changed > 0) {
            state = state.copy(transactions = items)
            writeVault(state)
        }
        changed
    }

    fun claimLegacyTransactions(uid: String) {
        if (uid.isBlank()) return
        mutate { current ->
            val now = LocalDateTime.now()
            current.copy(
                transactions = current.transactions.map { tx ->
                    if (tx.createdByUid.isBlank()) tx.copy(createdByUid = uid, updatedAt = now) else tx
                }
            )
        }
    }

    fun softDeleteTransaction(id: String) {
        val current = findTransaction(id) ?: return
        upsertTransaction(
            current.copy(deleted = true, version = current.version + 1, updatedAt = LocalDateTime.now()),
            action = "Удалено"
        )
    }

    fun restoreTransaction(id: String) {
        val current = findTransaction(id) ?: return
        upsertTransaction(
            current.copy(deleted = false, version = current.version + 1, updatedAt = LocalDateTime.now()),
            action = "Восстановлено"
        )
    }

    fun loadBudget(): Long = synchronized(lock) { state.budget }
    fun saveBudget(value: Long) = mutate { it.copy(budget = value.coerceAtLeast(0L)) }

    fun loadSavings(): Long = synchronized(lock) { state.savings }
    fun saveSavings(value: Long) = mutate { it.copy(savings = value.coerceAtLeast(0L)) }

    fun loadDarkTheme(): Boolean = synchronized(lock) { state.darkTheme }
    fun saveDarkTheme(value: Boolean) = mutate { it.copy(darkTheme = value) }

    fun loadCategoryLimits(): Map<String, Long> = synchronized(lock) { state.categoryLimits.toMap() }

    fun saveCategoryLimit(category: String, amount: Long) = mutate { current ->
        val updated = current.categoryLimits.toMutableMap()
        if (amount <= 0L) updated.remove(category) else updated[category] = amount
        current.copy(categoryLimits = updated)
    }

    fun replaceCategoryLimits(limits: Map<String, Long>) = mutate { current ->
        current.copy(categoryLimits = limits.filterValues { it > 0L })
    }

    fun loadGoals(): List<Goal> = synchronized(lock) { state.goals.sortedByDescending { it.createdAt } }

    fun saveGoal(goal: Goal) = mutate { current ->
        val goals = current.goals.toMutableList()
        val index = goals.indexOfFirst { it.id == goal.id }
        if (index >= 0) goals[index] = goal else goals.add(goal)
        current.copy(goals = goals)
    }

    fun deleteGoal(id: String) = mutate { current ->
        current.copy(goals = current.goals.filterNot { it.id == id })
    }

    fun replaceGoals(goals: List<Goal>) = mutate { current -> current.copy(goals = goals) }

    fun loadFamilyProfile(): FamilyProfile = synchronized(lock) { state.profile }
    fun saveFamilyProfile(profile: FamilyProfile) = mutate { it.copy(profile = profile) }
    fun clearFamilyLink() = mutate { current ->
        current.copy(profile = current.profile.copy(familyId = "", inviteCode = ""))
    }

    fun loadChangeLog(limit: Int = 100): List<ChangeLogEntry> = synchronized(lock) {
        state.changes.sortedByDescending { it.changedAt }.take(limit.coerceAtLeast(0))
    }

    fun close() = Unit

    private fun mutate(transform: (VaultState) -> VaultState) = synchronized(lock) {
        val updated = transform(state)
        writeVault(updated)
        state = updated
    }

    private fun appendChange(
        changes: List<ChangeLogEntry>,
        entityId: String,
        action: String,
        description: String
    ): List<ChangeLogEntry> {
        val nextId = (changes.maxOfOrNull { it.id } ?: 0L) + 1L
        return (changes + ChangeLogEntry(nextId, entityId, action, description, LocalDateTime.now()))
            .takeLast(MAX_CHANGE_LOG)
    }

    private fun ensureKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= 35) {
            builder.setUnlockedDeviceRequired(true)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Ключ защищённого хранилища недоступен")
    }

    private fun writeVault(value: VaultState) {
        val plain = value.toJson().toString().toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv

        val out = atomicFile.startWrite()
        try {
            out.write(MAGIC)
            out.write(iv.size)
            out.write(iv)
            out.write(encrypted)
            atomicFile.finishWrite(out)
        } catch (t: Throwable) {
            atomicFile.failWrite(out)
            throw t
        }
    }

    private fun readVault(): VaultState {
        val bytes = atomicFile.openRead().use { it.readBytes() }
        require(bytes.size > MAGIC.size + 2) { "Повреждён файл хранилища" }
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Неизвестный формат хранилища" }
        val ivSize = bytes[MAGIC.size].toInt() and 0xff
        require(ivSize in 12..32) { "Повреждён IV" }
        val ivStart = MAGIC.size + 1
        val ivEnd = ivStart + ivSize
        require(ivEnd < bytes.size) { "Повреждён файл хранилища" }
        val iv = bytes.copyOfRange(ivStart, ivEnd)
        val encrypted = bytes.copyOfRange(ivEnd, bytes.size)

        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val plain = cipher.doFinal(encrypted)
        return JSONObject(String(plain, StandardCharsets.UTF_8)).toVaultState()
    }

    private fun migrateLegacyData(): VaultState {
        val fromV02 = migrateV02Sqlite()
        if (fromV02 != null) return fromV02
        return migrateV01Preferences()
    }

    private fun migrateV02Sqlite(): VaultState? {
        val dbFile = appContext.getDatabasePath(V02_DB_NAME)
        if (!dbFile.exists()) return null
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        return db.use { database ->
            val transactions = runCatching {
                database.rawQuery("SELECT * FROM transactions", null).use { c ->
                    buildList {
                        while (c.moveToNext()) add(c.toLegacyTransaction())
                    }
                }
            }.getOrDefault(emptyList())

            val settings = mutableMapOf<String, String>()
            runCatching {
                database.rawQuery("SELECT key, value FROM settings", null).use { c ->
                    while (c.moveToNext()) settings[c.getString(0)] = c.getString(1)
                }
            }

            val limits = mutableMapOf<String, Long>()
            runCatching {
                database.rawQuery("SELECT category, amount FROM category_limits", null).use { c ->
                    while (c.moveToNext()) limits[c.getString(0)] = c.getLong(1)
                }
            }

            val goals = runCatching {
                database.rawQuery("SELECT * FROM goals", null).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                Goal(
                                    id = c.getString(c.getColumnIndexOrThrow("id")),
                                    name = c.getString(c.getColumnIndexOrThrow("name")),
                                    targetAmount = c.getLong(c.getColumnIndexOrThrow("target_amount")),
                                    currentAmount = c.getLong(c.getColumnIndexOrThrow("current_amount")),
                                    joint = c.getInt(c.getColumnIndexOrThrow("joint")) == 1,
                                    createdAt = parseDate(c.getString(c.getColumnIndexOrThrow("created_at")))
                                )
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())

            val changes = runCatching {
                database.rawQuery("SELECT * FROM change_log", null).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                ChangeLogEntry(
                                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                                    entityId = c.getString(c.getColumnIndexOrThrow("entity_id")),
                                    action = c.getString(c.getColumnIndexOrThrow("action")),
                                    description = c.getString(c.getColumnIndexOrThrow("description")),
                                    changedAt = parseDate(c.getString(c.getColumnIndexOrThrow("changed_at")))
                                )
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())

            VaultState(
                transactions = transactions,
                budget = settings["budget"]?.toLongOrNull() ?: 0L,
                savings = settings["savings"]?.toLongOrNull() ?: 0L,
                darkTheme = settings["dark_theme"]?.toBooleanStrictOrNull() ?: false,
                categoryLimits = limits,
                goals = goals,
                profile = FamilyProfile(
                    myName = settings["my_name"] ?: "Я",
                    partnerName = settings["partner_name"] ?: "Партнёр",
                    phone = settings["phone"] ?: "",
                    familyId = settings["family_id"] ?: "",
                    inviteCode = settings["invite_code"] ?: ""
                ),
                changes = changes
            )
        }
    }

    private fun Cursor.toLegacyTransaction(): MoneyTransaction = MoneyTransaction(
        id = getString(getColumnIndexOrThrow("id")),
        type = enumOrDefault(getString(getColumnIndexOrThrow("type")), TransactionType.EXPENSE),
        amount = getLong(getColumnIndexOrThrow("amount")).coerceAtLeast(0L),
        category = getString(getColumnIndexOrThrow("category")),
        note = getString(getColumnIndexOrThrow("note")),
        accountId = getString(getColumnIndexOrThrow("account_id")),
        payer = enumOrDefault(getString(getColumnIndexOrThrow("payer")), Payer.ME),
        payerName = getString(getColumnIndexOrThrow("payer_name")),
        createdByUid = getString(getColumnIndexOrThrow("created_by_uid")),
        createdAt = parseDate(getString(getColumnIndexOrThrow("created_at"))),
        updatedAt = parseDate(getString(getColumnIndexOrThrow("updated_at"))),
        version = getLong(getColumnIndexOrThrow("version")).coerceAtLeast(1L),
        deleted = getInt(getColumnIndexOrThrow("deleted")) == 1
    )

    private fun migrateV01Preferences(): VaultState {
        val prefs = appContext.getSharedPreferences("duo_budget", Context.MODE_PRIVATE)
        val transactions = mutableListOf<MoneyTransaction>()
        val raw = prefs.getString("transactions", null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val createdAt = parseDate(obj.optString("createdAt"))
                    transactions += MoneyTransaction(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = enumOrDefault(obj.optString("type"), TransactionType.EXPENSE),
                        amount = obj.optLong("amount").coerceAtLeast(0L),
                        category = obj.optString("category", "Другое"),
                        note = obj.optString("note"),
                        accountId = obj.optString("accountId", "cash"),
                        payer = enumOrDefault(obj.optString("payer"), Payer.ME),
                        payerName = if (obj.optString("payer") == Payer.PARTNER.name) "Партнёр" else "Я",
                        createdAt = createdAt,
                        updatedAt = createdAt
                    )
                }
            }
        }
        return VaultState(
            transactions = transactions,
            budget = prefs.getLong("budget", 0L),
            savings = prefs.getLong("savings", 0L),
            darkTheme = prefs.getBoolean("dark_theme", false),
            changes = transactions.mapIndexed { index, tx ->
                ChangeLogEntry((index + 1).toLong(), tx.id, "Импортировано из v0.1", "${tx.category}: ${tx.amount} ₽", LocalDateTime.now())
            }
        )
    }

    private fun removeLegacyPlaintext() {
        val dbFile = appContext.getDatabasePath(V02_DB_NAME)
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"), File(dbFile.path + "-journal"))
            .forEach { runCatching { if (it.exists()) it.delete() } }
        runCatching { appContext.getSharedPreferences("duo_budget", Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private fun VaultState.toJson() = JSONObject().apply {
        put("schema", VAULT_SCHEMA)
        put("budget", budget)
        put("savings", savings)
        put("darkTheme", darkTheme)
        put("transactions", JSONArray().apply { transactions.forEach { put(it.toJson()) } })
        put("categoryLimits", JSONObject().apply { categoryLimits.forEach { (key, value) -> put(key, value) } })
        put("goals", JSONArray().apply { goals.forEach { put(it.toJson()) } })
        put("profile", profile.toJson())
        put("changes", JSONArray().apply { changes.forEach { put(it.toJson()) } })
    }

    private fun JSONObject.toVaultState(): VaultState {
        val transactionsArray = optJSONArray("transactions") ?: JSONArray()
        val goalsArray = optJSONArray("goals") ?: JSONArray()
        val changesArray = optJSONArray("changes") ?: JSONArray()
        val limitsObj = optJSONObject("categoryLimits") ?: JSONObject()
        val limits = buildMap {
            val keys = limitsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = limitsObj.optLong(key, 0L)
                if (value > 0L) put(key, value)
            }
        }
        return VaultState(
            transactions = buildList { for (i in 0 until transactionsArray.length()) add(transactionsArray.getJSONObject(i).toTransaction()) },
            budget = optLong("budget", 0L).coerceAtLeast(0L),
            savings = optLong("savings", 0L).coerceAtLeast(0L),
            darkTheme = optBoolean("darkTheme", false),
            categoryLimits = limits,
            goals = buildList { for (i in 0 until goalsArray.length()) add(goalsArray.getJSONObject(i).toGoal()) },
            profile = (optJSONObject("profile") ?: JSONObject()).toProfile(),
            changes = buildList { for (i in 0 until changesArray.length()) add(changesArray.getJSONObject(i).toChangeLog()) }
        )
    }

    private fun MoneyTransaction.toJson() = JSONObject().apply {
        put("id", id); put("type", type.name); put("amount", amount); put("category", category)
        put("note", note); put("accountId", accountId); put("payer", payer.name); put("payerName", payerName)
        put("createdByUid", createdByUid); put("createdAt", createdAt.toString()); put("updatedAt", updatedAt.toString())
        put("version", version); put("deleted", deleted)
    }

    private fun JSONObject.toTransaction() = MoneyTransaction(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        type = enumOrDefault(optString("type"), TransactionType.EXPENSE),
        amount = optLong("amount", 0L).coerceAtLeast(0L),
        category = optString("category", "Другое"), note = optString("note"),
        accountId = optString("accountId", "cash"), payer = enumOrDefault(optString("payer"), Payer.ME),
        payerName = optString("payerName"), createdByUid = optString("createdByUid"),
        createdAt = parseDate(optString("createdAt")), updatedAt = parseDate(optString("updatedAt")),
        version = optLong("version", 1L).coerceAtLeast(1L), deleted = optBoolean("deleted", false)
    )

    private fun Goal.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("targetAmount", targetAmount); put("currentAmount", currentAmount)
        put("joint", joint); put("createdAt", createdAt.toString())
    }

    private fun JSONObject.toGoal() = Goal(
        id = optString("id").ifBlank { UUID.randomUUID().toString() }, name = optString("name", "Цель"),
        targetAmount = optLong("targetAmount", 0L).coerceAtLeast(0L),
        currentAmount = optLong("currentAmount", 0L).coerceAtLeast(0L), joint = optBoolean("joint", true),
        createdAt = parseDate(optString("createdAt"))
    )

    private fun FamilyProfile.toJson() = JSONObject().apply {
        put("myName", myName); put("partnerName", partnerName); put("phone", phone)
        put("familyId", familyId); put("inviteCode", inviteCode)
    }

    private fun JSONObject.toProfile() = FamilyProfile(
        myName = optString("myName", "Я").ifBlank { "Я" }, partnerName = optString("partnerName", "Партнёр").ifBlank { "Партнёр" },
        phone = optString("phone"), familyId = optString("familyId"), inviteCode = optString("inviteCode")
    )

    private fun ChangeLogEntry.toJson() = JSONObject().apply {
        put("id", id); put("entityId", entityId); put("action", action); put("description", description); put("changedAt", changedAt.toString())
    }

    private fun JSONObject.toChangeLog() = ChangeLogEntry(
        id = optLong("id", 0L), entityId = optString("entityId"), action = optString("action"),
        description = optString("description"), changedAt = parseDate(optString("changedAt"))
    )

    private fun parseDate(raw: String?): LocalDateTime = runCatching { LocalDateTime.parse(raw) }.getOrDefault(LocalDateTime.now())

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private data class VaultState(
        val transactions: List<MoneyTransaction> = emptyList(),
        val budget: Long = 0L,
        val savings: Long = 0L,
        val darkTheme: Boolean = false,
        val categoryLimits: Map<String, Long> = emptyMap(),
        val goals: List<Goal> = emptyList(),
        val profile: FamilyProfile = FamilyProfile(),
        val changes: List<ChangeLogEntry> = emptyList()
    )

    companion object {
        private const val VAULT_FILE = "duobudget_v03.vault"
        private const val VAULT_SCHEMA = 3
        private const val V02_DB_NAME = "duo_budget_v02.db"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "duobudget-vault-aes-v1"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val MAX_CHANGE_LOG = 500
        private val MAGIC = byteArrayOf(0x44, 0x42, 0x56, 0x33) // DBV3
        private val AAD = "DuoBudget:v3:local-vault".toByteArray(StandardCharsets.UTF_8)
    }
}
