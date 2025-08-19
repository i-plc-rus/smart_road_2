package ru.iplc.smart_road.data.model

data class Payment(
    val date: String,  // или LocalDate, если используете java.time
    val amount: Int,   // сумма в рублях
    val paymentMethod: String,
    val notes: String? = null  // опциональное поле для примечаний
)
