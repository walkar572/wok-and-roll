package com.wokandroll.back.pricing.service;

import com.wokandroll.back.pricing.dto.DiscountTier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The money math of the cart, with no database and no Spring inside. Every result is rounded to 2
 * decimals half-up right after the step (Decision Log, 5 Nov), so the saved steps always add up.
 *
 * <p>Formula (docs/pricing.md): Subtotal -> VAT -> Amount incl. VAT -> Discount -> Delivery fee ->
 * Total. The discount rate is chosen by Amount incl. VAT; the delivery fee is added last, so it has
 * no VAT, no discount and does not change the discount tier.
 */
@Component
public class PriceCalculator {

  public static final int SCALE = 2;
  public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  /** Rounds to 2 decimals, half-up. */
  public BigDecimal round(BigDecimal value) {
    return value.setScale(SCALE, ROUNDING);
  }

  /** Unit price = regional price + the price deltas of the chosen modifiers. */
  public BigDecimal unitPrice(BigDecimal regionalPrice, List<BigDecimal> modifierDeltas) {
    BigDecimal sum = regionalPrice;
    for (BigDecimal delta : modifierDeltas) {
      sum = sum.add(delta);
    }
    return round(sum);
  }

  public BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
    return round(unitPrice.multiply(BigDecimal.valueOf(quantity)));
  }

  public BigDecimal subtotal(List<BigDecimal> lineTotals) {
    BigDecimal sum = BigDecimal.ZERO;
    for (BigDecimal lineTotal : lineTotals) {
      sum = sum.add(lineTotal);
    }
    return round(sum);
  }

  /** VAT amount. The rate is a percent, for example 15.00 means 15%. */
  public BigDecimal vat(BigDecimal subtotal, BigDecimal vatRatePercent) {
    return round(subtotal.multiply(vatRatePercent).divide(HUNDRED));
  }

  public BigDecimal amountInclVat(BigDecimal subtotal, BigDecimal vat) {
    return round(subtotal.add(vat));
  }

  /**
   * The discount rate in percent for an Amount incl. VAT: the tier with the biggest {@code
   * minAmount} that is not more than the amount, so exactly 100.00 already gets the first tier. If
   * no tier fits (or there are no tiers) the rate is 0.00. The order of the list does not matter.
   */
  public BigDecimal discountRate(BigDecimal amountInclVat, List<DiscountTier> tiers) {
    BigDecimal bestMinAmount = null;
    BigDecimal bestRate = BigDecimal.ZERO;
    for (DiscountTier tier : tiers) {
      boolean reached = tier.minAmount().compareTo(amountInclVat) <= 0;
      if (reached && (bestMinAmount == null || tier.minAmount().compareTo(bestMinAmount) > 0)) {
        bestMinAmount = tier.minAmount();
        bestRate = tier.ratePercent();
      }
    }
    return round(bestRate);
  }

  /** Discount amount = Amount incl. VAT x rate (percent), rounded half-up. */
  public BigDecimal discount(BigDecimal amountInclVat, BigDecimal discountRatePercent) {
    return round(amountInclVat.multiply(discountRatePercent).divide(HUNDRED));
  }

  /** Total = Amount incl. VAT - Discount + Delivery fee (0.00 for pickup). */
  public BigDecimal total(BigDecimal amountInclVat, BigDecimal discount, BigDecimal deliveryFee) {
    return round(amountInclVat.subtract(discount).add(deliveryFee));
  }
}
