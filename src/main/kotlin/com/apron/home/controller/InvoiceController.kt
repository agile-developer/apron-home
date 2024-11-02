package com.apron.home.controller

import com.apron.home.domain.Currency
import com.apron.home.domain.Invoice
import com.apron.home.domain.Invoice.Status
import com.apron.home.service.InvoiceService
import com.apron.home.service.TransferService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/apron-home/invoices")
class InvoiceController(
    private val invoiceService: InvoiceService,
    private val transferService: TransferService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun fetchInvoicesForUser(
        @RequestHeader("X-User-Id")
        userId: Int,
        @RequestParam("status")
        status: Invoice.Status?
    ): ResponseEntity<List<InvoiceResponse>> {
        logger.info("Fetching invoices for user-id: $userId with status: $status")
        val invoices = invoiceService.fetchInvoicesForUserByStatus(userId, status)
        return ResponseEntity.ok(invoices.map { it.toInvoiceResponse() })
    }

    @PostMapping("/transfer")
    fun transferInvoicesFromAccount(
        @RequestHeader("X-User-id") userId: Int,
        @RequestHeader("X-Idempotency-Id") idempotencyId: String,
        @RequestBody transferRequest: InvoicesTransferRequest
    ): ResponseEntity<*> {
        logger.info("Transferring invoices: [${transferRequest.invoiceIds.joinToString()}] from account-id: ${transferRequest.accountId} for user-id: $userId")
        val result = transferService.transferInvoicesFromAccount(
            transferRequest.invoiceIds,
            transferRequest.accountId,
            userId,
            idempotencyId
        )
        return when (result) {
            is TransferService.TransferInvoicesResult.DuplicateIdempotencyId -> ResponseEntity.badRequest().body(result.message)
            is TransferService.TransferInvoicesResult.Failure -> ResponseEntity.badRequest().body(result.message)
            is TransferService.TransferInvoicesResult.Processed -> ResponseEntity.ok(result.results.map {
                with(it.invoice) {
                    SingleInvoiceTransferResult(id, amount, currency, status, it.newStatus, it.message)
                }
            })
        }
    }
}

data class InvoiceResponse(
    val id: Int,
    val userId: Int,
    val targetPaymentDetails: String,
    val amount: BigDecimal,
    val currency: Currency,
    val status: Status,
    val vendorName: String,
    val details: String,
)

data class InvoicesTransferRequest(
    val invoiceIds: Set<Int>,
    val accountId: Int,
)

data class SingleInvoiceTransferResult(
    val invoiceId: Int,
    val amount: BigDecimal,
    val currency: Currency,
    val oldStatus: Status,
    val newStatus: Status,
    val message: String,
)

fun Invoice.toInvoiceResponse(): InvoiceResponse {
    return with(this) {
        InvoiceResponse(id, userId, targetPaymentDetails, amount, currency, status, vendorName, details)
    }
}
