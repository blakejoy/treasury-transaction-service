package com.bjoynes.transactionsservice.domain.dto;

import com.bjoynes.transactionsservice.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StoredTransactionResponse(
        UUID id,
        String description,
        LocalDate transactionDate,
        BigDecimal amountInUsd
) {
    public static StoredTransactionResponse from(Transaction transaction) {
        return new StoredTransactionResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getAmountInUsd()
        );
    }
}
