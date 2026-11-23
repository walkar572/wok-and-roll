package com.wokandroll.back.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.wokandroll.back.pricing.dto.DiscountTier;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Discount, delivery fee and total (#145). The tiers are the same numbers in the local currency of
 * every country (Decision Log, 5 Nov): 100 / 500 / 700 / 1000 / 5000 with 3 / 5 / 7 / 10 / 15 %.
 */
class PriceCalculatorDiscountTest {

  private static final List<DiscountTier> TIERS =
      List.of(
          tier("100", "3"),
          tier("500", "5"),
          tier("700", "7"),
          tier("1000", "10"),
          tier("5000", "15"));

  private final PriceCalculator calculator = new PriceCalculator();

  /** Every case of the shared file: all steps from the unit price to the total. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("sharedExamples")
  void matchesEveryStepOfTheSharedPricingExamples(String id, JsonNode example) {
    List<BigDecimal> lineTotals = new ArrayList<>();
    for (JsonNode item : example.get("items")) {
      BigDecimal unitPrice = calculator.unitPrice(item.get("unitPrice").decimalValue(), List.of());
      lineTotals.add(calculator.lineTotal(unitPrice, item.get("quantity").asInt()));
    }
    JsonNode expected = example.get("expected");

    BigDecimal subtotal = calculator.subtotal(lineTotals);
    BigDecimal vat = calculator.vat(subtotal, expected.get("vatRate").decimalValue());
    BigDecimal amountInclVat = calculator.amountInclVat(subtotal, vat);
    BigDecimal discountRate = calculator.discountRate(amountInclVat, TIERS);
    BigDecimal discount = calculator.discount(amountInclVat, discountRate);
    BigDecimal deliveryFee = calculator.round(example.get("deliveryFee").decimalValue());
    BigDecimal total = calculator.total(amountInclVat, discount, deliveryFee);

    assertThat(subtotal).isEqualByComparingTo(expected.get("subtotal").decimalValue());
    assertThat(vat).isEqualByComparingTo(expected.get("vat").decimalValue());
    assertThat(amountInclVat).isEqualByComparingTo(expected.get("amountInclVat").decimalValue());
    assertThat(discountRate).isEqualByComparingTo(expected.get("discountRate").decimalValue());
    assertThat(discount).isEqualByComparingTo(expected.get("discount").decimalValue());
    assertThat(total).isEqualByComparingTo(expected.get("total").decimalValue());
  }

  /**
   * Just below and exactly on every threshold. The tiers are the same numbers for all 5 countries
   * (Decision Log, 5 Nov), and the calculator gets them as a list, so one run is enough.
   */
  @ParameterizedTest(name = "{0} -> {1} %")
  @MethodSource("thresholdEdges")
  void discountRateAtTheThresholdEdges(String amountInclVat, String expectedRate) {
    BigDecimal rate = calculator.discountRate(new BigDecimal(amountInclVat), TIERS);

    assertThat(rate).isEqualByComparingTo(expectedRate);
    assertThat(rate.scale()).isEqualTo(2);
  }

  @Test
  void orderOfTheTiersDoesNotMatter() {
    List<DiscountTier> reversed = new ArrayList<>(TIERS);
    Collections.reverse(reversed);

    assertThat(calculator.discountRate(new BigDecimal("700.00"), reversed))
        .isEqualByComparingTo("7.00");
    assertThat(calculator.discountRate(new BigDecimal("99.99"), reversed))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void noTiersMeansNoDiscount() {
    BigDecimal rate = calculator.discountRate(new BigDecimal("5000.00"), List.of());

    assertThat(rate).isEqualByComparingTo("0.00");
    assertThat(calculator.discount(new BigDecimal("5000.00"), rate)).isEqualByComparingTo("0.00");
  }

  @Test
  void discountRoundsHalfUp() {
    // 4999.99 * 10% = 499.999 -> 500.00
    // 151.50 * 3% = 4.545 -> 4.55 (HALF_EVEN would give 4.54, so this case catches a wrong mode)
    assertThat(calculator.discount(new BigDecimal("4999.99"), new BigDecimal("10.00")))
        .isEqualByComparingTo("500.00");
    assertThat(calculator.discount(new BigDecimal("151.50"), new BigDecimal("3.00")))
        .isEqualByComparingTo("4.55");
  }

  @Test
  void deliveryFeeIsAddedLastAndDoesNotChangeTheTier() {
    // 400.00 + VAT 60.00 = 460.00 -> 3% = 13.80; with the 20.00 zone the total is 466.20
    BigDecimal amountInclVat = new BigDecimal("460.00");
    BigDecimal discountRate = calculator.discountRate(amountInclVat, TIERS);
    BigDecimal discount = calculator.discount(amountInclVat, discountRate);

    assertThat(discountRate).isEqualByComparingTo("3.00");
    assertThat(calculator.total(amountInclVat, discount, new BigDecimal("0.00")))
        .isEqualByComparingTo("446.20");
    assertThat(calculator.total(amountInclVat, discount, new BigDecimal("20.00")))
        .isEqualByComparingTo("466.20");
    // 480.00 + a 20.00 fee would be 500.00, but the tier is chosen without the fee: still 3%
    assertThat(calculator.discountRate(new BigDecimal("480.00"), TIERS))
        .isEqualByComparingTo("3.00");
  }

  @Test
  void totalAlwaysHasTwoDecimals() {
    BigDecimal total =
        calculator.total(new BigDecimal("10"), new BigDecimal("0"), new BigDecimal("5"));

    assertThat(total).isEqualByComparingTo("15.00");
    assertThat(total.scale()).isEqualTo(2);
  }

  static Stream<Arguments> sharedExamples() throws IOException {
    return SharedPricingExamples.all();
  }

  static Stream<Arguments> thresholdEdges() {
    String[][] edges = {
      {"0.00", "0.00"},
      {"99.99", "0.00"},
      {"100.00", "3.00"},
      {"499.99", "3.00"},
      {"500.00", "5.00"},
      {"699.99", "5.00"},
      {"700.00", "7.00"},
      {"999.99", "7.00"},
      {"1000.00", "10.00"},
      {"4999.99", "10.00"},
      {"5000.00", "15.00"},
      {"99999.99", "15.00"}
    };
    return Stream.of(edges).map(edge -> Arguments.of(edge[0], edge[1]));
  }

  private static DiscountTier tier(String minAmount, String ratePercent) {
    return new DiscountTier(new BigDecimal(minAmount), new BigDecimal(ratePercent));
  }
}
