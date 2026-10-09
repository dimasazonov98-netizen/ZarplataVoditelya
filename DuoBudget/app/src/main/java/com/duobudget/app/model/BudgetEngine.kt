package com.duobudget.app.model
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

object Money {
    const val MAX = 100_000_000_000L
    fun parse(input: String): Long? = runCatching {
        val n = input.trim().replace(" ", "").replace("\u00a0", "").replace(',', '.')
        require(n.matches(Regex("-?[0-9]{1,10}(\\.[0-9]{1,2})?")))
        BigDecimal(n).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact().also { require(it in -MAX..MAX) }
    }.getOrNull()
    fun edit(value: Long): String = BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString().replace('.', ',')
}
object BudgetEngine {
    private fun amount(n: Long, zero: Boolean = false) { require(n in (if (zero) 0L else 1L)..Money.MAX) { "Недопустимая сумма" } }
    private fun text(s: String, max: Int, empty: Boolean = false) { require((empty || s.isNotBlank()) && s.length <= max) { "Проверьте длину текста" } }
    private fun sum(a: Long, b: Long): Long = Math.addExact(a, b).also { amount(it, true) }
    fun apply(s: BudgetData, c: BudgetCommand): BudgetData {
        text(c.id, 80); text(c.actorId, 80)
        return when (c.kind) {
            "TX_PUT" -> {
                val t = requireNotNull(c.tx)
                text(t.id, 80); amount(t.amount); text(t.category, 60); text(t.note, 500, true); text(t.payerName, 60, true)
                require(t.createdByUid == c.actorId) { "Операция принадлежит другому участнику" }
                val old = s.transactions.firstOrNull { it.id == t.id }
                require(old == null || old.createdByUid == c.actorId) { "Можно менять только свои операции" }
                require(old == null || !old.deleted) { "Сначала восстановите операцию" }
                require(s.accounts.any { it.id == t.accountId && (!it.archived || old?.accountId == it.id) }) { "Счёт недоступен" }
                if (t.type == TransactionType.TRANSFER)
                    require(t.targetAccountId != t.accountId && s.accounts.any { it.id == t.targetAccountId && (!it.archived || old?.targetAccountId == it.id) }) { "Выберите другой счёт для перевода" }
                require(t.createdAt.toLocalDate() <= LocalDate.now().plusDays(1)) { "Дата операции в будущем" }
                require(s.transactions.size < 20_000 || old != null) { "Достигнут предел операций" }
                val n = t.copy(version = (old?.version ?: 0L) + 1L, deleted = false)
                s.copy(transactions = s.transactions.filterNot { it.id == t.id } + n)
            }
            "TX_DELETE", "TX_RESTORE" -> {
                val t = s.transactions.firstOrNull { it.id == c.entityId } ?: error("Операция не найдена")
                require(t.createdByUid == c.actorId) { "Можно менять только свои операции" }
                s.copy(transactions = s.transactions.map { if (it.id == t.id) it.copy(deleted = c.kind == "TX_DELETE", version = it.version + 1, updatedAt = LocalDateTime.now()) else it })
            }
            "BUDGET_SET" -> { amount(c.value, true); s.copy(budget = c.value) }
            "LIMIT_SET" -> {
                text(c.text, 60); require(c.text !in listOf("__proto__", "prototype", "constructor")); amount(c.value, true)
                s.copy(categoryLimits = if (c.value == 0L) s.categoryLimits - c.text else s.categoryLimits + (c.text to c.value))
            }
            "SAVINGS_ADD" -> { amount(c.value); s.copy(savings = sum(s.savings, c.value)) }
            "SAVINGS_WITHDRAW" -> { amount(c.value); require(c.value <= s.savings) { "В накоплениях недостаточно денег" }; s.copy(savings = s.savings - c.value) }
            "GOAL_CREATE" -> {
                val g = requireNotNull(c.goal); text(g.id, 80); text(g.name, 60); amount(g.targetAmount)
                require(g.currentAmount == 0L && s.goals.none { it.id == g.id } && s.goals.size < 100) { "Нельзя создать такую цель" }
                s.copy(goals = s.goals + g)
            }
            "GOAL_FUND" -> {
                val g = s.goals.firstOrNull { it.id == c.entityId } ?: error("Цель не найдена")
                amount(c.value)
                val transfer = minOf(c.value, s.savings, g.targetAmount - g.currentAmount)
                require(transfer > 0) { "Цель заполнена или нет накоплений" }
                s.copy(savings = s.savings - transfer, goals = s.goals.map { if (it.id == g.id) it.copy(currentAmount = it.currentAmount + transfer) else it })
            }
            "GOAL_WITHDRAW" -> {
                val g = s.goals.firstOrNull { it.id == c.entityId } ?: error("Цель не найдена")
                amount(c.value); require(c.value <= g.currentAmount) { "В цели недостаточно денег" }
                s.copy(savings = sum(s.savings, c.value), goals = s.goals.map { if (it.id == g.id) it.copy(currentAmount = it.currentAmount - c.value) else it })
            }
            "GOAL_DELETE" -> {
                val g = s.goals.firstOrNull { it.id == c.entityId } ?: error("Цель не найдена")
                s.copy(savings = sum(s.savings, g.currentAmount), goals = s.goals.filterNot { it.id == g.id })
            }
            "ACCOUNT_PUT" -> {
                val a = requireNotNull(c.account); text(a.id, 80); text(a.name, 60)
                require(a.openingBalance in -Money.MAX..Money.MAX && (s.accounts.size < 100 || s.accounts.any { it.id == a.id })) { "Недопустимый счёт" }
                s.copy(accounts = s.accounts.filterNot { it.id == a.id } + a).also { require(it.accounts.any { a -> !a.archived }) { "Нужен активный счёт" } }
            }
            "ACCOUNT_ARCHIVE" -> {
                require(s.accounts.any { it.id == c.entityId }) { "Счёт не найден" }
                require(c.value != 1L || s.accounts.count { !it.archived } > 1) { "Нужен хотя бы один активный счёт" }
                s.copy(accounts = s.accounts.map { if (it.id == c.entityId) it.copy(archived = c.value == 1L) else it })
            }
            "PROFILE_NAMES" -> { text(c.text, 60); text(c.extra, 60); s.copy(myName = c.text, partnerName = c.extra) }
            else -> error("Неизвестное изменение")
        }
    }
    fun balance(s: BudgetData, id: String): Long {
        var total = s.accounts.firstOrNull { it.id == id }?.openingBalance ?: 0L
        s.transactions.filterNot { it.deleted }.forEach { t ->
            if (t.accountId == id) total = Math.addExact(total, if (t.type == TransactionType.EXPENSE || t.type == TransactionType.TRANSFER) -t.amount else t.amount)
            if (t.type == TransactionType.TRANSFER && t.targetAccountId == id) total = Math.addExact(total, t.amount)
        }
        return total
    }
    fun netExpenses(s: BudgetData, month: YearMonth, category: String? = null): Long = s.transactions
        .filter { !it.deleted && YearMonth.from(it.createdAt) == month && (category == null || it.category == category) }
        .sumOf { when (it.type) { TransactionType.EXPENSE -> it.amount; TransactionType.REFUND -> -it.amount; else -> 0L } }
    fun incomes(s: BudgetData, month: YearMonth): Long = s.transactions
        .filter { !it.deleted && YearMonth.from(it.createdAt) == month && (it.type == TransactionType.INCOME || it.type == TransactionType.CASHBACK) }.sumOf { it.amount }
    fun savingsTotal(s: BudgetData): Long = s.savings + s.goals.sumOf { it.currentAmount }
    fun available(s: BudgetData): Long = s.accounts.sumOf { balance(s, it.id) } - savingsTotal(s)
}

