package com.bjoynes.transactionsservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "treasury")
public record TreasuryProperties(
        String baseUrl,
        String ratesPath,
        int maxLookbackMonths,
        Duration connectTimeout,
        Duration readTimeout
) {
}