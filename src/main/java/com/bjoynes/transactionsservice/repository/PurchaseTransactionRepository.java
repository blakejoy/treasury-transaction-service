package com.bjoynes.transactionsservice.repository;

import com.bjoynes.transactionsservice.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseTransactionRepository extends JpaRepository<Transaction, UUID> {
}
