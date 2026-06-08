package com.bjoynes.transactionsservice.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyConverterTest {

    private final CurrencyConverter converter = new CurrencyConverter();

    @Test
    void convertRoundsHalfUpToTwoDecimals() {
        assertThat(converter.convert(new BigDecimal("12.50"), new BigDecimal("1.3690")))
                .isEqualByComparingTo("17.11");
        assertThat(converter.convert(new BigDecimal("1.00"), new BigDecimal("1.005")))
                .isEqualByComparingTo("1.01");
    }

    @Test
    void roundToCentsAppliesHalfUp() {
        assertThat(converter.roundToCents(new BigDecimal("12.495"))).isEqualByComparingTo("12.50");
        assertThat(converter.roundToCents(new BigDecimal("12.494"))).isEqualByComparingTo("12.49");
    }
}
