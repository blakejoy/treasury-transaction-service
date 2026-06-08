package com.bjoynes.transactionsservice.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CurrencyConverter {

    private static final int CURRENCY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Converts a USD amount to a target currency using the given exchange rate
     *
     * AC: The converted purchase amount to the target currency should be rounded to two decimal places
     */
    public BigDecimal convert(BigDecimal amountInUsd, BigDecimal rate) {
        return amountInUsd.multiply(rate).setScale(CURRENCY_SCALE, ROUNDING);
    }

    /** Normalizes a monetary amount to cent precision (two decimal places). */
    public BigDecimal roundToCents(BigDecimal amount) {
        return amount.setScale(CURRENCY_SCALE, ROUNDING);
    }
}