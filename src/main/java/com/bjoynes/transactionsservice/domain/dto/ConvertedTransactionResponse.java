package com.bjoynes.transactionsservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ConvertedTransactionResponse(
        UUID id,
        String description,
        LocalDate transactionDate,
        BigDecimal originalAmountInUsd,
        List<CurrencyConversion> conversions,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CurrencyError> errors
) {

    /**
     * The converted amount for a single country/currency, with the rate used and the
     * Treasury currency description that identifies it. Each entry is self-describing,
     * so a result may span multiple countries (e.g. a currency-only lookup).
     */
    public record CurrencyConversion(
            String country,
            String currencyDescription,
            BigDecimal exchangeRateUsed,
            BigDecimal convertedAmount,
            LocalDate recordDate
    ) {
    }

    /**
     * A currency that matched the lookup but could not be converted (no rate within the
     * lookback window). Present only on partial results spanning multiple currencies.
     */
    public record CurrencyError(
            String country,
            String currencyDescription,
            String message
    ) {
    }
}
