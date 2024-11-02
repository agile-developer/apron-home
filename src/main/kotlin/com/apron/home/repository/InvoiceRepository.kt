package com.apron.home.repository

import com.apron.home.domain.Currency
import com.apron.home.domain.Invoice
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Repository
class InvoiceRepository(
    private val jdbcClient: JdbcClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun findInvoiceById(invoiceId: Int): Invoice? {
        val select = """
            SELECT id, user_id, target_payment_details, amount_in_minor_units, currency, status, vendor_name, details, transfer_idempotency_id, paid_by_account_id, created_at, last_updated
            FROM invoices i
            WHERE i.id = ?
        """.trimIndent()
        return jdbcClient.sql(select)
            .params(invoiceId)
            .query { rs, _ ->
                rs.toInvoice()
            }.optional().orElse(null)
    }

    fun findInvoicesForUserByStatus(userId: Int, status: Invoice.Status): List<Invoice> {
        val select = """
            SELECT id, user_id, target_payment_details, amount_in_minor_units, currency, status, vendor_name, details, transfer_idempotency_id, paid_by_account_id, created_at, last_updated
            FROM invoices i
            WHERE i.user_id = ?
            AND i.status = ?
        """.trimIndent()
        return jdbcClient.sql(select)
            .params(userId, status.name)
            .query { rs, _ ->
                rs.toInvoice()
            }.list()
    }

    fun findUnpaidInvoicesForUser(userId: Int): List<Invoice> {
        return findInvoicesForUserByStatus(userId, Invoice.Status.UNPAID)
    }

    fun findAllInvoicesForUser(userId: Int): List<Invoice> {
        val select = """
            SELECT id, user_id, target_payment_details, amount_in_minor_units, currency, status, vendor_name, details, transfer_idempotency_id, paid_by_account_id, created_at, last_updated
            FROM invoices i
            WHERE i.user_id = ?
        """.trimIndent()
        return jdbcClient.sql(select)
            .params(userId)
            .query { rs, _ ->
                rs.toInvoice()
            }.list()
    }

    fun saveTransferIdempotencyId(transferIdempotencyId: String): Int {
        val insert = """
            INSERT INTO transfer_idempotency_ids (id)
            VALUES (:id)
        """.trimIndent()
        return jdbcClient.sql(insert)
            .param("id", transferIdempotencyId, Types.VARCHAR)
            .update()
    }

    fun modifyInvoiceToPaidWithPaidByAccountId(invoiceId: Int, transferIdempotencyId: String, paidByAccountId: Int): Int {
        val update = """
            UPDATE invoices
            SET status = :paid, paid_by_account_id = :paidByAccountId, transfer_idempotency_id = :transferIdempotencyId, last_updated = :lastUpdated
            WHERE id = :invoiceId
            AND status = :unpaid
            AND transfer_idempotency_id is null
        """.trimIndent()
        return jdbcClient.sql(update)
            .param("paid", Invoice.Status.PAID.name, Types.VARCHAR)
            .param("paidByAccountId", paidByAccountId, Types.INTEGER)
            .param("transferIdempotencyId", transferIdempotencyId, Types.VARCHAR)
            .param("lastUpdated", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .param("invoiceId", invoiceId, Types.INTEGER)
            .param("unpaid", Invoice.Status.UNPAID.name, Types.VARCHAR)
            .update()
    }

    fun modifyInvoiceToDeclinedWithIdempotencyId(invoiceId: Int, transferIdempotencyId: String): Int {
        val update = """
            UPDATE invoices
            SET status = :declined, transfer_idempotency_id = :transferIdempotencyId, last_updated = :lastUpdated
            WHERE id = :invoiceId
            AND status = :unpaid
            AND transfer_idempotency_id is null
        """.trimIndent()
        return jdbcClient.sql(update)
            .param("declined", Invoice.Status.DECLINED.name, Types.VARCHAR)
            .param("transferIdempotencyId", transferIdempotencyId, Types.VARCHAR)
            .param("lastUpdated", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), Types.TIMESTAMP)
            .param("invoiceId", invoiceId, Types.INTEGER)
            .param("unpaid", Invoice.Status.UNPAID.name, Types.VARCHAR)
            .update()
    }

    private fun ResultSet.toInvoice(): Invoice {
        return with(this) {
            Invoice(
                getInt("id"),
                getInt("user_id"),
                getString("target_payment_details"),
                getInt("amount_in_minor_units").toBigDecimal().movePointLeft(2),
                Currency.valueOf(getString("currency")),
                Invoice.Status.valueOf(getString("status")),
                getString("vendor_name"),
                getString("details"),
                getString("transfer_idempotency_id"),
                getInt("paid_by_account_id"),
                getTimestamp("created_at").toLocalDateTime(),
                getTimestamp("last_updated")?.toLocalDateTime()
            )
        }
    }
}
