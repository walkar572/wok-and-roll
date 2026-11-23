package com.wokandroll.back.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PriceCalculatorTest {

  private final PriceCalculator calculator = new PriceCalculator();

  /**
   * The shared file of the whole team (backend, Playwright, manual matrix). Subtotal, VAT and
   * Amount incl. VAT are checked here; discount and total are added with #55.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("sharedExamples")
  void matchesSharedPricingExamples(String id, JsonNode example) {
    List<BigDecimal> lineTotals = new ArrayList<>();
    for (JsonNode item : example.get("items")) {
      BigDecimal unitPrice = calculator.unitPrice(item.get("unitPrice").decimalValue(), List.of());
      lineTotals.add(calculator.lineTotal(unitPrice, item.get("quantity").asInt()));
    }
    JsonNode expected = example.get("expected");

    BigDecimal subtotal = calculator.subtotal(lineTotals);
    BigDecimal vat = calculator.vat(subtotal, expected.get("vatRate").decimalValue());
    BigDecimal amountInclVat = calculator.amountInclVat(subtotal, vat);

    assertThat(subtotal).isEqualByComparingTo(expected.get("subtotal").decimalValue());
    assertThat(vat).isEqualByComparingTo(expected.get("vat").decimalValue());
    assertThat(amountInclVat).isEqualByComparingTo(expected.get("amountInclVat").decimalValue());
  }

  @Test
  void vatRoundsHalfUp() {
    // 10.30 * 15% = 1.545 -> 1.55 (banker's rounding would give 1.54)
    assertThat(calculator.vat(new BigDecimal("10.30"), new BigDecimal("15.00")))
        .isEqualByComparingTo("1.55");
  }

  @Test
  void resultsAlwaysHaveTwoDecimals() {
    assertThat(calculator.vat(new BigDecimal("400.00"), new BigDecimal("15.00")).scale())
        .isEqualTo(2);
    assertThat(calculator.subtotal(List.of(new BigDecimal("5"))).scale()).isEqualTo(2);
    assertThat(calculator.subtotal(List.of()).scale()).isEqualTo(2);
  }

  @Test
  void unitPriceAddsModifierDeltas() {
    assertThat(
            calculator.unitPrice(
                new BigDecimal("48.00"), List.of(new BigDecimal("5.50"), new BigDecimal("0.00"))))
        .isEqualByComparingTo("53.50");
  }

  @Test
  void lineTotalMultipliesByQuantity() {
    assertThat(calculator.lineTotal(new BigDecimal("53.50"), 3)).isEqualByComparingTo("160.50");
  }

  @Test
  void zeroVatRateGivesZeroVat() {
    assertThat(calculator.vat(new BigDecimal("80.00"), new BigDecimal("0.00")))
        .isEqualByComparingTo("0.00");
  }

  static Stream<Arguments> sharedExamples() throws IOException {
    ObjectMapper mapper =
        new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    JsonNode examples = mapper.readTree(findExamplesFile().toFile());
    List<Arguments> arguments = new ArrayList<>();
    for (JsonNode example : examples) {
      arguments.add(Arguments.of(example.get("id").asText(), example));
    }
    return arguments.stream();
  }

  /** Maven runs from wok-and-roll-back; an IDE may run from the repo root. Try both. */
  private static Path findExamplesFile() {
    Path fromModule = Path.of("..", "wok-and-roll-qa", "test-data", "pricing-examples.json");
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    return Path.of("wok-and-roll-qa", "test-data", "pricing-examples.json");
  }
}
