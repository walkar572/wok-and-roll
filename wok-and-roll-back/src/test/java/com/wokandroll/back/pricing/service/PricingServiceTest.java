package com.wokandroll.back.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.pricing.dto.ModifierType;
import com.wokandroll.back.pricing.dto.PriceBreakdown;
import com.wokandroll.back.pricing.dto.PricedLine;
import com.wokandroll.back.pricing.dto.PricingItem;
import com.wokandroll.back.pricing.dto.PricingRequest;
import com.wokandroll.back.pricing.dto.Scenario;
import com.wokandroll.back.pricing.repository.FakePricingRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PricingServiceTest {

  private final PricingService service =
      new PricingService(
          FakePricingRepository.sampleMenu(), new PriceCalculator(), FakeDeliveryZoneFees.sample());

  @Test
  void pricesLinesWithModifiersAndVat() {
    // udon 48.00 + chili 5.50 = 53.50, x2 = 107.00; gyoza 32.00; subtotal 139.00
    // VAT 15% = 20.85; amount incl. VAT = 159.85
    PriceBreakdown result =
        service.calculate(
            request(
                "UZB", new PricingItem(5L, 2, List.of(100L)), new PricingItem(8L, 1, List.of())));

    assertThat(result.country()).isEqualTo("UZB");
    assertThat(result.currency()).isEqualTo("UZS");
    assertThat(result.subtotal()).isEqualByComparingTo("139.00");
    assertThat(result.vatRate()).isEqualByComparingTo("15.00");
    assertThat(result.vat()).isEqualByComparingTo("20.85");
    assertThat(result.amountInclVat()).isEqualByComparingTo("159.85");

    PricedLine udon = result.lines().get(0);
    assertThat(udon.name()).isEqualTo("Firecracker udon");
    assertThat(udon.quantity()).isEqualTo(2);
    assertThat(udon.unitPrice()).isEqualByComparingTo("53.50");
    assertThat(udon.lineTotal()).isEqualByComparingTo("107.00");
    assertThat(udon.modifiers()).hasSize(1);
    assertThat(udon.modifiers().get(0).name()).isEqualTo("Extra chili");
  }

  @Test
  void countryCodeIsNotCaseSensitive() {
    PriceBreakdown result = service.calculate(request(" uzb ", new PricingItem(8L, 1, null)));

    assertThat(result.country()).isEqualTo("UZB");
    assertThat(result.subtotal()).isEqualByComparingTo("32.00");
  }

  @Test
  void sameProductTwiceGivesTwoLines() {
    PriceBreakdown result =
        service.calculate(
            request(
                "UZB", new PricingItem(5L, 1, List.of()), new PricingItem(5L, 1, List.of(100L))));

    assertThat(result.lines()).hasSize(2);
    assertThat(result.subtotal()).isEqualByComparingTo("101.50");
  }

  @Test
  void unknownProductIsBadRequest() {
    assertBadRequest(
        request("UZB", new PricingItem(99L, 1, List.of())),
        "UNKNOWN_PRODUCT",
        "items[0].productId");
  }

  @Test
  void hiddenProductIsTreatedAsUnknown() {
    assertBadRequest(
        request("UZB", new PricingItem(11L, 1, List.of())),
        "UNKNOWN_PRODUCT",
        "items[0].productId");
  }

  @Test
  void productWithoutRegionalPriceIsNotSold() {
    assertBadRequest(
        request("UZB", new PricingItem(12L, 1, List.of())),
        "PRODUCT_NOT_SOLD",
        "items[0].productId");
  }

  @Test
  void outOfStockProductIsBadRequest() {
    assertBadRequest(
        request("UZB", new PricingItem(2L, 1, List.of())), "OUT_OF_STOCK", "items[0].productId");
  }

  @Test
  void allProblemsAreReportedTogetherAndTheFirstDecidesTheCode() {
    assertBadRequest(
        request(
            "UZB",
            new PricingItem(5L, 1, List.of()),
            new PricingItem(2L, 1, List.of()),
            new PricingItem(99L, 1, List.of())),
        "OUT_OF_STOCK",
        "items[1].productId",
        "items[2].productId");
  }

  @Test
  void modifierOfAnotherProductIsRejected() {
    assertBadRequest(
        request("UZB", new PricingItem(5L, 1, List.of(101L))),
        "INVALID_MODIFIER",
        "items[0].modifierIds[0]");
  }

  @Test
  void hiddenUnknownOrUnpricedModifierIsRejected() {
    for (Long modifierId : List.of(102L, 103L, 999L)) {
      assertBadRequest(
          request("UZB", new PricingItem(5L, 1, List.of(modifierId))),
          "INVALID_MODIFIER",
          "items[0].modifierIds[0]");
    }
  }

  @Test
  void sameModifierTwiceIsRejected() {
    assertBadRequest(
        request("UZB", new PricingItem(5L, 1, List.of(100L, 100L))),
        "INVALID_MODIFIER",
        "items[0].modifierIds[1]");
  }

  @Test
  void quantityBelowOneIsRejected() {
    assertBadRequest(
        request("UZB", new PricingItem(5L, 0, List.of())), "VALIDATION_ERROR", "items[0].quantity");
  }

  @Test
  void unknownOrMissingCountryIsBadRequest() {
    assertBadRequest(
        request("XXX", new PricingItem(5L, 1, List.of())), "UNKNOWN_COUNTRY", "country");
    assertBadRequest(
        request(null, new PricingItem(5L, 1, List.of())), "UNKNOWN_COUNTRY", "country");
  }

  @Test
  void dishWithSizesNeedsExactlyOneSize() {
    // no modifier, two sizes, and an extra without a size
    for (List<Long> modifierIds : List.of(List.<Long>of(), List.of(110L, 111L), List.of(112L))) {
      assertBadRequest(
          request("UZB", new PricingItem(6L, 1, modifierIds)),
          "INVALID_MODIFIER",
          "items[0].modifierIds");
    }
  }

  @Test
  void sizeAndExtrasAreAddedToTheUnitPrice() {
    // rice 44.00 + Large 6.00 + Extra egg 3.00 = 53.00, x2 = 106.00; VAT 15.90; incl. VAT 121.90
    PriceBreakdown result =
        service.calculate(request("UZB", new PricingItem(6L, 2, List.of(111L, 112L))));

    PricedLine rice = result.lines().get(0);
    assertThat(rice.unitPrice()).isEqualByComparingTo("53.00");
    assertThat(rice.modifiers().get(0).type()).isEqualTo(ModifierType.SIZE);
    assertThat(rice.modifiers().get(1).type()).isEqualTo(ModifierType.EXTRA);
    assertThat(result.subtotal()).isEqualByComparingTo("106.00");
    assertThat(result.vat()).isEqualByComparingTo("15.90");
    assertThat(result.amountInclVat()).isEqualByComparingTo("121.90");
  }

  @Test
  void sizesThatCannotBeOrderedInTheCountryAreNotRequired() {
    // dish 7: its size has no price in UZB; dish 9: its size is hidden
    PriceBreakdown result =
        service.calculate(
            request("UZB", new PricingItem(7L, 1, List.of()), new PricingItem(9L, 1, List.of())));

    assertThat(result.subtotal()).isEqualByComparingTo("76.00");
  }

  @Test
  void quantityIsLimitedTo99() {
    PriceBreakdown ok = service.calculate(request("UZB", new PricingItem(5L, 99, List.of())));
    assertThat(ok.subtotal()).isEqualByComparingTo("4752.00");

    assertBadRequest(
        request("UZB", new PricingItem(5L, 100, List.of())),
        "VALIDATION_ERROR",
        "items[0].quantity");
  }

  @Test
  void cartIsLimitedTo50Lines() {
    PriceBreakdown ok =
        service.calculate(
            new PricingRequest("UZB", Collections.nCopies(50, new PricingItem(8L, 1, List.of()))));
    assertThat(ok.lines()).hasSize(50);
    assertThat(ok.subtotal()).isEqualByComparingTo("1600.00");

    assertBadRequest(
        new PricingRequest("UZB", Collections.nCopies(51, new PricingItem(8L, 1, List.of()))),
        "VALIDATION_ERROR",
        "items");
  }

  @Test
  void emptyCartIsBadRequest() {
    assertThatThrownBy(() -> service.calculate(request("UZB")))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(e.getFields()).containsOnlyKeys("items");
            });
  }

  @Test
  void countryWithoutVatRateIsAServerProblem() {
    assertThatThrownBy(() -> service.calculate(request("GEO", new PricingItem(5L, 1, List.of()))))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(e.getCode()).isEqualTo("PRICING_CONFIG_MISSING");
            });
  }

  @Test
  void pickupIsTheDefaultAndGetsTheDiscountWithoutDeliveryFee() {
    // amount incl. VAT 159.85 -> 3% = 4.80 (4.7955 rounded half-up); total 155.05
    PriceBreakdown result =
        service.calculate(
            request(
                "UZB", new PricingItem(5L, 2, List.of(100L)), new PricingItem(8L, 1, List.of())));

    assertThat(result.scenario()).isEqualTo(Scenario.PICKUP);
    assertThat(result.discountRate()).isEqualByComparingTo("3.00");
    assertThat(result.discount()).isEqualByComparingTo("4.80");
    assertThat(result.deliveryFee()).isEqualByComparingTo("0.00");
    assertThat(result.total()).isEqualByComparingTo("155.05");
  }

  @Test
  void deliveryAddsTheZoneFeeAfterTheDiscount() {
    PriceBreakdown result =
        service.calculate(
            delivery(
                "UZB",
                1L,
                new PricingItem(5L, 2, List.of(100L)),
                new PricingItem(8L, 1, List.of())));

    assertThat(result.scenario()).isEqualTo(Scenario.DELIVERY);
    assertThat(result.discount()).isEqualByComparingTo("4.80");
    assertThat(result.deliveryFee()).isEqualByComparingTo("20.00");
    assertThat(result.total()).isEqualByComparingTo("175.05");
  }

  @Test
  void smallCartHasNoDiscount() {
    // gyoza 32.00 + VAT 4.80 = 36.80, below 100: no discount; delivery 15.00 -> 51.80
    PriceBreakdown result =
        service.calculate(delivery("UZB", 2L, new PricingItem(8L, 1, List.of())));

    assertThat(result.discountRate()).isEqualByComparingTo("0.00");
    assertThat(result.discount()).isEqualByComparingTo("0.00");
    assertThat(result.total()).isEqualByComparingTo("51.80");
  }

  @Test
  void biggestTierIsUsedFor5000AndMore() {
    // 99 x 48.00 = 4752.00 + VAT 712.80 = 5464.80 -> 15% = 819.72 -> total 4645.08
    PriceBreakdown result = service.calculate(request("UZB", new PricingItem(5L, 99, List.of())));

    assertThat(result.amountInclVat()).isEqualByComparingTo("5464.80");
    assertThat(result.discountRate()).isEqualByComparingTo("15.00");
    assertThat(result.discount()).isEqualByComparingTo("819.72");
    assertThat(result.total()).isEqualByComparingTo("4645.08");
  }

  @Test
  void unknownOrForeignOrInactiveZoneIsRejected() {
    PricingItem item = new PricingItem(8L, 1, List.of());
    // 99: no such zone; 5: a zone of KAZ, not of UZB
    assertBadRequest(delivery("UZB", 99L, item), "UNKNOWN_DELIVERY_ZONE", "deliveryZoneId");
    assertBadRequest(delivery("UZB", 5L, item), "UNKNOWN_DELIVERY_ZONE", "deliveryZoneId");
  }

  @Test
  void deliveryNeedsAZoneAndPickupMustNotHaveOne() {
    PricingItem item = new PricingItem(8L, 1, List.of());
    assertBadRequest(delivery("UZB", null, item), "VALIDATION_ERROR", "deliveryZoneId");
    assertBadRequest(
        new PricingRequest("UZB", List.of(item), Scenario.PICKUP, 1L),
        "VALIDATION_ERROR",
        "deliveryZoneId");
  }

  @Test
  void zoneProblemAndCartProblemsAreReportedTogetherZoneFirst() {
    assertBadRequest(
        delivery("UZB", 99L, new PricingItem(2L, 1, List.of())),
        "UNKNOWN_DELIVERY_ZONE",
        "deliveryZoneId",
        "items[0].productId");
  }

  private static PricingRequest delivery(String country, Long zoneId, PricingItem... items) {
    return new PricingRequest(country, List.of(items), Scenario.DELIVERY, zoneId);
  }

  private void assertBadRequest(PricingRequest request, String code, String... fieldNames) {
    assertThatThrownBy(() -> service.calculate(request))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(e.getCode()).isEqualTo(code);
              assertThat(e.getFields()).containsOnlyKeys(fieldNames);
            });
  }

  private static PricingRequest request(String country, PricingItem... items) {
    return new PricingRequest(country, List.of(items));
  }
}
