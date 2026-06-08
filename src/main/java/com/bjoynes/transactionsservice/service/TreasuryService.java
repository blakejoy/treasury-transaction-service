package com.bjoynes.transactionsservice.service;

import com.bjoynes.transactionsservice.domain.treasury.ConversionRates;

import java.time.LocalDate;

public interface TreasuryService {
    ConversionRates getConversionRates(String country, String currency, LocalDate purchaseDate);
}
