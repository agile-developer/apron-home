package com.apron.home.repository

import com.apron.home.domain.Account
import com.apron.home.domain.Currency
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Repository
class AccountRepository(
    private val jdbcClient: JdbcClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun findAccountById(accountId: Int): Account? {
        val select = """
            SELECT id, user_id, type, balance_in_minor_units, currency, state, locked, created_at, last_updated
            FROM accounts a
            WHERE a.id = ?
        """.trimIndent()
        return jdbcClient.sql(select)
            .params(accountId)
            .query { rs, _ ->
                rs.toAccount()
            }.optional().orElse(null)
    }

    fun findAllAccountsForUser(userId: Int): List<Account> {
        val select = """
            SELECT id, user_id, type, balance_in_minor_units, currency, state, locked, created_at, last_updated
            FROM accounts a
            WHERE a.user_id = ?
        """.trimIndent()
        return jdbcClient.sql(select)
            .params(userId)
            .query { rs, _ ->
                rs.toAccount()
            }.list()
    }

    fun modifyAccountBalanceForUser(accountId: Int, userId: Int, oldBalance: BigDecimal, newBalance: BigDecimal): Int {
        val update = """
            UPDATE accounts
            SET balance_in_minor_units = :newBalance, last_updated = :lastUpdated
            WHERE user_id = :userId
            AND id = :accountId
            AND balance_in_minor_units = :oldBalance
        """.trimIndent()
        return jdbcClient.sql(update)
            .param("newBalance", newBalance.movePointRight(2).toInt(), Types.INTEGER)
            .param("lastUpdated", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .param("userId", userId, Types.INTEGER)
            .param("accountId", accountId, Types.INTEGER)
            .param("oldBalance", oldBalance.movePointRight(2).toInt(), Types.INTEGER)
            .update()
    }

    fun saveTopUpForAccountWithIdempotencyId(accountId: Int, amount: BigDecimal, balanceAtTopUp: BigDecimal, topUpIdempotencyId: String): Int {
        val insert = """
            INSERT INTO top_ups (account_id, amount_in_minor_units, balance_at_top_up_in_minor_units, top_up_idempotency_id, created_at)
            VALUES (:accountId, :amount, :balanceAtTopUp, :topUpIdempotencyId, :createdAt)
        """.trimIndent()
        val keyHolder = GeneratedKeyHolder()
        jdbcClient.sql(insert)
            .param("accountId", accountId, Types.INTEGER)
            .param("amount", amount.movePointRight(2).toInt(), Types.INTEGER)
            .param("balanceAtTopUp", balanceAtTopUp.movePointRight(2).toInt(), Types.INTEGER)
            .param("topUpIdempotencyId", topUpIdempotencyId, Types.VARCHAR)
            .param("createdAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .update(keyHolder)
        return keyHolder.keys?.get("id") as Int
    }

    fun lockAccount(accountId: Int): Int {
        val lockUpdate = """
            UPDATE accounts
            SET locked = 'TRUE', last_updated = :lastUpdated
            WHERE id = :accountId
            AND locked = 'FALSE'
        """.trimIndent()
        val rowsUpdated = jdbcClient.sql(lockUpdate)
            .param("accountId", accountId, Types.INTEGER)
            .param("lastUpdated", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .update()
        if (rowsUpdated != 1) {
            logger.warn("Failed to acquire record lock for account-id: $accountId, it might already be locked")
        }
        return rowsUpdated
    }

    fun unlockAccount(accountId: Int): Int {
        val lockUpdate = """
            UPDATE accounts
            SET locked = 'FALSE', last_updated = :lastUpdated
            WHERE id = :accountId
            AND locked = 'TRUE'
        """.trimIndent()
        val rowsUpdated = jdbcClient.sql(lockUpdate)
            .param("accountId", accountId, Types.INTEGER)
            .param("lastUpdated", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .update()
        if (rowsUpdated != 1) {
            logger.warn("Failed to release record lock for account-id: $accountId, it might already be unlocked")
        }
        return rowsUpdated
    }

    private fun ResultSet.toAccount(): Account {
        return with(this) {
            Account(
                getInt("id"),
                getInt("user_id"),
                Account.Type.valueOf(getString("type")),
                getInt("balance_in_minor_units").toBigDecimal().movePointLeft(2),
                Currency.valueOf(getString("currency")),
                Account.State.valueOf(getString("state")),
                getBoolean("locked"),
                getTimestamp("created_at").toLocalDateTime(),
                getTimestamp("last_updated")?.toLocalDateTime()
            )
        }
    }
}
