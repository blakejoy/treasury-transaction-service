package com.bjoynes.transactionsservice.live;

import com.bjoynes.transactionsservice.config.TreasuryClientConfig;
import com.bjoynes.transactionsservice.config.TreasuryProperties;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRates;
import com.bjoynes.transactionsservice.service.impl.TreasuryServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live smoke test against the real Treasury Reporting Rates of Exchange API. Tagged
 * "live" and excluded from the default build (it is non-hermetic and depends on network
 * + upstream availability). Run on demand with {@code mvn test -Plive}.
 *
 * <p>Uses the production {@link TreasuryClientConfig} client wiring so it also exercises
 * the real TLS chain — useful given the documented local-JDK PKIX quirk against
 * {@code api.fiscaldata.treasury.gov}.
 */
@Tag("live")
class TreasuryLiveSmokeTest {

    @Test
    void fetchesCanadaDollarRateFromLiveApi() {
        TreasuryProperties properties = new TreasuryProperties(
                "https://api.fiscaldata.treasury.gov/services/api/fiscal_service",
                "/v1/accounting/od/rates_of_exchange", 6,
                Duration.ofSeconds(5), Duration.ofSeconds(10));
        RestClient client = new TreasuryClientConfig().treasuryRestClient(properties);
        TreasuryServiceImpl service = new TreasuryServiceImpl(client, properties);

        ConversionRates result = service.getConversionRates("Canada", "Dollar", LocalDate.now().minusMonths(1));

        assertThat(result.rates()).isNotEmpty();
        assertThat(result.rates()).allSatisfy(rate -> {
            assertThat(rate.country()).isEqualTo("Canada");
            assertThat(rate.currencyDescription()).isEqualTo("Canada-Dollar");
            assertThat(rate.rate()).isPositive();
        });
    }
}
