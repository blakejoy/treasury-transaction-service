package com.bjoynes.transactionsservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(length = 50, nullable = false)
    private String description;

    @Column(name = "amount_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountInUsd;

    @Column(nullable = false)
    private LocalDate transactionDate;

    public Transaction(String description, BigDecimal amountInUsd, LocalDate transactionDate) {
        this.description = description;
        this.amountInUsd = amountInUsd;
        this.transactionDate = transactionDate;
    }
}
