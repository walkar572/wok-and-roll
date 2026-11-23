package com.wokandroll.back.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The contract (§3.9) and the checkout page use "12:30", not "12:30:00". */
class OrderJsonTest {

  private final ObjectMapper mapper =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  @Test
  void responseWritesTheSlotAsHoursAndMinutes() {
    OrderResponse response =
        new OrderResponse(
            "WR-7K3M9Q2X",
            "NEW",
            Instant.parse("2026-11-18T09:15:00Z"),
            "UZB",
            "UZS",
            LocalDate.of(2026, 11, 20),
            LocalTime.of(12, 30),
            LocalTime.of(13, 0),
            "PICKUP",
            new OrderResponse.CafeSummary(1L, "Wok & Roll Chilanzar", "12 Bunyodkor Ave"),
            null,
            null,
            "Aziz Karimov",
            "+998901234567",
            List.of(),
            new BigDecimal("170.00"),
            new BigDecimal("15.00"),
            new BigDecimal("25.50"),
            new BigDecimal("195.50"),
            new BigDecimal("3.00"),
            new BigDecimal("5.87"),
            new BigDecimal("0.00"),
            new BigDecimal("189.63"));

    JsonNode json = mapper.valueToTree(response);

    assertThat(json.get("slotStart").asText()).isEqualTo("12:30");
    assertThat(json.get("slotEnd").asText()).isEqualTo("13:00");
    assertThat(json.get("orderDate").asText()).isEqualTo("2026-11-20");
  }

  @Test
  void requestKeepsTheDateAndTheSlotAsText() throws Exception {
    OrderRequest request =
        mapper.readValue(
            """
            {"country": "UZB", "orderDate": "2026-11-20", "scenario": "PICKUP",
             "cafeId": 1, "slotStart": "12:30", "contactName": "Aziz Karimov",
             "contactPhone": "+998901234567", "items": []}
            """,
            OrderRequest.class);

    // Text on purpose: a wrong format is a field problem of the validation, not an unreadable body.
    assertThat(request.orderDate()).isEqualTo("2026-11-20");
    assertThat(request.slotStart()).isEqualTo("12:30");
    assertThat(mapper.valueToTree(request).get("slotStart").asText()).isEqualTo("12:30");
  }
}
