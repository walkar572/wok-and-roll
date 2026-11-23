package com.wokandroll.back.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.JwtService;
import com.wokandroll.back.common.time.CountryClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true (PostgreSQL on localhost:5432). Each test is rolled back.
/** The staff API (#176, api-contract.md section 3.14). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class StaffApiIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @Autowired private JdbcClient jdbc;
  @Autowired private CountryClock clock;

  private String bearer(String role) {
    return "Bearer " + jwt.createToken(new AuthUser(1, "staff@wokandroll.test", role));
  }

  @Test
  void staffSeeTheOrdersOfACafeAndMoveThemThroughTheStatuses() throws Exception {
    String tomorrow = clock.today("UZB").plusDays(1).toString();
    String number = placeOrder(tomorrow, "16:00");

    mvc.perform(
            get("/api/v1/staff/orders?cafeId=1&date=" + tomorrow)
                .header("Authorization", bearer("STAFF")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].orderNumber", hasItem(number)));

    changeStatus(number, "CONFIRMED", "STAFF").andExpect(status().isOk());
    changeStatus(number, "NEW", "STAFF")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATUS_CHANGE"));
    changeStatus(number, "DONE", "ADMIN")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
  }

  @Test
  void cancellingAnOrderGivesTheSlotPlaceBack() throws Exception {
    String tomorrow = clock.today("UZB").plusDays(1).toString();
    String number = placeOrder(tomorrow, "16:30");
    assertThat(reserved(tomorrow, "16:30")).isEqualTo(1);

    changeStatus(number, "CANCELLED", "STAFF").andExpect(status().isOk());

    assertThat(reserved(tomorrow, "16:30")).isZero();
  }

  @Test
  void customersAndViewersCannotUseTheStaffApi() throws Exception {
    for (String role : new String[] {"CUSTOMER", "VIEWER"}) {
      mvc.perform(
              get("/api/v1/staff/orders?cafeId=1&date=2026-11-20")
                  .header("Authorization", bearer(role)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            get("/api/v1/staff/bookings?cafeId=999&date=2026-11-20")
                .header("Authorization", bearer("STAFF")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_CAFE"));
  }

  private org.springframework.test.web.servlet.ResultActions changeStatus(
      String number, String status, String role) throws Exception {
    return mvc.perform(
        patch("/api/v1/staff/orders/" + number)
            .header("Authorization", bearer(role))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\": \"" + status + "\"}"));
  }

  private int reserved(String date, String slot) {
    return jdbc.sql(
            """
            SELECT coalesce(max(reserved), 0) FROM slot_reservation
            WHERE cafe_id = 1 AND slot_date = CAST(:date AS date) AND slot_start = CAST(:slot AS time)
            """)
        .param("date", date)
        .param("slot", slot)
        .query(Integer.class)
        .single();
  }

  private String placeOrder(String date, String slot) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"country": "UZB", "orderDate": "%s", "scenario": "PICKUP", "cafeId": 1,
                         "slotStart": "%s", "contactName": "Staff Test",
                         "contactPhone": "+998901234567", "expectedTotal": 138.32,
                         "items": [{"productId": 1, "quantity": 2, "modifierIds": []}]}
                        """
                            .formatted(date, slot)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.orderNumber");
  }
}
