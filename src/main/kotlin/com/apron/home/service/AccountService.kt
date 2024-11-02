package com.apron.home.service

import com.apron.home.domain.Account
import com.apron.home.domain.Currency
import com.apron.home.repository.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AccountService(
    private val accountRepository: AccountRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun fetchAllAccountsForUser(userId: Int): List<Account> {
        return accountRepository.findAllAccountsForUser(userId)
    }

    fun topUpAccountForUser(accountId: Int, userId: Int, amount: BigDecimal, currency: Currency, topUpIdempotencyId: String): TopUpResult {
        if (amount <= BigDecimal.ZERO) {
            logger.error("Top-up amount is zero or negative")
            return TopUpResult.Failure("Top-up amount is zero or negative")
        }
        val account = accountRepository.findAccountById(accountId)
            ?: return TopUpResult.Failure("Account with id: $accountId does not exist")
        if (account.userId != userId) {
            return TopUpResult.Failure("Account with id: $accountId does not belong to user: $userId")
        }
        if (account.currency != currency) {
            return TopUpResult.Failure("Account with id: $accountId is currency ${account.currency}, not $currency")
        }

        // Critical section
        val rowsLocked = accountRepository.lockAccount(accountId)
        if (rowsLocked != 1) {
            return TopUpResult.Failure("Account with id: $accountId could not be locked for top-up, it might be locked by another transaction")
        }
        val accountAfterLock = accountRepository.findAccountById(accountId)
        val currentBalance = accountAfterLock!!.balance
        val newBalance = currentBalance + amount
        return runCatching {
            accountRepository.saveTopUpForAccountWithIdempotencyId(accountId, amount, currentBalance, topUpIdempotencyId)
            accountRepository.modifyAccountBalanceForUser(accountId, userId, currentBalance, newBalance)
            accountRepository.unlockAccount(accountId)
        }.fold(onSuccess = {
            TopUpResult.Success(newBalance)
        }, onFailure = {
            logger.error("Failed with exception: ${it.message}")
            accountRepository.unlockAccount(accountId)
            TopUpResult.Failure("Failed with exception: ${it.message}")
        })
    }

    sealed interface TopUpResult {
        data class Success(val newBalance: BigDecimal) : TopUpResult
        data class Failure(val message: String) : TopUpResult
    }
}
