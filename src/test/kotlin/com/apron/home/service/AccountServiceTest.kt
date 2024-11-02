package com.apron.home.service

import com.apron.home.domain.Account
import com.apron.home.domain.Currency
import com.apron.home.repository.AccountRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class AccountServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val accountService = AccountService(accountRepository)

    @Test
    fun `should return 'Success' when top-up succeeds`() {
        // arrange
        val accountId = 1
        val userId = 1
        val amount = BigDecimal("50.00")
        val balance = BigDecimal("100.00")
        val currency = Currency.GBP
        val account = Account(
            id = accountId,
            userId = userId,
            type = Account.Type.CURRENT,
            balance = balance,
            currency = currency,
            createdAt = LocalDateTime.now()
        )
        val idempotencyId = "top-up-idempotency-id"
        every { accountRepository.findAccountById(accountId) } returns account
        every { accountRepository.lockAccount(accountId) } returns 1
        every { accountRepository.unlockAccount(accountId) } returns 1
        every { accountRepository.saveTopUpForAccountWithIdempotencyId(accountId,amount, balance, idempotencyId) } returns 1
        every { accountRepository.modifyAccountBalanceForUser(accountId, userId, balance, balance + amount) } returns 1

        // act
        val result = accountService.topUpAccountForUser(accountId, userId, amount, currency, idempotencyId)

        // assert
        assertThat(result).isInstanceOf(AccountService.TopUpResult.Success::class.java)
        verify { accountRepository.lockAccount(accountId) }
        verify { accountRepository.unlockAccount(accountId) }
    }

    @Test
    fun `should return 'Failure' when top-up amount is negative`() {
        // arrange
        val accountId = 1
        val userId = 1
        val amount = BigDecimal("-50.00")

        // act
        val result = accountService.topUpAccountForUser(accountId, userId, amount, Currency.GBP, "")

        // assert
        assertThat(result).isInstanceOf(AccountService.TopUpResult.Failure::class.java)
        verify(exactly = 0) { accountRepository.modifyAccountBalanceForUser(any(), any(), any(), any()) }
    }

    @Test
    fun `should return 'Failure' when account does not belong to given user`() {
        // arrange
        val accountId = 1
        val userId = 1
        val amount = BigDecimal("50.00")
        val balance = BigDecimal("100.00")
        val currency = Currency.GBP
        val account = Account(
            id = accountId,
            userId = 2,
            type = Account.Type.CURRENT,
            balance = balance,
            currency = currency,
            createdAt = LocalDateTime.now()
        )
        every { accountRepository.findAccountById(accountId) } returns account

        // act
        val result = accountService.topUpAccountForUser(accountId, userId, amount, currency, "")

        // assert
        assertThat(result).isInstanceOf(AccountService.TopUpResult.Failure::class.java)
        assertThat((result as AccountService.TopUpResult.Failure).message).isEqualTo("Account with id: $accountId does not belong to user: $userId")
        verify(exactly = 0) { accountRepository.modifyAccountBalanceForUser(any(), any(), any(), any()) }
    }

    @Test
    fun `should return 'Failure' when account balance cannot be updated`() {
        // arrange
        val accountId = 1
        val userId = 1
        val amount = BigDecimal("50.00")
        val balance = BigDecimal("100.00")
        val currency = Currency.GBP
        val account = Account(
            id = accountId,
            userId = 1,
            type = Account.Type.CURRENT,
            balance = balance,
            currency = currency,
            createdAt = LocalDateTime.now()
        )
        val idempotencyId = "top-up-idempotency-id"
        every { accountRepository.findAccountById(accountId) } returns account
        every { accountRepository.lockAccount(accountId) } returns 1
        every { accountRepository.unlockAccount(accountId) } returns 1
        every { accountRepository.saveTopUpForAccountWithIdempotencyId(accountId,amount, balance, idempotencyId) } returns 1
        every { accountRepository.modifyAccountBalanceForUser(accountId, userId, balance, balance + amount) } throws RuntimeException()

        // act
        val result = accountService.topUpAccountForUser(accountId, userId, amount, currency, idempotencyId)

        // assert
        assertThat(result).isInstanceOf(AccountService.TopUpResult.Failure::class.java)
        verify { accountRepository.lockAccount(accountId) }
        verify { accountRepository.unlockAccount(accountId) }
    }
}
