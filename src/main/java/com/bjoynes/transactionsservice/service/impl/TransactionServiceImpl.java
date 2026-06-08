package com.bjoynes.transactionsservice.service.impl;

import com.bjoynes.transactionsservice.domain.Transaction;
import com.bjoynes.transactionsservice.domain.dto.ConvertedTransactionResponse;
import com.bjoynes.transactionsservice.domain.dto.CreateTransactionRequest;
import com.bjoynes.transactionsservice.domain.dto.StoredTransactionResponse;
import com.bjoynes.transactionsservice.exception.TransactionNotFoundException;
import com.bjoynes.transactionsservice.repository.PurchaseTransactionRepository;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRates;
import com.bjoynes.transactionsservice.service.CurrencyConverter;
import com.bjoynes.transactionsservice.service.TransactionService;
import com.bjoynes.transactionsservice.service.TreasuryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final PurchaseTransactionRepository repository;
    private final TreasuryService treasuryService;
    private final CurrencyConverter currencyConverter;

    public TransactionServiceImpl(PurchaseTransactionRepository repository,
                                  TreasuryService treasuryService,
                                  CurrencyConverter currencyConverter) {
        this.repository = repository;
        this.treasuryService = treasuryService;
        this.currencyConverter = currencyConverter;
    }

    @Override
    public StoredTransactionResponse create(CreateTransactionRequest request) {
        BigDecimal amountInUsd = currencyConverter.roundToCents(request.amountInUsd());
        Transaction transaction = new Transaction(request.description(), amountInUsd, request.transactionDate());
        return StoredTransactionResponse.from(repository.save(transaction));
    }

    @Override
    public ConvertedTransactionResponse convert(UUID id, String country, String currency) {
        Transaction transaction = repository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        ConversionRates lookup =
                treasuryService.getConversionRates(country, currency, transaction.getTransactionDate());

        List<ConvertedTransactionResponse.CurrencyConversion> conversions = lookup.rates().stream()
                .map(rate -> new ConvertedTransactionResponse.CurrencyConversion(
                        rate.country(),
                        rate.currencyDescription(),
                        rate.rate(),
                        currencyConverter.convert(transaction.getAmountInUsd(), rate.rate()),
                        rate.recordDate()))
                .toList();

        List<ConvertedTransactionResponse.CurrencyError> errors = lookup.unavailable().stream()
                .map(u -> new ConvertedTransactionResponse.CurrencyError(
                        u.country(),
                        u.currencyDescription(),
                        u.reason()))
                .toList();

        return new ConvertedTransactionResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getAmountInUsd(),
                conversions,
                errors
        );
    }
}
