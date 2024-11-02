package com.apron.home.repository

import com.apron.home.TestcontainersConfiguration
import com.apron.home.domain.Invoice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient

@Import(TestcontainersConfiguration::class)
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ComponentScan
class InvoiceRepositoryTest {

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    private lateinit var invoiceRepository: InvoiceRepository

    @Test
    fun `should retrieve invoice by id`() {
        // arrange
        val invoiceId = 1

        // act
        val result = invoiceRepository.findInvoiceById(invoiceId)

        //assert
        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(1)
    }

    @Test
    fun `should retrieve UNPAID invoice records for given user`() {
        // arrange
        val userId = 1

        // act
        val results = invoiceRepository.findUnpaidInvoicesForUser(userId)

        //assert
        assertThat(results.size).isEqualTo(3)
    }

    @Test
    fun `should retrieve PAID invoice records for given user`() {
        // arrange
        val userId = 1

        // act
        val results = invoiceRepository.findInvoicesForUserByStatus(userId, Invoice.Status.PAID)

        //assert
        assertThat(results.size).isEqualTo(2)
    }

    @Test
    fun `should retrieve ALL invoice records for given user`() {
        // arrange
        val userId = 1

        // act
        val results = invoiceRepository.findAllInvoicesForUser(userId)

        //assert
        assertThat(results.size).isEqualTo(6)
    }

    @Test
    fun `should update invoice to PAID with given idempotency-id and paid-by-account-id`() {
        // arrange
        val invoiceId = 2
        val transferIdempotencyId = "idempotency-id-test-paid"
        val paidByAccountId = 1
        invoiceRepository.saveTransferIdempotencyId(transferIdempotencyId)

        // act
        val paid = invoiceRepository.modifyInvoiceToPaidWithPaidByAccountId(invoiceId, transferIdempotencyId, paidByAccountId)

        //assert
        assertThat(paid).isEqualTo(1)
        val invoice = invoiceRepository.findInvoiceById(invoiceId)
        assertThat(invoice?.status).isEqualTo(Invoice.Status.PAID)
        assertThat(invoice?.transferIdempotencyId).isEqualTo(transferIdempotencyId)
        assertThat(invoice?.paidByAccountId).isEqualTo(paidByAccountId)
    }

    @Test
    fun `should update invoice to DECLINED with given idempotency-id`() {
        // arrange
        val invoiceId = 3
        val transferIdempotencyId = "idempotency-id-test-declined"
        invoiceRepository.saveTransferIdempotencyId(transferIdempotencyId)

        // act
        val declined = invoiceRepository.modifyInvoiceToDeclinedWithIdempotencyId(invoiceId, transferIdempotencyId)

        //assert
        assertThat(declined).isEqualTo(1)
        val invoice = invoiceRepository.findInvoiceById(invoiceId)
        assertThat(invoice?.status).isEqualTo(Invoice.Status.DECLINED)
        assertThat(invoice?.transferIdempotencyId).isEqualTo(transferIdempotencyId)
        assertThat(invoice?.paidByAccountId).isEqualTo(0)
    }
}
