package com.bjoynes.transactionsservice.domain.treasury;

import java.util.List;

public record ConversionRates(List<ConversionRate> rates, List<UnavailableCurrency> unavailable) {

    public record UnavailableCurrency(String country, String currencyDescription, String reason) {
    }
}
