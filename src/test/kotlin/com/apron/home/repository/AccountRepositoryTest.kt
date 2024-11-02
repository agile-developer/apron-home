package com.apron.home.repository

import com.apron.home.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.sql.Types
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Import(TestcontainersConfiguration::class)
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ComponentScan
class AccountRepositoryTest {

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Test
    fun `should retrieve account by id`() {
        // arrange
        val accountId = 1

        // act
        val result = accountRepository.findAccountById(accountId)

        // assert
        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(1)
    }

    @Test
    fun `should retrieve all accounts for given user`() {
        // arrange
        val userId = 1

        // act
        val result = accountRepository.findAllAccountsForUser(userId)

        // assert
        assertThat(result.size).isEqualTo(2)
    }

    @Test
    fun `should update account balance for given user`() {
        // arrange
        val userId = 1
        val account = accountRepository.findAllAccountsForUser(userId)[0]
        val oldBalance = account.balance
        val newBalance = oldBalance.plus(BigDecimal.TEN)

        // act
        val result = accountRepository.modifyAccountBalanceForUser(account.id, userId, oldBalance, newBalance)

        // assert
        assertThat(result).isEqualTo(1)
        val updatedAccount = accountRepository.findAccountById(account.id)
        assertThat(updatedAccount?.balance).isEqualTo(newBalance)
    }

    @Test
    fun `should insert record in top_up table with idempotency-id`() {
        // arrange
        val accountId = 1
        val amount = BigDecimal("50.00")
        val balanceAtTopUp = BigDecimal("100.00")
        val idempotencyId = "top-up-idempotency-id"

        // act
        val result = accountRepository.saveTopUpForAccountWithIdempotencyId(accountId, amount, balanceAtTopUp, idempotencyId)

        // assert
        assertThat(result).isPositive()
    }

    @Test
    fun `should mark given account as locked when it is not already locked`() {
        // arrange
        val accountId = 3
        val insertNewUnlockedAccount = """
            INSERT INTO accounts (id, user_id, type, balance_in_minor_units, currency, state, locked, created_at)
            VALUES (:accountId, 1, 'CURRENT', 50000, 'GBP', 'ACTIVE', 'FALSE' ,:createdAt);
        """.trimIndent()
        jdbcClient.sql(insertNewUnlockedAccount)
            .param("accountId", accountId, Types.INTEGER)
            .param("createdAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .update()
        val account = accountRepository.findAccountById(accountId)
        val lockedStateBeforeUpdate = account?.locked

        // act
        val result = accountRepository.lockAccount(accountId)

        // assert
        assertThat(lockedStateBeforeUpdate).isFalse()
        assertThat(result).isEqualTo(1)
        val accountAfterUpdate = accountRepository.findAccountById(accountId)
        assertThat(accountAfterUpdate?.locked).isTrue()
    }

    @Test
    fun `should not mark given account as locked when it is already locked`() {
        // arrange
        val accountId = 4
        val insertNewLockedAccount = """
            INSERT INTO accounts (id, user_id, type, balance_in_minor_units, currency, state, locked, created_at)
            VALUES (:accountId, 1, 'CURRENT', 50000, 'GBP', 'ACTIVE', 'TRUE' ,:createdAt);
        """.trimIndent()
        jdbcClient.sql(insertNewLockedAccount)
            .param("accountId", accountId, Types.INTEGER)
            .param("createdAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .update()
        val account = accountRepository.findAccountById(accountId)
        val lockedStateBeforeUpdate = account?.locked

        // act
        val result = accountRepository.lockAccount(accountId)

        // assert
        assertThat(lockedStateBeforeUpdate).isTrue()
        assertThat(result).isEqualTo(0)
        val accountAfterUpdate = accountRepository.findAccountById(accountId)
        assertThat(accountAfterUpdate?.locked).isTrue()
    }

    @Test
    fun `should mark given account as unlocked when it is already locked`() {
        // arrange
        val accountId = 5
        val insertNewLockedAccount = """
            INSERT INTO accounts (id, user_id, type, balance_in_minor_units, currency, state, locked, created_at)
            VALUES (:accountId, 1, 'CURRENT', 50000, 'GBP', 'ACTIVE', 'TRUE' ,:createdAt);
        """.trimIndent()
        jdbcClient.sql(insertNewLockedAccount)
            .param("accountId", accountId, Types.INTEGER)
            .param("createdAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .update()
        val account = accountRepository.findAccountById(accountId)
        val lockedStateBeforeUpdate = account?.locked

        // act
        val result = accountRepository.unlockAccount(accountId)

        // assert
        assertThat(lockedStateBeforeUpdate).isTrue()
        assertThat(result).isEqualTo(1)
        val accountAfterUpdate = accountRepository.findAccountById(accountId)
        assertThat(accountAfterUpdate?.locked).isFalse()
    }

    @Test
    fun `should not mark given account as unlocked when it is already unlocked`() {
        // arrange
        val accountId = 6
        val insertNewLockedAccount = """
            INSERT INTO accounts (id, user_id, type, balance_in_minor_units, currency, state, locked, created_at)
            VALUES (:accountId, 1, 'CURRENT', 50000, 'GBP', 'ACTIVE', 'FALSE' ,:createdAt);
        """.trimIndent()
        jdbcClient.sql(insertNewLockedAccount)
            .param("accountId", accountId, Types.INTEGER)
            .param("createdAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .update()
        val account = accountRepository.findAccountById(accountId)
        val lockedStateBeforeUpdate = account?.locked

        // act
        val result = accountRepository.unlockAccount(accountId)

        // assert
        assertThat(lockedStateBeforeUpdate).isFalse()
        assertThat(result).isEqualTo(0)
        val accountAfterUpdate = accountRepository.findAccountById(accountId)
        assertThat(accountAfterUpdate?.locked).isFalse()
    }
}
