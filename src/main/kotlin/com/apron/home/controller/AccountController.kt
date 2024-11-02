package com.apron.home.controller

import com.apron.home.domain.Account
import com.apron.home.domain.Account.State
import com.apron.home.domain.Account.Type
import com.apron.home.domain.Currency
import com.apron.home.service.AccountService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/apron-home/accounts")
class AccountController(
    private val accountService: AccountService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun fetchAccountsForUser(
        @RequestHeader("X-User-Id") userId: Int
    ): ResponseEntity<List<AccountResponse>> {
        logger.info("Fetching accounts for user-id: $userId")
        val accounts = accountService.fetchAllAccountsForUser(userId)
        return ResponseEntity.ok(accounts.map { it.toAccountResponse() })
    }

    @PostMapping("/{accountId}/top-up")
    fun topUp(
        @PathVariable accountId: Int,
        @RequestBody request: TopUpRequest,
        @RequestHeader("X-User-Id") userId: Int,
        @RequestHeader("X-Idempotency-Id") topUpIdempotencyId: String
    ): ResponseEntity<*> {
        logger.info("Top-up account-id: $accountId for user-id: $userId, with amount: ${request.amount.toPlainString() + request.currency.name}, idempotency-id: $topUpIdempotencyId")
        return when (val result =
            accountService.topUpAccountForUser(accountId, userId, request.amount, request.currency, topUpIdempotencyId)) {
            is AccountService.TopUpResult.Failure -> ResponseEntity.badRequest().body(result.message)
            is AccountService.TopUpResult.Success -> ResponseEntity.ok()
                .body(TopUpResponse(result.newBalance, request.currency))
        }
    }
}

data class TopUpRequest(val amount: BigDecimal, val currency: Currency)
data class TopUpResponse(val newBalance: BigDecimal, val currency: Currency)
data class AccountResponse(
    val id: Int,
    val userId: Int,
    val type: Type,
    val balance: BigDecimal,
    val currency: Currency,
    val state: State = State.ACTIVE,
)

fun Account.toAccountResponse(): AccountResponse {
    return with(this) {
        AccountResponse(id, userId, type, balance, currency, state)
    }
}
