package com.apron.home

import com.apron.home.controller.SingleInvoiceTransferResult
import com.apron.home.controller.TopUpResponse
import com.apron.home.domain.Invoice
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.math.BigDecimal
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class ApronHomeApplicationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

//	@Test
//	fun contextLoads() {
//	}

    @Test
    fun `should top-up account for user by given amount`() {
        // arrange
        val userId = 1
        val accountId = 2
        val topUpRequest = """
			{
			  "amount" : "50.00",
			  "currency" : "GBP"
			}
		""".trimIndent()

        // act
        val result = mockMvc.perform(
            post("/apron-home/accounts/$accountId/top-up")
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .header("X-User-Id", userId)
                .header("X-Idempotency-Id", UUID.randomUUID().toString())
                .content(topUpRequest)
        ).andReturn()

        // assert
        assertThat(result.response.status).isEqualTo(200)
        val responseString = result.response.contentAsString
        val topUpResponse = objectMapper.readValue(responseString, TopUpResponse::class.java)
        assertThat(topUpResponse.newBalance).isEqualTo(BigDecimal("150.00"))
    }

    @Test
    fun `should transfer given invoices and mark them as PAID`() {
        // arrange
        val invoiceId1 = 1
        val invoiceId2 = 2
        val accountId = 1
        val userId =1
        val invoicesTransferRequest = """
            {
              "invoiceIds" : [$invoiceId1, $invoiceId2],
              "accountId" : $accountId
            }
        """.trimIndent()

        // act
        val result = mockMvc.perform(
            post("/apron-home/invoices/transfer")
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .header("X-User-Id", userId)
                .header("X-Idempotency-Id", UUID.randomUUID().toString())
                .content(invoicesTransferRequest)
        ).andReturn()

        // assert
        assertThat(result.response.status).isEqualTo(200)
        val responseString = result.response.contentAsString
        val transferResponses = objectMapper.readValue(responseString, object: TypeReference<List<SingleInvoiceTransferResult>>() {})
        assertThat(transferResponses.size).isEqualTo(2)
        assertThat(transferResponses[0].newStatus).isEqualTo(Invoice.Status.PAID)
        assertThat(transferResponses[1].newStatus).isEqualTo(Invoice.Status.PAID)
    }

    @Test
    fun `should not transfer given invoice and mark it as DECLINED`() {
        // arrange
        val invoiceId = 3
        val accountId = 1
        val userId =1
        val invoicesTransferRequest = """
            {
              "invoiceIds" : [$invoiceId],
              "accountId" : $accountId
            }
        """.trimIndent()

        // act
        val result = mockMvc.perform(
            post("/apron-home/invoices/transfer")
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .header("X-User-Id", userId)
                .header("X-Idempotency-Id", UUID.randomUUID().toString())
                .content(invoicesTransferRequest)
        ).andReturn()

        // assert
        assertThat(result.response.status).isEqualTo(200)
        val responseString = result.response.contentAsString
        val transferResponses = objectMapper.readValue(responseString, object: TypeReference<List<SingleInvoiceTransferResult>>() {})
        assertThat(transferResponses.size).isEqualTo(1)
        assertThat(transferResponses[0].newStatus).isEqualTo(Invoice.Status.DECLINED)
    }
}
