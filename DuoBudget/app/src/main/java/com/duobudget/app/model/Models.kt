package com.duobudget.app.model
import java.time.LocalDateTime
import java.util.UUID
enum class TransactionType { INCOME, EXPENSE, TRANSFER, REFUND, CASHBACK }
enum class Payer { ME, PARTNER }
enum class SyncState { DISABLED, READY, SYNCING, SYNCED, ERROR }
data class MoneyAccount(val id: String, val name: String, val openingBalance: Long = 0L, val archived: Boolean = false)
data class MoneyTransaction(
    val id: String = UUID.randomUUID().toString(), val type: TransactionType,
    val amount: Long, val category: String, val note: String, val accountId: String,
    val payer: Payer, val payerName: String = "", val createdByUid: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(), val updatedAt: LocalDateTime = LocalDateTime.now(),
    val version: Long = 1L, val deleted: Boolean = false, val targetAccountId: String = ""
)
data class Goal(val id: String = UUID.randomUUID().toString(), val name: String, val targetAmount: Long,
    val currentAmount: Long = 0L, val joint: Boolean = true, val createdAt: LocalDateTime = LocalDateTime.now())
data class FamilyProfile(val myName: String = "Я", val partnerName: String = "Партнёр",
    val phone: String = "", val familyId: String = "", val inviteCode: String = "") {
    val linked: Boolean get() = familyId.isNotBlank()
}
data class ChangeLogEntry(val id: Long, val entityId: String, val action: String, val description: String, val changedAt: LocalDateTime)
data class BudgetData(
    val transactions: List<MoneyTransaction> = emptyList(),
    val accounts: List<MoneyAccount> = listOf(MoneyAccount("sber", "Сбербанк"), MoneyAccount("tbank", "Т-Банк"), MoneyAccount("cash", "Наличные")),
    val budget: Long = 0L, val savings: Long = 0L, val categoryLimits: Map<String, Long> = emptyMap(),
    val goals: List<Goal> = emptyList(), val myName: String = "Я", val partnerName: String = "Партнёр"
)
data class BudgetCommand(
    val id: String = UUID.randomUUID().toString(), val kind: String, val actorId: String,
    val entityId: String = "", val value: Long = 0L, val text: String = "", val extra: String = "",
    val tx: MoneyTransaction? = null, val goal: Goal? = null, val account: MoneyAccount? = null
)
data class CloudSession(val familyId: String, val token: String, val actorId: String, val role: Payer,
    val inviteCode: String = "", val expiresAt: String = "")

