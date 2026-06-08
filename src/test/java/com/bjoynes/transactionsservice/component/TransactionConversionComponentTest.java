package com.bjoynes.transactionsservice.component;

import com.bjoynes.transactionsservice.domain.dto.ConvertedTransactionResponse;
import com.bjoynes.transactionsservice.domain.dto.StoredTransactionResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component test for the currency-conversion flow: boots the whole app (dev/H2) and
 * exercises web -> service -> JPA over real HTTP, with the Treasury Rates of Exchange
 * API replaced by a WireMock stub so the test stays hermetic. Untagged, so it runs in
 * the normal {@code mvn test} build alongside the unit tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("dev")
class TransactionConversionComponentTest {

    private static final String RATES_PATH = "/v1/accounting/od/rates_of_exchange";

    private static final WireMockServer TREASURY = new WireMockServer(options().dynamicPort());

    static {
        TREASURY.start();
    }

    @AfterAll
    static void stopWireMock() {
        TREASURY.stop();
    }

    @DynamicPropertySource
    static void treasuryProperties(DynamicPropertyRegistry registry) {
        registry.add("treasury.base-url", TREASURY::baseUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetStubs() {
        TREASURY.resetAll();
    }

    @Test
    void convertsSingleCurrencyAndReturns200() {
        TREASURY.stubFor(get(urlPathEqualTo(RATES_PATH))
                .withQueryParam("filter", containing("country:eq:Canada"))
                .willReturn(okJson("""
                        {"data":[{"country":"Canada","country_currency_desc":"Canada-Dollar","exchange_rate":"1.35","record_date":"2026-01-31"}]}
                        """)));
        UUID id = createTransaction("10.00", "2026-03-15");

        ResponseEntity<ConvertedTransactionResponse> response = restTemplate.getForEntity(
                "/transactions/{id}?country=Canada", ConvertedTransactionResponse.class, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errors()).isNullOrEmpty();
        assertThat(response.getBody().conversions()).singleElement().satisfies(c -> {
            assertThat(c.currencyDescription()).isEqualTo("Canada-Dollar");
            assertThat(c.convertedAmount()).isEqualByComparingTo("13.50");
        });
    }

    @Test
    void returns200WithErrorsWhenSomeCurrenciesAreStale() {
        TREASURY.stubFor(get(urlPathEqualTo(RATES_PATH))
                .withQueryParam("filter", containing("country:eq:Euro Zone"))
                .willReturn(okJson("""
                        {"data":[
                          {"country":"Euro Zone","country_currency_desc":"Euro Zone-Euro","exchange_rate":"0.92","record_date":"2026-01-31"},
                          {"country":"Euro Zone","country_currency_desc":"Euro Zone-Krona","exchange_rate":"10.5","record_date":"2025-01-31"}
                        ]}
                        """)));
        UUID id = createTransaction("10.00", "2026-03-15");

        ResponseEntity<ConvertedTransactionResponse> response = restTemplate.getForEntity(
                "/transactions/{id}?country=Euro Zone", ConvertedTransactionResponse.class, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().conversions()).singleElement()
                .satisfies(c -> assertThat(c.currencyDescription()).isEqualTo("Euro Zone-Euro"));
        assertThat(response.getBody().errors()).singleElement()
                .satisfies(e -> assertThat(e.currencyDescription()).isEqualTo("Euro Zone-Krona"));
    }

    @Test
    void returns422WhenNoRateOnRecord() {
        TREASURY.stubFor(get(urlPathEqualTo(RATES_PATH))
                .willReturn(okJson("{\"data\":[]}")));
        UUID id = createTransaction("10.00", "2026-03-15");

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/transactions/{id}?country=Atlantis", String.class, id);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    private UUID createTransaction(String amountInUsd, String transactionDate) {
        Map<String, Object> request = Map.of(
                "description", "Coffee",
                "amountInUsd", amountInUsd,
                "transactionDate", transactionDate);
        ResponseEntity<StoredTransactionResponse> created =
                restTemplate.postForEntity("/transactions", request, StoredTransactionResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        return created.getBody().id();
    }
}
