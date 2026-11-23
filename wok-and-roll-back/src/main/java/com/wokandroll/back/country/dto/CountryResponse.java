package com.wokandroll.back.country.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One country in the switcher: code, name, currency and VAT rate (api-contract.md 3.1). */
public record CountryResponse(
    @Schema(example = "UZB") String code,
    @Schema(example = "Uzbekistan") String name,
    @Schema(description = "ISO 4217 currency code", example = "UZS") String currency,
    @Schema(description = "VAT in percent, 2 decimals", example = "15.00") BigDecimal vatRate,
    @Schema(description = "IANA time zone", example = "Asia/Tashkent") String timeZone) {}
