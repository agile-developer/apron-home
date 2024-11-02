package com.apron.home.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class Invoice(
    val id: Int,
    val userId: Int,
    val targetPaymentDetails: String,
    val amount: BigDecimal,
    val currency: Currency,
    val status: Status,
    val vendorName: String = "Acme Services Ltd",
    val details: String = "For services rendered",
    val transferIdempotencyId: String? = null,
    val paidByAccountId: Int? = null,
    val createdAt: LocalDateTime,
    val lastUpdated: LocalDateTime? = null,
) {
    enum class Status {
        UNPAID,
        LOCKED_FOR_PAYMENT,
        PAID,
        DECLINED
    }
}
