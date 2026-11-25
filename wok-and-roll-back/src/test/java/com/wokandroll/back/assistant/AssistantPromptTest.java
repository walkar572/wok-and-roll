package com.wokandroll.back.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.wokandroll.back.cafe.dto.CafeResponse;
import com.wokandroll.back.catalog.detail.dto.ModifierResponse;
import com.wokandroll.back.catalog.dto.CategoryResponse;
import com.wokandroll.back.catalog.dto.ProductResponse;
import com.wokandroll.back.catalog.dto.SetItemResponse;
import com.wokandroll.back.country.dto.CountryResponse;
import com.wokandroll.back.delivery.dto.DeliveryZoneResponse;
import com.wokandroll.back.pricing.dto.DiscountTier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantPromptTest {

  private static ProductResponse product(
      long id,
      long categoryId,
      String name,
      String description,
      String price,
      boolean inStock,
      String regularPrice,
      List<SetItemResponse> setItems) {
    return new ProductResponse(
        id,
        categoryId,
        name,
        description,
        "/images/x.png",
        setItems.isEmpty() ? 8 : 24,
        250,
        null,
        new BigDecimal(price),
        "UZS",
        inStock,
        id == 1,
        regularPrice == null ? null : new BigDecimal(regularPrice),
        setItems);
  }

  private static final AssistantPrompt.MenuData DATA =
      new AssistantPrompt.MenuData(
          new CountryResponse(
              "UZB", "Uzbekistan", "UZS", new BigDecimal("12.00"), "Asia/Tashkent", List.of()),
          List.of(
              new CategoryResponse(1, "Rolls"),
              new CategoryResponse(2, "Sets"),
              new CategoryResponse(3, "Empty")),
          List.of(
              product(
                  1, 1, "Philadelphia", "Salmon, cream cheese.", "65000", true, null, List.of()),
              product(2, 1, "Dragon roll", "Eel, avocado.", "72000.5", false, null, List.of()),
              product(
                  3,
                  2,
                  "Big set",
                  "Ignored for a set",
                  "180000",
                  true,
                  "209000",
                  List.of(
                      new SetItemResponse(1, "Philadelphia", 2),
                      new SetItemResponse(2, "Dragon roll", 1)))),
          Map.of(1L, List.of(new ModifierResponse(4, "SIZE", "Large", new BigDecimal("15000")))),
          List.of(
              new CafeResponse(
                  1,
                  "UZB",
                  "Wok & Roll Chilanzar",
                  "Tashkent",
                  "12 Bunyodkor Ave",
                  "+998712000001",
                  "10:00",
                  "22:00",
                  "Asia/Tashkent",
                  41.0,
                  69.0)),
          List.of(
              new DeliveryZoneResponse(
                  1, "UZB", "Tashkent: Center", 1, new BigDecimal("15000"), "UZS", 45)),
          List.of(new DiscountTier(new BigDecimal("300000"), new BigDecimal("5.00"))));

  @Test
  void listsTheMenuWithIdsPricesPortionsAndOptions() {
    String text = AssistantPrompt.instructions(DATA);

    assertThat(text)
        .contains("You are Panko")
        .contains("# Menu of Uzbekistan, prices in UZS before VAT")
        .contains("## Rolls")
        .contains(
            "- [id 1] Philadelphia: 65000 UZS; 8 pcs, 250 g; Salmon, cream cheese.; options:"
                + " Large (+15000, size)")
        .contains("- [id 2] Dragon roll: 72000.50 UZS; 8 pcs, 250 g; Eel, avocado.; OUT OF STOCK")
        .contains(
            "- [id 3] Big set: 180000 UZS (209000 for its dishes one by one, saves 13%); 24"
                + " pcs, 250 g; contains 2 x Philadelphia, Dragon roll")
        .doesNotContain("Ignored for a set")
        .doesNotContain("## Empty");
  }

  @Test
  void listsCafesZonesVatAndDiscounts() {
    String text = AssistantPrompt.instructions(DATA);

    assertThat(text)
        .contains(
            "- Wok & Roll Chilanzar, Tashkent: 12 Bunyodkor Ave; open every day 10:00-22:00 local"
                + " time; phone +998712000001")
        .contains("- Tashkent: Center: fee 15000 UZS, about 45 min")
        .contains("- VAT 12% is added at checkout.")
        .contains("from 300000 UZS 5%");
  }

  @Test
  void theContextNamesTheLanguageTheCityAndTheTime() {
    String text =
        AssistantPrompt.context(
            "kk", "Tashkent", "Uzbekistan", LocalDateTime.parse("2026-11-25T19:05:00"));

    assertThat(text)
        .contains("Answer in Kazakh (Cyrillic script)")
        .contains("Tashkent, Uzbekistan")
        .contains("Wednesday 25 November 2026, 19:05");
    assertThat(AssistantPrompt.context("en-US", null, "Uzbekistan", LocalDateTime.now()))
        .contains("American English")
        .contains("prices of Uzbekistan.");
  }

  @Test
  void theCachedPartIsTheSameForTheSameData() {
    assertThat(AssistantPrompt.instructions(DATA)).isEqualTo(AssistantPrompt.instructions(DATA));
  }
}
