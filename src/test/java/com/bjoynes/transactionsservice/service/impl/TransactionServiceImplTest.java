package com.bjoynes.transactionsservice.service.impl;

import com.bjoynes.transactionsservice.domain.Transaction;
import com.bjoynes.transactionsservice.domain.dto.ConvertedTransactionResponse;
import com.bjoynes.transactionsservice.domain.dto.CreateTransactionRequest;
import com.bjoynes.transactionsservice.domain.dto.StoredTransactionResponse;
import com.bjoynes.transactionsservice.exception.TransactionNotFoundException;
import com.bjoynes.transactionsservice.repository.PurchaseTransactionRepository;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRate;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRates;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRates.UnavailableCurrency;
import com.bjoynes.transactionsservice.service.CurrencyConverter;
import com.bjoynes.transactionsservice.service.TreasuryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private PurchaseTransactionRepository repository;

    @Mock
    private TreasuryService treasuryService;

    private TransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(repository, treasuryService, new CurrencyConverter());
    }

    @Test
    void createRoundsAmountToNearestCentBeforeSaving() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "Coffee", new BigDecimal("12.495"), LocalDate.of(2026, 3, 15));

        when(repository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        StoredTransactionResponse result = service.create(request);

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAmountInUsd()).isEqualByComparingTo("12.50");
        assertThat(result.amountInUsd()).isEqualByComparingTo("12.50");
        assertThat(result.id()).isNotNull();
    }

    @Test
    void convertsAndRoundsHalfUpToTwoDecimals() {
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 3, 15);
        Transaction tx = new Transaction("Coffee", new BigDecimal("1.00"), date);
        tx.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(tx));
        when(treasuryService.getConversionRates(eq("Canada"), eq(null), eq(date)))
                .thenReturn(new ConversionRates(
                        List.of(new ConversionRate("Canada", new BigDecimal("1.005"), "Canada-Dollar",
                                LocalDate.of(2026, 1, 31))),
                        List.of()));

        ConvertedTransactionResponse result = service.convert(id, "Canada", null);

        assertThat(result.originalAmountInUsd()).isEqualByComparingTo("1.00");
        assertThat(result.errors()).isEmpty();
        assertThat(result.conversions()).singleElement().satisfies(conversion -> {
            assertThat(conversion.country()).isEqualTo("Canada");
            assertThat(conversion.currencyDescription()).isEqualTo("Canada-Dollar");
            assertThat(conversion.exchangeRateUsed()).isEqualByComparingTo("1.005");
            assertThat(conversion.convertedAmount()).isEqualByComparingTo("1.01");
            assertThat(conversion.recordDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        });
    }

    @Test
    void mapsOneConversionPerCurrencyReturned() {
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 3, 15);
        Transaction tx = new Transaction("Coffee", new BigDecimal("10.00"), date);
        tx.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(tx));
        when(treasuryService.getConversionRates(eq("Euro Zone"), eq(null), eq(date)))
                .thenReturn(new ConversionRates(
                        List.of(
                                new ConversionRate("Euro Zone", new BigDecimal("0.92"), "Euro Zone-Euro",
                                        LocalDate.of(2026, 1, 31)),
                                new ConversionRate("Euro Zone", new BigDecimal("10.50"), "Euro Zone-Krona",
                                        LocalDate.of(2026, 1, 31))),
                        List.of()));

        ConvertedTransactionResponse result = service.convert(id, "Euro Zone", null);

        assertThat(result.conversions()).extracting(
                        ConvertedTransactionResponse.CurrencyConversion::currencyDescription,
                        c -> c.convertedAmount().stripTrailingZeros())
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Euro Zone-Euro", new BigDecimal("9.2")),
                        org.assertj.core.api.Assertions.tuple("Euro Zone-Krona", new BigDecimal("1.05E+2")));
    }

    @Test
    void carriesUnavailableCurrenciesThroughAsErrors() {
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 3, 15);
        Transaction tx = new Transaction("Coffee", new BigDecimal("10.00"), date);
        tx.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(tx));
        when(treasuryService.getConversionRates(eq("Euro Zone"), eq(null), eq(date)))
                .thenReturn(new ConversionRates(
                        List.of(new ConversionRate("Euro Zone", new BigDecimal("0.92"), "Euro Zone-Euro",
                                LocalDate.of(2026, 1, 31))),
                        List.of(new UnavailableCurrency("Euro Zone", "Euro Zone-Krona", "no rate in window"))));

        ConvertedTransactionResponse result = service.convert(id, "Euro Zone", null);

        assertThat(result.conversions()).singleElement()
                .extracting(ConvertedTransactionResponse.CurrencyConversion::currencyDescription)
                .isEqualTo("Euro Zone-Euro");
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.country()).isEqualTo("Euro Zone");
            assertThat(error.currencyDescription()).isEqualTo("Euro Zone-Krona");
            assertThat(error.message()).isEqualTo("no rate in window");
        });
    }

    @Test
    void throwsWhenTransactionMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convert(id, "Canada", null))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
