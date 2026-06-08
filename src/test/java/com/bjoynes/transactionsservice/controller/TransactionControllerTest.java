package com.bjoynes.transactionsservice.controller;

import com.bjoynes.transactionsservice.domain.dto.ConvertedTransactionResponse;
import com.bjoynes.transactionsservice.domain.dto.StoredTransactionResponse;
import com.bjoynes.transactionsservice.exception.TransactionNotFoundException;
import com.bjoynes.transactionsservice.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        StoredTransactionResponse stored = new StoredTransactionResponse(
                id, "Coffee", LocalDate.of(2026, 3, 15), new BigDecimal("12.49"));
        when(transactionService.create(any())).thenReturn(stored);

        String body = """
                {"description":"Coffee","amountInUsd":12.49,"transactionDate":"2026-03-15"}
                """;

        mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/transactions/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.amountInUsd").value(12.49));
    }

    @Test
    void createReturns400OnInvalidBody() throws Exception {
        String body = """
                {"description":"","amountInUsd":0,"transactionDate":"2026-03-15"}
                """;

        mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConvertedTransaction() throws Exception {
        UUID id = UUID.randomUUID();
        ConvertedTransactionResponse response = new ConvertedTransactionResponse(
                id, "Coffee", LocalDate.of(2026, 3, 15), new BigDecimal("12.49"),
                List.of(new ConvertedTransactionResponse.CurrencyConversion(
                        "Canada", "Canada-Dollar", new BigDecimal("1.35"), new BigDecimal("16.86"),
                        LocalDate.of(2026, 1, 31))),
                List.of());

        when(transactionService.convert(eq(id), eq("Canada"), eq(null))).thenReturn(response);

        mockMvc.perform(get("/transactions/{id}", id).param("country", "Canada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].country").value("Canada"))
                .andExpect(jsonPath("$.conversions[0].currencyDescription").value("Canada-Dollar"))
                .andExpect(jsonPath("$.conversions[0].convertedAmount").value(16.86))
                .andExpect(jsonPath("$.conversions[0].exchangeRateUsed").value(1.35))
                .andExpect(jsonPath("$.conversions[0].recordDate").value("2026-01-31"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void returns200WithErrorsWhenSomeCurrenciesUnavailable() throws Exception {
        UUID id = UUID.randomUUID();
        ConvertedTransactionResponse response = new ConvertedTransactionResponse(
                id, "Coffee", LocalDate.of(2026, 3, 15), new BigDecimal("12.49"),
                List.of(new ConvertedTransactionResponse.CurrencyConversion(
                        "Euro Zone", "Euro Zone-Euro", new BigDecimal("0.92"), new BigDecimal("11.49"),
                        LocalDate.of(2026, 1, 31))),
                List.of(new ConvertedTransactionResponse.CurrencyError(
                        "Euro Zone", "Euro Zone-Krona", "no rate in window")));

        when(transactionService.convert(eq(id), eq("Euro Zone"), eq(null))).thenReturn(response);

        mockMvc.perform(get("/transactions/{id}", id).param("country", "Euro Zone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].currencyDescription").value("Euro Zone-Euro"))
                .andExpect(jsonPath("$.errors[0].currencyDescription").value("Euro Zone-Krona"))
                .andExpect(jsonPath("$.errors[0].message").value("no rate in window"));
    }

    @Test
    void returns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.convert(any(), any(), any()))
                .thenThrow(new TransactionNotFoundException(id));

        mockMvc.perform(get("/transactions/{id}", id).param("country", "Canada"))
                .andExpect(status().isNotFound());
    }
}
