package com.bjoynes.transactionsservice.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTransactionRequest(
        @NotBlank
        @Size(max = 50, message = "description must not exceed 50 characters")
        String description,

        @NotNull(message = "amountInUsd is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "amountInUsd must be a positive value")
        BigDecimal amountInUsd,

        @NotNull(message = "transactionDate is required")
        LocalDate transactionDate
) {
}
