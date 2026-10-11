package com.duobudget.app.receipt

import com.duobudget.app.model.Money
import com.duobudget.app.model.TransactionType
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

data class ReceiptDraft(val amount: Long?, val date: LocalDateTime?, val merchant: String,
    val category: String, val type: TransactionType = TransactionType.EXPENSE,
    val receiptId: String = "", val fromQr: Boolean = false)

object ReceiptParser {
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun identity(fn: String, fd: String, fp: String) = "receipt_" + hash("$fn|$fd|$fp")
    fun qr(raw: String): ReceiptDraft {
        require(raw.length <= 4096) { "QR-код слишком большой" }
        val query = raw.substringAfter('?', raw).substringBefore('#')
        val pairs = query.split('&').map {
            require(it.contains('=')) { "Это не QR-код кассового чека" }
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        require(pairs.distinctBy { it.first }.size == pairs.size) { "Повторяющиеся поля QR-кода" }
        val fields = pairs.toMap()
        fun digits(key: String, min: Int, max: Int): String = fields[key].orEmpty().also {
            require(it.matches(Regex("[0-9]{$min,$max}"))) { "В QR-коде нет реквизитов чека" }
        }
        val fn = digits("fn", 16, 16); val fd = digits("i", 1, 10); val fp = digits("fp", 1, 10)
        val amount = Money.parse(fields["s"].orEmpty())
        require(amount != null && amount > 0) { "В чеке некорректная сумма" }
        val time = fields["t"].orEmpty()
        val pattern = when (time.length) { 13 -> "uuuuMMdd'T'HHmm"; 15 -> "uuuuMMdd'T'HHmmss"; else -> error("В чеке некорректная дата") }
        val date = LocalDateTime.parse(time, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT))
        require(date.year >= 1900 && date.toLocalDate() <= LocalDate.now()) { "Дата чека в будущем" }
        val type = when (fields["n"]) { "1" -> TransactionType.EXPENSE; "2" -> TransactionType.REFUND; else -> error("Поддерживаются чеки покупок и возвратов") }
        return ReceiptDraft(amount, date, "", "Другое", type, identity(fn, fd, fp), true)
    }
    fun text(raw: String): ReceiptDraft {
        val text = raw.take(100_000).replace('\u00a0', ' ')
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val upper = text.uppercase(Locale.ROOT)
        val money = Regex("(?<![0-9])[0-9]{1,3}(?:[ ]?[0-9]{3})*[.,][0-9]{2}(?![0-9])")
        // Only a total label is trusted. Never substitute cash tendered, change or a product price.
        val total = Regex("^(?:ИТОГ(?:О)?|СУММА РАСЧ[ЕЁ]ТА|К ОПЛАТЕ|TOTAL)(?:\\s|:|=|$)", RegexOption.IGNORE_CASE)
        val amounts = lines.mapIndexedNotNull { index, line ->
            if (!total.containsMatchIn(line)) null else {
                val same = money.findAll(line).lastOrNull()?.value
                val next = lines.getOrNull(index + 1)?.takeIf { it.matches(Regex("[0-9 .,]+(?:₽|руб[.]?)?", RegexOption.IGNORE_CASE)) }
                Money.parse(same ?: next?.let { money.find(it)?.value }.orEmpty())?.takeIf { it > 0 }
            }
        }.distinct()
        val dateMatch = Regex("\\b([0-3][0-9][./-][01][0-9][./-][0-9]{4})(?:\\s+([0-2][0-9]:[0-5][0-9](?::[0-5][0-9])?))?").find(text)
        val date = dateMatch?.let { m -> runCatching {
            val day = LocalDate.parse(m.groupValues[1].replace('/', '.').replace('-', '.'), DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT))
            require(day <= LocalDate.now() && day.year >= 1900)
            day.atTime(if (m.groupValues[2].isBlank()) java.time.LocalTime.MIDNIGHT else java.time.LocalTime.parse(m.groupValues[2]))
        }.getOrNull() }
        val merchant = lines.take(6).firstOrNull { line ->
            line.count { it.isLetter() } >= 3 && !Regex("ЧЕК|ИНН|КАСС|ФИСК|АДРЕС|ПРИХОД|ОПЕРАТОР", RegexOption.IGNORE_CASE).containsMatchIn(line)
        }.orEmpty().take(120)
        val category = when {
            Regex("АПТЕК|ФАРМ").containsMatchIn(upper) -> "Здоровье"
            Regex("АЗС|ЛУКОЙЛ|ГАЗПРОМНЕФТЬ|РОСНЕФТЬ|БЕНЗИН|ДИЗЕЛ").containsMatchIn(upper) -> "Авто"
            Regex("КАФЕ|РЕСТОРАН|КОФЕЙН").containsMatchIn(upper) -> "Кафе"
            Regex("МАГНИТ|ПЯТ[ЕЁ]РОЧКА|ПЕРЕКР[ЕЁ]СТОК|ЛЕНТА|АШАН|ВКУСВИЛЛ").containsMatchIn(upper) -> "Продукты"
            else -> "Другое"
        }
        fun field(label: String, count: String) = Regex("(?:$label)\\s*[:№#]?\\s*([0-9]{$count})(?![0-9])", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val fn = field("ФН|FN", "16"); val fd = field("ФД|FD", "1,10"); val fp = field("ФПД|ФП|FP", "1,10")
        return ReceiptDraft(amounts.singleOrNull(), date, merchant, category,
            if (upper.contains("ВОЗВРАТ ПРИХОДА")) TransactionType.REFUND else TransactionType.EXPENSE,
            if (fn != null && fd != null && fp != null) identity(fn, fd, fp) else "")
    }
}
