package com.bjoynes.transactionsservice.domain.treasury;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from the Treasury Reporting Rates of Exchange API.
 * @param data List of exchange rates, one per currency.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RatesOfExchangeResponse(List<RateRecord> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RateRecord(
            @JsonProperty("country") String country,
            @JsonProperty("country_currency_desc") String countryCurrencyDesc,
            @JsonProperty("exchange_rate") String exchangeRate,
            @JsonProperty("record_date") String recordDate
    ) {
    }
}
