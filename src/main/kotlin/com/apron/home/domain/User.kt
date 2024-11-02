package com.apron.home.domain

import java.time.LocalDateTime

data class User(
    val id: Int,
    val name: String,
    val createdAt: LocalDateTime,
    val lastUpdated: LocalDateTime? = null,
)
