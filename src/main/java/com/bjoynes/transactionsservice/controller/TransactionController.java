package com.bjoynes.transactionsservice.controller;

import com.bjoynes.transactionsservice.domain.dto.ConvertedTransactionResponse;
import com.bjoynes.transactionsservice.domain.dto.CreateTransactionRequest;
import com.bjoynes.transactionsservice.domain.dto.StoredTransactionResponse;
import com.bjoynes.transactionsservice.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Create a new transaction.
     * @param request the transaction to create
     * @param uriBuilder the URI builder to use to create the location header
     * @return
     */
    @PostMapping
    public ResponseEntity<StoredTransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request,
            UriComponentsBuilder uriBuilder) {
        StoredTransactionResponse created = transactionService.create(request);
        URI location = uriBuilder.path("/transactions/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }


    /**
     * Get a converted transaction.
     * @param id the transaction ID
     * @param country the country to convert to
     * @param currency the currency to convert to (optional)
     * @return the converted transaction
     */
    @GetMapping("/{id}")
    public ConvertedTransactionResponse getConverted(
            @PathVariable UUID id,
            @RequestParam("country") String country,
            @RequestParam(value = "currency", required = false) String currency) {
        return transactionService.convert(id, country, currency);
    }
}