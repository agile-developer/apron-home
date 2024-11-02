package com.apron.home.service

import com.apron.home.domain.Invoice
import com.apron.home.repository.InvoiceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InvoiceService(
    private val invoiceRepository: InvoiceRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun fetchInvoicesForUserByStatus(userId: Int, status: Invoice.Status?): List<Invoice> {
        logger.info("Fetching invoices for user-id: $userId with status: $status")
        return status?.let { invoiceRepository.findInvoicesForUserByStatus(userId, it) } ?: invoiceRepository.findAllInvoicesForUser(userId)
    }
}
