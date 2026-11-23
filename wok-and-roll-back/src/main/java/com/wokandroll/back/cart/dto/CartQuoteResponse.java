package com.wokandroll.back.cart.dto;

import com.wokandroll.back.pricing.dto.PriceBreakdown;
import com.wokandroll.back.pricing.dto.Scenario;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Price breakdown of a cart. Money has 2 decimals, in the currency of the country. Rates are
 * percent numbers. Total = amount incl. VAT - discount + delivery fee.
 */
@Schema(description = "Price breakdown of the cart")
public record CartQuoteResponse(
    @Schema(example = "UZB") String country,
    @Schema(example = "UZS") String currency,
    @Schema(example = "DELIVERY") Scenario scenario,
    List<CartLineResponse> lines,
    @Schema(example = "400.00") BigDecimal subtotal,
    @Schema(description = "VAT rate in percent", example = "15.00") BigDecimal vatRate,
    @Schema(example = "60.00") BigDecimal vat,
    @Schema(example = "460.00") BigDecimal amountInclVat,
    @Schema(description = "Discount rate in percent", example = "3.00") BigDecimal discountRate,
    @Schema(example = "13.80") BigDecimal discount,
    @Schema(description = "0.00 for pickup", example = "20.00") BigDecimal deliveryFee,
    @Schema(description = "Amount incl. VAT - discount + delivery fee", example = "466.20")
        BigDecimal total) {

  public static CartQuoteResponse from(PriceBreakdown breakdown) {
    List<CartLineResponse> lines =
        breakdown.lines().stream()
            .map(
                line ->
                    new CartLineResponse(
                        line.productId(),
                        line.name(),
                        line.quantity(),
                        line.unitPrice(),
                        line.lineTotal(),
                        line.modifiers().stream()
                            .map(
                                m ->
                                    new CartModifierResponse(
                                        m.id(), m.name(), m.type().name(), m.priceDelta()))
                            .toList()))
            .toList();
    return new CartQuoteResponse(
        breakdown.country(),
        breakdown.currency(),
        breakdown.scenario(),
        lines,
        breakdown.subtotal(),
        breakdown.vatRate(),
        breakdown.vat(),
        breakdown.amountInclVat(),
        breakdown.discountRate(),
        breakdown.discount(),
        breakdown.deliveryFee(),
        breakdown.total());
  }
}
