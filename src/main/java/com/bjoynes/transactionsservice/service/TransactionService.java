package com.bjoynes.transactionsservice.service;

import com.bjoynes.transactionsservice.domain.dto.ConvertedTransactionResponse;
import com.bjoynes.transactionsservice.domain.dto.CreateTransactionRequest;
import com.bjoynes.transactionsservice.domain.dto.StoredTransactionResponse;

import java.util.UUID;

public interface TransactionService {


    StoredTransactionResponse create(CreateTransactionRequest request);

    ConvertedTransactionResponse convert(UUID id, String country, String currency);
}
