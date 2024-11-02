package com.apron.home.thirdparty

import com.apron.home.domain.Currency
import java.math.BigDecimal

interface TransferGateway {

    fun transfer(targetPaymentDetails: String, amount: BigDecimal, currency: Currency): String

    fun calculateTransferFee(targetPaymentDetails: String, amount: BigDecimal, currency: Currency): BigDecimal
}
