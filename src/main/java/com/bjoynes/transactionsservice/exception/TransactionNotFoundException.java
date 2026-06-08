package com.bjoynes.transactionsservice.exception;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID id) {
        super("No transaction found with id " + id);
    }
}
