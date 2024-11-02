package com.apron.home.service

import com.apron.home.domain.Currency
import com.apron.home.domain.Invoice
import com.apron.home.repository.InvoiceRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal
import java.time.LocalDateTime

class InvoiceServiceTest {

    private val invoiceRepository = mockk<InvoiceRepository>()
    private val invoiceService = InvoiceService(invoiceRepository)

    @ParameterizedTest
    @EnumSource(Invoice.Status::class)
    fun `should return invoices by status`(status: Invoice.Status) {
        // arrange
        val userId = 1
        val invoice1 = Invoice(
            id = 1,
            userId = userId,
            targetPaymentDetails = "77777777",
            amount = BigDecimal("500.00"),
            currency = Currency.GBP,
            status = status,
            createdAt = LocalDateTime.now())
        every { invoiceRepository.findInvoicesForUserByStatus(userId, status) } returns listOf(invoice1)

        // act
        val result = invoiceService.fetchInvoicesForUserByStatus(userId, status)

        // assert
        assertThat(result.size).isEqualTo(1)
        assertThat(result[0].status).isEqualTo(status)
    }

    @Test
    fun `should return all invoices for user`() {
        // arrange
        val userId = 1
        val invoice1 = Invoice(
            id = 1,
            userId = userId,
            targetPaymentDetails = "77777777",
            amount = BigDecimal("500.00"),
            currency = Currency.GBP,
            status = Invoice.Status.UNPAID,
            createdAt = LocalDateTime.now())
        val invoice2 = Invoice(
            id = 2,
            userId = userId,
            targetPaymentDetails = "77777777",
            amount = BigDecimal("500.00"),
            currency = Currency.GBP,
            status = Invoice.Status.PAID,
            createdAt = LocalDateTime.now())
        val invoice3 = Invoice(
            id = 3,
            userId = userId,
            targetPaymentDetails = "77777777",
            amount = BigDecimal("500.00"),
            currency = Currency.GBP,
            status = Invoice.Status.DECLINED,
            createdAt = LocalDateTime.now())
        every { invoiceRepository.findAllInvoicesForUser(userId) } returns listOf(invoice1, invoice2, invoice3)

        // act
        val result = invoiceService.fetchInvoicesForUserByStatus(userId, null)

        // assert
        assertThat(result.size).isEqualTo(3)
        assertThat(result[0].status).isEqualTo(Invoice.Status.UNPAID)
        assertThat(result[1].status).isEqualTo(Invoice.Status.PAID)
        assertThat(result[2].status).isEqualTo(Invoice.Status.DECLINED)
    }
}
