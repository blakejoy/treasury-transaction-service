package com.bjoynes.transactionsservice.component;

import com.bjoynes.transactionsservice.domain.dto.StoredTransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component test: boots the whole application in-process on the dev profile (H2)
 * and exercises the create flow over real HTTP through web -> service -> JPA.
 *
 * <p>Placeholder — covers POST only (no external calls). Conversion (GET) coverage
 * would add a stubbed Treasury API (e.g. WireMock) so it stays hermetic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("dev")
class TransactionFlowComponentTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createPersistsTransactionAndReturns201() {
        Map<String, Object> request = Map.of(
                "description", "Coffee and pastry",
                "amountInUsd", "12.49",
                "transactionDate", "2026-03-15");

        ResponseEntity<StoredTransactionResponse> response =
                restTemplate.postForEntity("/transactions", request, StoredTransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().amountInUsd()).isEqualByComparingTo("12.49");
    }
}
