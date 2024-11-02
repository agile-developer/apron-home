package com.apron.home.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class Account(
    val id: Int,
    val userId: Int,
    val type: Type,
    val balance: BigDecimal,
    val currency: Currency,
    val state: State = State.ACTIVE,
    val locked: Boolean = false,
    val createdAt: LocalDateTime,
    val lastUpdated: LocalDateTime? = null,
) {
    enum class State {
        ACTIVE,
        BLOCKED,
        CLOSED,
    }

    enum class Type {
        CURRENT,
        SAVINGS,
    }
}
