package com.bjoynes.transactionsservice.service.impl;

import com.bjoynes.transactionsservice.config.TreasuryProperties;
import com.bjoynes.transactionsservice.exception.CurrencyConversionException;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRate;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class TreasuryServiceImplTest {

    private MockRestServiceServer server;
    private TreasuryServiceImpl service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://treasury.test");
        server = MockRestServiceServer.bindTo(builder).build();
        TreasuryProperties properties = new TreasuryProperties(
                "https://treasury.test", "/v1/accounting/od/rates_of_exchange", 6,
                Duration.ofSeconds(2), Duration.ofSeconds(5));
        service = new TreasuryServiceImpl(builder.build(), properties);
    }

    @Test
    void returnsRateForNewestRecordWithinWindow() {
        String body = """
                {"data":[{"country":"Canada","country_currency_desc":"Canada-Dollar","exchange_rate":"1.35","record_date":"2026-01-31"}]}
                """;
        server.expect(method(GET))
                .andExpect(queryParam("sort", "-record_date"))
                .andExpect(queryParam("filter", "country:eq:Canada,record_date:lte:2026-03-15"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ConversionRates result = service.getConversionRates("Canada", null, LocalDate.of(2026, 3, 15));

        assertThat(result.unavailable()).isEmpty();
        assertThat(result.rates()).singleElement().satisfies(rate -> {
            assertThat(rate.country()).isEqualTo("Canada");
            assertThat(rate.rate()).isEqualByComparingTo("1.35");
            assertThat(rate.currencyDescription()).isEqualTo("Canada-Dollar");
        });
        server.verify();
    }

    @Test
    void returnsLatestRatePerCurrencyWhenCountryHasMultiple() {
        String body = """
                {"data":[
                  {"country":"Euro Zone","country_currency_desc":"Euro Zone-Euro","exchange_rate":"0.92","record_date":"2026-01-31"},
                  {"country":"Euro Zone","country_currency_desc":"Euro Zone-Euro","exchange_rate":"0.95","record_date":"2025-10-31"},
                  {"country":"Euro Zone","country_currency_desc":"Euro Zone-Krona","exchange_rate":"10.5","record_date":"2026-01-31"}
                ]}
                """;
        server.expect(method(GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ConversionRates result = service.getConversionRates("Euro Zone", null, LocalDate.of(2026, 3, 15));

        assertThat(result.unavailable()).isEmpty();
        assertThat(result.rates()).extracting(ConversionRate::currencyDescription)
                .containsExactly("Euro Zone-Euro", "Euro Zone-Krona");
        assertThat(result.rates().get(0).rate()).isEqualByComparingTo("0.92");
    }

    @Test
    void narrowsToCurrencyWhenProvided() {
        String body = """
                {"data":[{"country":"Canada","country_currency_desc":"Canada-Dollar","exchange_rate":"1.35","record_date":"2026-01-31"}]}
                """;
        server.expect(method(GET))
                .andExpect(queryParam("filter", "country:eq:Canada,record_date:lte:2026-03-15,currency:eq:Dollar"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ConversionRates result = service.getConversionRates("Canada", "Dollar", LocalDate.of(2026, 3, 15));

        assertThat(result.rates()).singleElement()
                .extracting(ConversionRate::currencyDescription)
                .isEqualTo("Canada-Dollar");
        server.verify();
    }

    @Test
    void reportsPartialWhenOnlySomeCurrenciesHaveInWindowRate() {
        String body = """
                {"data":[
                  {"country":"Euro Zone","country_currency_desc":"Euro Zone-Euro","exchange_rate":"0.92","record_date":"2026-01-31"},
                  {"country":"Euro Zone","country_currency_desc":"Euro Zone-Krona","exchange_rate":"10.5","record_date":"2025-01-31"}
                ]}
                """;
        server.expect(method(GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ConversionRates result = service.getConversionRates("Euro Zone", null, LocalDate.of(2026, 3, 15));

        assertThat(result.rates()).singleElement()
                .extracting(ConversionRate::currencyDescription).isEqualTo("Euro Zone-Euro");
        assertThat(result.unavailable()).singleElement().satisfies(u -> {
            assertThat(u.currencyDescription()).isEqualTo("Euro Zone-Krona");
            assertThat(u.reason()).contains("2025-01-31");
        });
    }

    @Test
    void throwsWhenSingleCurrencyHasNoInWindowRate() {
        String body = """
                {"data":[{"country":"Canada","country_currency_desc":"Canada-Dollar","exchange_rate":"1.35","record_date":"2024-01-31"}]}
                """;
        server.expect(method(GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getConversionRates("Canada", null, LocalDate.of(2026, 3, 15)))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("Canada-Dollar")
                .hasMessageContaining("2024-01-31");
    }

    @Test
    void throwsWhenNoRateOnRecord() {
        server.expect(method(GET)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getConversionRates("Canada", null, LocalDate.of(2026, 3, 15)))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("Canada");
    }
}
