package com.bjoynes.transactionsservice.integration;

import com.bjoynes.transactionsservice.domain.dto.StoredTransactionResponse;
import com.bjoynes.transactionsservice.repository.PurchaseTransactionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: full stack against a real Postgres provided by Testcontainers,
 * mirroring the qa/staging/prod database engine. Tagged "integration" so it runs in
 * {@code mvn verify} (Failsafe) rather than on every unit build. Requires Docker.
 *
 * <p>Placeholder — verifies a transaction round-trips to Postgres via the API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("postgres")
@Testcontainers
@Tag("integration")
class TransactionPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PurchaseTransactionRepository repository;

    @Test
    void persistsTransactionToPostgres() {
        Map<String, Object> request = Map.of(
                "description", "Coffee and pastry",
                "amountInUsd", "12.49",
                "transactionDate", "2026-03-15");

        ResponseEntity<StoredTransactionResponse> response =
                restTemplate.postForEntity("/transactions", request, StoredTransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = response.getBody().id();
        assertThat(repository.findById(id)).isPresent();
    }
}
