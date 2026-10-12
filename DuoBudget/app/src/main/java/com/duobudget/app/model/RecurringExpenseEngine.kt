package com.duobudget.app.model

import java.time.LocalDate
import java.time.YearMonth

object RecurringExpenseEngine {
    fun transactionId(rule: RecurringExpense, month: YearMonth) = "fixed_${rule.id}_$month"
    fun dueDate(rule:RecurringExpense,month:YearMonth)=month.atDay(rule.dayOfMonth.coerceAtMost(month.lengthOfMonth()))

    fun validate(rules: List<RecurringExpense>, data: BudgetData) {
        require(rules.size <= 100 && rules.distinctBy { it.id }.size == rules.size) { "Можно добавить не более 100 обязательных расходов" }
        rules.forEach { r ->
            require(r.id.matches(Regex("[A-Za-z0-9_-]{1,60}")))
            require(r.name.isNotBlank() && r.name.length <= 60 && r.category.isNotBlank() && r.category.length <= 60) { "Заполните название и категорию" }
            require(r.amount in 1..Money.MAX) { "Проверьте сумму обязательного расхода" }
            require(r.dayOfMonth in 1..31){"День оплаты должен быть от 1 до 31"}
            val start = YearMonth.parse(r.startMonth)
            require(start.year in 2000..9999) { "Начальный месяц должен быть не ранее 2000 года" }
            if(r.resumeMonth.isNotBlank())require(YearMonth.parse(r.resumeMonth).year in 2000..9999)
            require(data.accounts.any { it.id == r.accountId }) { "Счёт не найден" }
        }
    }

    // Stable IDs and retained tombstones make repeated opens, restores and syncs idempotent.
    fun commands(data: BudgetData, rules: List<RecurringExpense>, actor: String,
                 today: LocalDate = LocalDate.now(), updateCurrentRuleId: String? = null): List<BudgetCommand> {
        validate(rules, data)
        val current = YearMonth.from(today)
        val result = mutableListOf<BudgetCommand>()
        var next = data
        val existing=data.transactions.associateBy{it.id}.toMutableMap()
        rules.filter { it.enabled && data.accounts.any { a -> a.id==it.accountId&&!a.archived } }.forEach { r ->
            var month = YearMonth.parse(r.startMonth)
            if(r.resumeMonth.isNotBlank())month=maxOf(month,YearMonth.parse(r.resumeMonth))
            while (month <= current) {
                val id = transactionId(r, month)
                val old = existing[id]
                val due=dueDate(r,month)
                if ((old == null && due<=today) || (old!=null && month == current && r.id == updateCurrentRuleId && !old.deleted && old.createdByUid == actor)) {
                    val t = MoneyTransaction(id = id, type = TransactionType.EXPENSE, amount = r.amount,
                        category = r.category, note = "Обязательный расход · ${r.name}", accountId = r.accountId,
                        payer = r.payer, payerName = if (r.payer == Payer.ME) next.myName else next.partnerName,
                        createdByUid = actor, createdAt = old?.createdAt?:due.atStartOfDay(), updatedAt = today.atStartOfDay(),
                        version = old?.version ?: 1L)
                    if (old == null || t.copy(version = old.version, updatedAt = old.updatedAt) != old) {
                        val c = BudgetCommand(kind = "TX_PUT", actorId = actor, tx = t)
                        next = BudgetEngine.apply(next, c)
                        existing[id]=next.transactions.last()
                        result.add(c)
                    }
                }
                month = month.plusMonths(1)
            }
        }
        return result
    }
}
