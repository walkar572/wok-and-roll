package com.wokandroll.back.cart.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.cart.service.CartService;
import com.wokandroll.back.common.error.GlobalExceptionHandler;
import com.wokandroll.back.pricing.repository.FakePricingRepository;
import com.wokandroll.back.pricing.service.FakeDeliveryZoneFees;
import com.wokandroll.back.pricing.service.PriceCalculator;
import com.wokandroll.back.pricing.service.PricingService;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP level: JSON in, JSON out, status codes and the error format {code, message, fields}. */
class CartControllerTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    PricingService pricing =
        new PricingService(
            FakePricingRepository.sampleMenu(),
            new PriceCalculator(),
            FakeDeliveryZoneFees.sample());
    mvc =
        MockMvcBuilders.standaloneSetup(new CartController(new CartService(pricing)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void quoteReturnsSubtotalVatAndAmountInclVat() throws Exception {
    quote(
            """
            {"country": "UZB", "items": [
              {"productId": 5, "quantity": 2, "modifierIds": [100]},
              {"productId": 8, "quantity": 1}
            ]}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.country").value("UZB"))
        .andExpect(jsonPath("$.currency").value("UZS"))
        .andExpect(jsonPath("$.lines[0].productId").value(5))
        .andExpect(jsonPath("$.lines[0].name").value("Firecracker udon"))
        .andExpect(jsonPath("$.lines[0].quantity").value(2))
        .andExpect(jsonPath("$.lines[0].unitPrice").value(53.5))
        .andExpect(jsonPath("$.lines[0].lineTotal").value(107.0))
        .andExpect(jsonPath("$.lines[0].modifiers[0].name").value("Extra chili"))
        .andExpect(jsonPath("$.lines[0].modifiers[0].type").value("EXTRA"))
        .andExpect(jsonPath("$.lines[1].modifiers").isEmpty())
        .andExpect(jsonPath("$.subtotal").value(139.0))
        .andExpect(jsonPath("$.vatRate").value(15.0))
        .andExpect(jsonPath("$.vat").value(20.85))
        .andExpect(jsonPath("$.amountInclVat").value(159.85))
        .andExpect(jsonPath("$.scenario").value("PICKUP"))
        .andExpect(jsonPath("$.discountRate").value(3.0))
        .andExpect(jsonPath("$.discount").value(4.8))
        .andExpect(jsonPath("$.deliveryFee").value(0.0))
        .andExpect(jsonPath("$.total").value(155.05));
  }

  @Test
  void deliveryQuoteAddsTheZoneFee() throws Exception {
    quote(
            """
            {"country": "UZB", "scenario": "DELIVERY", "deliveryZoneId": 1, "items": [
              {"productId": 5, "quantity": 2, "modifierIds": [100]},
              {"productId": 8, "quantity": 1}
            ]}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scenario").value("DELIVERY"))
        .andExpect(content().string(containsString("\"deliveryFee\":20.00")))
        .andExpect(content().string(containsString("\"discount\":4.80")))
        .andExpect(content().string(containsString("\"total\":175.05")));
  }

  @Test
  void unknownDeliveryZoneReturns400() throws Exception {
    quote(
            "{\"country\": \"UZB\", \"scenario\": \"DELIVERY\", \"deliveryZoneId\": 99, "
                + "\"items\": [{\"productId\": 8, \"quantity\": 1}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_DELIVERY_ZONE"))
        .andExpect(jsonPath("$.fields.deliveryZoneId").exists());
  }

  @Test
  void deliveryWithoutAZoneAndPickupWithAZoneReturn400() throws Exception {
    quote(
            "{\"country\": \"UZB\", \"scenario\": \"DELIVERY\", "
                + "\"items\": [{\"productId\": 8, \"quantity\": 1}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.deliveryZoneId").exists());
    quote(
            "{\"country\": \"UZB\", \"deliveryZoneId\": 1, "
                + "\"items\": [{\"productId\": 8, \"quantity\": 1}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.deliveryZoneId").exists());
  }

  @Test
  void unknownScenarioReturns400() throws Exception {
    quote(
            "{\"country\": \"UZB\", \"scenario\": \"FLY\", "
                + "\"items\": [{\"productId\": 8, \"quantity\": 1}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.scenario").exists());
  }

  @Test
  void moneyIsSentWithTwoDecimals() throws Exception {
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 8, \"quantity\": 1}]}")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("\"subtotal\":32.00")))
        .andExpect(content().string(containsString("\"vatRate\":15.00")))
        .andExpect(content().string(containsString("\"vat\":4.80")))
        .andExpect(content().string(containsString("\"amountInclVat\":36.80")));
  }

  @Test
  void quoteShowsTheSizeAndItsType() throws Exception {
    quote(
            "{\"country\": \"UZB\", \"items\": [{\"productId\": 6, \"quantity\": 1, "
                + "\"modifierIds\": [111]}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].unitPrice").value(50.0))
        .andExpect(jsonPath("$.lines[0].modifiers[0].name").value("Large"))
        .andExpect(jsonPath("$.lines[0].modifiers[0].type").value("SIZE"))
        .andExpect(jsonPath("$.subtotal").value(50.0));
  }

  @Test
  void dishWithSizesWithoutASizeReturns400() throws Exception {
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 6, \"quantity\": 1}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MODIFIER"))
        .andExpect(jsonPath("$.fields['items[0].modifierIds']").exists());
  }

  @Test
  void quantityAbove99IsRejectedAnd99IsAccepted() throws Exception {
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 5, \"quantity\": 100}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields['items[0].quantity']").exists());
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 5, \"quantity\": 99}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal").value(4752.0));
  }

  @Test
  void moreThan50LinesAreRejected() throws Exception {
    String line = "{\"productId\": 8, \"quantity\": 1}";
    quote(
            "{\"country\": \"UZB\", \"items\": ["
                + String.join(",", Collections.nCopies(51, line))
                + "]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.items").exists());
    quote(
            "{\"country\": \"UZB\", \"items\": ["
                + String.join(",", Collections.nCopies(50, line))
                + "]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal").value(1600.0));
  }

  @Test
  void quantityBelowOneIsRejected() throws Exception {
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 5, \"quantity\": 0}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields['items[0].quantity']").exists());
  }

  @Test
  void fractionalQuantityIsRejected() throws Exception {
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 5, \"quantity\": 1.5}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields['items[0].quantity']").exists());
  }

  @Test
  void missingQuantityIsRejected() throws Exception {
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 5}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields['items[0].quantity']").exists());
  }

  @Test
  void emptyCartAndMissingCountryAreRejected() throws Exception {
    quote("{\"items\": []}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.country").exists())
        .andExpect(jsonPath("$.fields.items").exists());
  }

  @Test
  void unknownProductReturns400WithCodeMessageAndFields() throws Exception {
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 99, \"quantity\": 1}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.fields['items[0].productId']").exists());
  }

  @Test
  void outOfStockProductReturns400() throws Exception {
    quote("{\"country\": \"UZB\", \"items\": [{\"productId\": 2, \"quantity\": 1}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
        .andExpect(jsonPath("$.fields['items[0].productId']").exists());
  }

  @Test
  void unknownCountryReturns400() throws Exception {
    quote("{\"country\": \"XXX\", \"items\": [{\"productId\": 5, \"quantity\": 1}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_COUNTRY"))
        .andExpect(jsonPath("$.fields.country").exists());
  }

  private ResultActions quote(String json) throws Exception {
    return mvc.perform(
        post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(json));
  }
}
