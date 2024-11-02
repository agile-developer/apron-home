package com.apron.home.service

import com.apron.home.domain.Account
import com.apron.home.domain.Currency
import com.apron.home.domain.Invoice
import com.apron.home.repository.AccountRepository
import com.apron.home.repository.InvoiceRepository
import com.apron.home.thirdparty.RequestTimeoutException
import com.apron.home.thirdparty.TransferDeclinedException
import com.apron.home.thirdparty.TransferGateway
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransferServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val invoiceRepository: InvoiceRepository = mockk()
    private val transferGateway: TransferGateway = mockk()
    private val transferService = TransferService(accountRepository, invoiceRepository, transferGateway, 3, 200L)

    @Test
    fun `should return 'Processed' and 'Paid' for valid invoices, account and user`() {
        // arrange
        val invoiceIds = setOf(1, 2)
        val accountId = 1
        val userId = 1
        val targetPaymentDetails = "77777777"
        val transferIdempotencyId = "idempotency-id"
        val invoice1 = Invoice(
            id = 1,
            userId = userId,
            targetPaymentDetails = targetPaymentDetails,
            amount = BigDecimal("50.00"),
            currency = Currency.GBP,
            status = Invoice.Status.UNPAID,
            createdAt = LocalDateTime.now()
        )
        val invoice2 = Invoice(
            id = 2,
            userId = userId,
            targetPaymentDetails = targetPaymentDetails,
            amount = BigDecimal("125.00"),
            currency = Currency.GBP,
            status = Invoice.Status.UNPAID,
            createdAt = LocalDateTime.now()
        )
        val account = Account(
            id = accountId,
            userId = userId,
            type = Account.Type.CURRENT,
            balance = BigDecimal("200.00"),
            currency = Currency.GBP,
            createdAt = LocalDateTime.now()
        )
        every { invoiceRepository.saveTransferIdempotencyId(transferIdempotencyId) } returns 1
        every { invoiceRepository.findUnpaidInvoicesForUser(userId) } returns listOf(invoice1, invoice2)
        every { accountRepository.lockAccount(accountId) } returns 1
        every { accountRepository.unlockAccount(accountId) } returns 1
        every { accountRepository.findAccountById(accountId) } returns account
        every { transferGateway.calculateTransferFee(targetPaymentDetails, any(BigDecimal::class), Currency.GBP) } returns BigDecimal.ONE
        every { transferGateway.transfer(targetPaymentDetails, any(BigDecimal::class), Currency.GBP) } returns "OK"
        every {
            invoiceRepository.modifyInvoiceToPaidWithPaidByAccountId(
                any(Int::class),
                transferIdempotencyId,
                accountId
            )
        } returns 1
        every {
            accountRepository.modifyAccountBalanceForUser(
                accountId,
                userId,
                any(BigDecimal::class),
                any(BigDecimal::class)
            )
        } returns 1

        // act
        val result = transferService.transferInvoicesFromAccount(invoiceIds, accountId, userId, transferIdempotencyId)

        // assert
        assertThat(result).isInstanceOf(TransferService.TransferInvoicesResult.Processed::class.java)
        val processed = (result as TransferService.TransferInvoicesResult.Processed).results
        assertThat(processed.size).isEqualTo(2)
        assertThat(processed[0]).isInstanceOf(TransferService.TransferSingleInvoiceResult.Paid::class.java)
        assertThat(processed[1]).isInstanceOf(TransferService.TransferSingleInvoiceResult.Paid::class.java)
        verify(exactly = 2) { accountRepository.lockAccount(accountId) }
        verify(exactly = 2) { accountRepository.unlockAccount(accountId) }
    }

    @Test
    fun `should return 'Processed' and 'Declined' when TransferGateway throws TransferDeclinedException`() {
        // arrange
        val invoiceIds = setOf(1)
        val accountId = 1
        val userId = 1
        val targetPaymentDetails = "77777777"
        val transferIdempotencyId = "idempotency-id"
        val invoice1 = Invoice(
            id = 1,
            userId = userId,
            targetPaymentDetails = targetPaymentDetails,
            amount = BigDecimal("50.00"),
            currency = Currency.GBP,
            status = Invoice.Status.UNPAID,
            createdAt = LocalDateTime.now()
        )
        val account = Account(
            id = accountId,
            userId = userId,
            type = Account.Type.CURRENT,
            balance = BigDecimal("200.00"),
            currency = Currency.GBP,
            createdAt = LocalDateTime.now()
        )
        every { invoiceRepository.saveTransferIdempotencyId(transferIdempotencyId) } returns 1
        every { invoiceRepository.findUnpaidInvoicesForUser(userId) } returns listOf(invoice1)
        every { accountRepository.lockAccount(accountId) } returns 1
        every { accountRepository.unlockAccount(accountId) } returns 1
        every { accountRepository.findAccountById(accountId) } returns account
        every { transferGateway.calculateTransferFee(targetPaymentDetails, any(BigDecimal::class), Currency.GBP) } returns BigDecimal.ONE
        every {
            transferGateway.transfer(
                targetPaymentDetails,
                any(BigDecimal::class),
                Currency.GBP
            )
        } throws TransferDeclinedException("Declined")
        every {
            invoiceRepository.modifyInvoiceToDeclinedWithIdempotencyId(
                any(Int::class),
                transferIdempotencyId
            )
        } returns 1

        // act
        val result = transferService.transferInvoicesFromAccount(invoiceIds, accountId, userId, transferIdempotencyId)

        // assert
        assertThat(result).isInstanceOf(TransferService.TransferInvoicesResult.Processed::class.java)
        val processed = (result as TransferService.TransferInvoicesResult.Processed).results
        assertThat(processed.size).isEqualTo(1)
        assertThat(processed[0]).isInstanceOf(TransferService.TransferSingleInvoiceResult.Declined::class.java)
        verify(exactly = 0) { invoiceRepository.modifyInvoiceToPaidWithPaidByAccountId(any(), any(), any()) }
        verify(exactly = 0) { accountRepository.modifyAccountBalanceForUser(any(), any(), any(), any()) }
        verify { accountRepository.lockAccount(accountId) }
        verify { accountRepository.unlockAccount(accountId) }
    }

    @Test
    fun `should retry transfer when RequestTimeoutException is thrown and succeed`() {
        // arrange
        var attemptNumber = 0
        val transfer = {
            if (attemptNumber < 3) {
                attemptNumber = attemptNumber.inc()
                throw RequestTimeoutException("Request timed out")
            }
            "OK"
        }

        // act
        val result = transferService.retry(5, 200L, transfer)

        // assert
        assertThat(result).isEqualTo("OK")
        assertThat(attemptNumber).isEqualTo(3)
    }

    @Test
    fun `should decline transfer when RequestTimeoutException is thrown and retries are exhausted`() {
        // arrange
        var attemptNumber = 0
        val transfer = {
            if (attemptNumber < 5) {
                attemptNumber = attemptNumber.inc()
                throw RequestTimeoutException("Request timed out")
            }
            "OK"
        }

        // act
        val result = transferService.retry(5, 200L, transfer)

        // assert
        assertThat(result).isEqualTo("DECLINED")
        assertThat(attemptNumber).isEqualTo(5)
    }

    @Test
    fun `should decline transfer without retrying when RequestDeclinedException is thrown`() {
        // arrange
        var attemptNumber = 0
        val transfer = {
            if (attemptNumber < 3) {
                attemptNumber = attemptNumber.inc()
                throw TransferDeclinedException("Transfer declined")
            }
            "OK"
        }

        // act
        val result = transferService.retry(5, 200L, transfer)

        // assert
        assertThat(result).isEqualTo("DECLINED")
        assertThat(attemptNumber).isEqualTo(1)
    }
}
