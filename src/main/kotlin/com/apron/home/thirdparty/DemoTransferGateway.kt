package com.apron.home.thirdparty

import com.apron.home.domain.Currency
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

@Component
class DemoTransferGateway : TransferGateway {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val fees = AtomicReference(BigDecimal.ZERO)

    override fun transfer(targetPaymentDetails: String, amount: BigDecimal, currency: Currency): String {
        logger.info("Calling transfer() for payment: $targetPaymentDetails, amount: $amount, currency: $currency")
        val waitTime = ThreadLocalRandom.current().nextInt(1, 5200)

        // ~ 4% chance of timeout
        if (waitTime > 5000) {
            logger.error("Timing out transfer with details: $targetPaymentDetails")
            throw RequestTimeoutException("Request timed out")
        }
        try {
            Thread.sleep(waitTime.toLong())
        } catch (e: InterruptedException) {
            logger.error("Sleep of transfer interrupted")
            throw RuntimeException(e)
        }

        // if the target payment details end with "13", the transfer is declined
        if (targetPaymentDetails.endsWith("13")) {
            logger.error("Declining transfer with details: $targetPaymentDetails")
            throw TransferDeclinedException("Transfer declined")
        }
        fees.accumulateAndGet(BigDecimal.ONE, BigDecimal::add)
        return "OK"
    }

    override fun calculateTransferFee(targetPaymentDetails: String, amount: BigDecimal, currency: Currency): BigDecimal = BigDecimal.ONE

    fun totalFees(): BigDecimal {
        return fees.get()
    }
}

class RequestTimeoutException(message: String) : Exception(message)

class TransferDeclinedException(message: String) : Exception(message)
