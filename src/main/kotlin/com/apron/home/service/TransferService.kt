package com.apron.home.service

import com.apron.home.domain.Invoice
import com.apron.home.repository.AccountRepository
import com.apron.home.repository.InvoiceRepository
import com.apron.home.thirdparty.RequestTimeoutException
import com.apron.home.thirdparty.TransferGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class TransferService(
    private val accountRepository: AccountRepository,
    private val invoiceRepository: InvoiceRepository,
    private val transferGateway: TransferGateway,
    @Value("\${retryTransfer.numberOfAttempts}")
    private val numberOfAttempts: Int,
    @Value("\${retryTransfer.delayInMillis}")
    private val delayInMillis: Long
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun transferInvoicesFromAccount(invoiceIds: Set<Int>, accountId: Int, userId: Int, transferIdempotencyId: String): TransferInvoicesResult {
        logger.info("Processing invoices with idempotency-id: $transferIdempotencyId")
        runCatching {
            invoiceRepository.saveTransferIdempotencyId(transferIdempotencyId)
        }.onFailure {
            logger.error("Duplicate request, saving idempotency-id: $transferIdempotencyId encountered exception: ${it.message}")
            return TransferInvoicesResult.DuplicateIdempotencyId(transferIdempotencyId)
        }
        val invoices = invoiceRepository.findUnpaidInvoicesForUser(userId).filter { invoiceIds.contains(it.id) }
        if (invoices.isEmpty()) {
            return TransferInvoicesResult.Failure("No UNPAID invoices found for user-id: $userId in invoices: [${invoiceIds.joinToString()}]")
        }
        val account = accountRepository.findAccountById(accountId)
            ?: return TransferInvoicesResult.Failure("Account-id: $accountId not found in database")
        if (account.userId != userId) {
            return TransferInvoicesResult.Failure("Account-id: $accountId does not belong to $userId")
        }

        val transferInvoiceResults = invoices.map { transferSingleInvoiceFromAccount(it, account.id, transferIdempotencyId) }
        return TransferInvoicesResult.Processed(transferInvoiceResults)
    }

    fun transferSingleInvoiceFromAccount(invoice: Invoice, accountId: Int, transferIdempotencyId: String): TransferSingleInvoiceResult {
        logger.info("Transferring invoice: ${invoice.id} from account: $accountId")

        // Critical section
        val rowsLocked = accountRepository.lockAccount(accountId)
        if (rowsLocked != 1) {
            return TransferSingleInvoiceResult.Skipped(invoice, "Account with id: $accountId could not be locked for invoice transfer, it might be locked by another transaction")
        }
        try {
            val account = accountRepository.findAccountById(accountId) ?: return TransferSingleInvoiceResult.Skipped(invoice, "Account-id: $accountId not found")
            val transferFee = transferGateway.calculateTransferFee(invoice.targetPaymentDetails, invoice.amount, invoice.currency)
            val amountToTransfer = invoice.amount + transferFee
            if (account.balance < amountToTransfer) {
                logger.warn("Skipping invoice id: ${invoice.id} as account balance: ${account.balance} is less than invoice amount: ${invoice.amount} plus fees: $transferFee")
                return TransferSingleInvoiceResult.Skipped(invoice, "Account-id: $accountId balance insufficient")
            }
            val transferResult = retry(numberOfAttempts, delayInMillis) {
                transferGateway.transfer(invoice.targetPaymentDetails, amountToTransfer, invoice.currency)
            }
            if (transferResult.uppercase() == "OK") {
                invoiceRepository.modifyInvoiceToPaidWithPaidByAccountId(invoice.id, transferIdempotencyId, account.id)
                val currentBalance = account.balance
                val newBalance = currentBalance - amountToTransfer
                accountRepository.modifyAccountBalanceForUser(account.id, account.userId, currentBalance, newBalance)
                return TransferSingleInvoiceResult.Paid(invoice)
            } else {
                invoiceRepository.modifyInvoiceToDeclinedWithIdempotencyId(invoice.id, transferIdempotencyId)
                return TransferSingleInvoiceResult.Declined(invoice, "Unrecoverable error or retries exhausted")
            }
        } finally {
            accountRepository.unlockAccount(accountId)
        }
    }

    sealed interface TransferInvoicesResult {
        data class DuplicateIdempotencyId(private val transferIdempotencyId: String) : TransferInvoicesResult {
            val message get() = "Idempotency-id $transferIdempotencyId has already been processed"
        }
        data class Failure(val message: String) : TransferInvoicesResult
        data class Processed(val results: List<TransferSingleInvoiceResult>) : TransferInvoicesResult
    }

    sealed interface TransferSingleInvoiceResult {
        val invoice: Invoice
        val newStatus: Invoice.Status
        val message: String
        data class Paid(override val invoice: Invoice) : TransferSingleInvoiceResult {
            override val newStatus get() = Invoice.Status.PAID
            override val message get() = "Payment succeeded"
        }
        data class Declined(override val invoice: Invoice, val reason: String) : TransferSingleInvoiceResult {
            override val newStatus get() = Invoice.Status.DECLINED
            override val message get() = "Payment declined: $reason"
        }
        data class Skipped(override val invoice: Invoice, val reason: String) : TransferSingleInvoiceResult {
            override val newStatus get() = Invoice.Status.UNPAID
            override val message get() = "Payment skipped: $reason"
        }
    }

    fun retry(numberOfAttempts: Int, delayInMillis: Long, transfer: () -> String): String {
        var remainingAttempts = numberOfAttempts
        while (remainingAttempts > 0) {
            val attempt = numberOfAttempts - remainingAttempts
            try {
                logger.info("Transfer attempt: ${attempt + 1}")
                remainingAttempts--
                return transfer.invoke()
            } catch (requestTimeoutException: RequestTimeoutException) {
                logger.warn("Encountered timeout: ${requestTimeoutException.message}")
                if (remainingAttempts > 0) {
                    try {
                        Thread.sleep(delayInMillis)
                    } catch (interruptedException: InterruptedException) {
                        logger.info("Sleep interrupted")
                    }
                    continue
                }
            } catch (exception: Exception) {
                logger.error("Encountered non-recoverable exception: ${exception.message}")
                logger.error(exception.javaClass.name)
                break
            }
        }
        return "DECLINED"
    }
}
