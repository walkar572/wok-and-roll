package com.wokandroll.back.country.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * One country: code, name, currency, VAT rate, time zone and the cities of its cafés
 * (api-contract.md 3.1). The city switcher of the interface lists the cities (v1.9).
 */
public record CountryResponse(
    @Schema(example = "UZB") String code,
    @Schema(example = "Uzbekistan") String name,
    @Schema(description = "ISO 4217 currency code", example = "UZS") String currency,
    @Schema(description = "VAT in percent, 2 decimals", example = "15.00") BigDecimal vatRate,
    @Schema(description = "IANA time zone", example = "Asia/Tashkent") String timeZone,
    @Schema(
            description = "Cities with an active café, A to Z; empty when there is none",
            example = "[\"Tashkent\"]")
        List<String> cities) {}
