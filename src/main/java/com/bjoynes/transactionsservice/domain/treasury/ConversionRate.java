package com.bjoynes.transactionsservice.domain.treasury;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionRate(String country, BigDecimal rate, String currencyDescription, LocalDate recordDate) {
}
