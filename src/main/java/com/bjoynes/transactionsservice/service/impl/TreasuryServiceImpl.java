package com.bjoynes.transactionsservice.service.impl;

import com.bjoynes.transactionsservice.config.TreasuryProperties;
import com.bjoynes.transactionsservice.domain.treasury.RatesOfExchangeResponse;
import com.bjoynes.transactionsservice.exception.CurrencyConversionException;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRate;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRates;
import com.bjoynes.transactionsservice.domain.treasury.ConversionRates.UnavailableCurrency;
import com.bjoynes.transactionsservice.service.TreasuryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the Treasury Reporting Rates of Exchange API. We fetch every matching currency's
 * most recent rate on or before the purchase date (no lower date bound), then classify
 * each currency in code: its latest rate is usable only if it falls inside the lookback
 * window. This lets us report stale currencies rather than silently dropping them.
 */
@Service
public class TreasuryServiceImpl implements TreasuryService {

    /**
     * Upper bound on rows pulled back per lookup. Rows are newest-first, so this needs to
     * be large enough to capture the latest record of every currency the lookup matches.
     */
    private static final int MAX_ROWS = 200;

    private final RestClient treasuryRestClient;
    private final TreasuryProperties properties;

    public TreasuryServiceImpl(RestClient treasuryRestClient, TreasuryProperties properties) {
        this.treasuryRestClient = treasuryRestClient;
        this.properties = properties;
    }

    @Override
    public ConversionRates getConversionRates(String country, String currency, LocalDate purchaseDate) {
        // AC: Conversion rate less than or equal to the purchase date from within the last 6 months
        LocalDate earliest = purchaseDate.minusMonths(properties.maxLookbackMonths());
        StringBuilder filter = new StringBuilder("country:eq:%s,record_date:lte:%s"
                .formatted(country, purchaseDate));
        if (StringUtils.hasText(currency)) {
            filter.append(",currency:eq:").append(currency);
        }
        String filterValue = filter.toString();

        RatesOfExchangeResponse response = treasuryRestClient.get()
                .uri(uri -> uri
                        .path(properties.ratesPath())
                        .queryParam("fields", "country,country_currency_desc,exchange_rate,record_date")
                        .queryParam("filter", filterValue)
                        .queryParam("sort", "-record_date")
                        .queryParam("page[size]", MAX_ROWS)
                        .build())
                .retrieve()
                .body(RatesOfExchangeResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            String target = StringUtils.hasText(currency) ? country + "/" + currency : country;
            throw new CurrencyConversionException(
                    "The purchase cannot be converted for '%s': no exchange rate on record on or before %s"
                            .formatted(target, purchaseDate));
        }

        Map<String, RatesOfExchangeResponse.RateRecord> latestByCurrency = new LinkedHashMap<>();
        for (RatesOfExchangeResponse.RateRecord record : response.data()) {
            latestByCurrency.putIfAbsent(record.countryCurrencyDesc(), record);
        }

        List<ConversionRate> rates = new ArrayList<>();
        List<UnavailableCurrency> unavailable = new ArrayList<>();
        for (RatesOfExchangeResponse.RateRecord record : latestByCurrency.values()) {
            LocalDate recordDate = LocalDate.parse(record.recordDate());
            if (!recordDate.isBefore(earliest)) {
                rates.add(new ConversionRate(
                        record.country(), new BigDecimal(record.exchangeRate()), record.countryCurrencyDesc(), recordDate));
            } else {
                unavailable.add(new UnavailableCurrency(record.country(), record.countryCurrencyDesc(),
                        "no exchange rate within %d months on or before %s (latest rate was %s)"
                                .formatted(properties.maxLookbackMonths(), purchaseDate, recordDate)));
            }
        }

        // A lookup that resolves to a single currency must yield a usable rate or fail outright.
        if (latestByCurrency.size() == 1 && rates.isEmpty()) {
            UnavailableCurrency only = unavailable.getFirst();
            throw new CurrencyConversionException(
                    "The purchase cannot be converted for '%s': %s"
                            .formatted(only.currencyDescription(), only.reason()));
        }

        return new ConversionRates(List.copyOf(rates), List.copyOf(unavailable));
    }
}
