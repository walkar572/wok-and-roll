package com.wokandroll.back.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.wokandroll.back.pricing.dto.PriceBreakdown;
import com.wokandroll.back.pricing.dto.PricingItem;
import com.wokandroll.back.pricing.dto.PricingRequest;
import com.wokandroll.back.pricing.dto.Scenario;
import com.wokandroll.back.pricing.repository.FakePricingRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The same shared examples as in {@link PriceCalculatorTest}, but through the whole service, so the
 * rates, rounding and the response numbers are checked together.
 */
class PricingServiceSharedExamplesTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("sharedExamples")
  void quoteMatchesSharedPricingExamples(String id, JsonNode example) {
    JsonNode expected = example.get("expected");
    JsonNode item = example.get("items").get(0);
    String country = example.get("country").asText();
    BigDecimal fee = example.get("deliveryFee").decimalValue();
    FakePricingRepository repository =
        FakePricingRepository.oneProduct(
            country,
            example.get("currency").asText(),
            expected.get("vatRate").decimalValue().toPlainString(),
            item.get("unitPrice").decimalValue().toPlainString());
    PricingService service =
        new PricingService(
            repository,
            new PriceCalculator(),
            new FakeDeliveryZoneFees().with(country, 1, fee.toPlainString()));
    List<PricingItem> items = List.of(new PricingItem(1L, item.get("quantity").asInt(), List.of()));

    // The shared examples always include the delivery fee, so they are quoted as DELIVERY.
    PriceBreakdown result =
        service.calculate(new PricingRequest(country, items, Scenario.DELIVERY, 1L));

    assertThat(result.currency()).isEqualTo(example.get("currency").asText());
    assertThat(result.subtotal()).isEqualByComparingTo(expected.get("subtotal").decimalValue());
    assertThat(result.vatRate()).isEqualByComparingTo(expected.get("vatRate").decimalValue());
    assertThat(result.vat()).isEqualByComparingTo(expected.get("vat").decimalValue());
    assertThat(result.amountInclVat())
        .isEqualByComparingTo(expected.get("amountInclVat").decimalValue());
    assertThat(result.discountRate())
        .isEqualByComparingTo(expected.get("discountRate").decimalValue());
    assertThat(result.discount()).isEqualByComparingTo(expected.get("discount").decimalValue());
    assertThat(result.deliveryFee()).isEqualByComparingTo(fee);
    assertThat(result.total()).isEqualByComparingTo(expected.get("total").decimalValue());
  }

  static Stream<Arguments> sharedExamples() throws IOException {
    return SharedPricingExamples.all();
  }
}
