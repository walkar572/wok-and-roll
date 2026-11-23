package com.wokandroll.back.account;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wokandroll.back.common.time.CountryClock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true (PostgreSQL on localhost:5432), like the other integration tests.
/**
 * {@code GET /api/v1/me/orders} and {@code /me/orders/{orderNumber}} (#177, api-contract.md section
 * 3.13): only the user's own orders, newest first, paging, and 404 for any other order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class MyOrdersApiIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private CountryClock clock;

  @Test
  void aNewUserHasNoOrders() throws Exception {
    String token = register();

    mvc.perform(get("/api/v1/me/orders").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.nextPageToken").value(nullValue()));
  }

  @Test
  void ordersAreListedNewestFirstWithPagingAndDetails() throws Exception {
    String token = register();
    String first = placeOrder(token, "12:00");
    String second = placeOrder(token, "12:30");

    String page1 =
        mvc.perform(get("/api/v1/me/orders?pageSize=1").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].orderNumber").value(second))
            .andExpect(jsonPath("$.items[0].status").value("NEW"))
            .andExpect(jsonPath("$.items[0].scenario").value("PICKUP"))
            .andExpect(jsonPath("$.items[0].itemCount").value(2))
            .andExpect(jsonPath("$.items[0].total").value(138.32))
            .andExpect(jsonPath("$.items[0].currency").value("UZS"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String next = JsonPath.read(page1, "$.nextPageToken");

    mvc.perform(
            get("/api/v1/me/orders?pageSize=1&pageToken=" + next)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].orderNumber").value(first))
        .andExpect(jsonPath("$.nextPageToken").value(nullValue()));

    mvc.perform(get("/api/v1/me/orders/" + first).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderNumber").value(first))
        .andExpect(jsonPath("$.lines[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$.total").value(138.32));
  }

  @Test
  void anotherUsersOrderIsNotFound() throws Exception {
    String owner = register();
    String other = register();
    String number = placeOrder(owner, "13:00");

    mvc.perform(get("/api/v1/me/orders/" + number).header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get("/api/v1/me/orders").header("Authorization", "Bearer " + other))
        .andExpect(jsonPath("$.items", hasSize(0)));
  }

  @Test
  void unknownNumberIsNotFoundAndGuestIsUnauthorized() throws Exception {
    String token = register();

    mvc.perform(get("/api/v1/me/orders/WR-00000000").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/me/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void badPagingIsAValidationError() throws Exception {
    String token = register();

    mvc.perform(get("/api/v1/me/orders?pageSize=0").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.pageSize").exists());
    mvc.perform(
            get("/api/v1/me/orders?pageToken=broken!").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.pageToken").exists());
  }

  private String register() throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "me-orders-%s@mail.test", "password": "secret123",
                         "fullName": "My Orders Test"}
                        """
                            .formatted(UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.token");
  }

  /** Two Salmon avocado rolls for pickup in Chilanzar tomorrow: total 138.32 UZS. */
  private String placeOrder(String token, String slot) throws Exception {
    String tomorrow = clock.today("UZB").plusDays(1).toString();
    String body =
        mvc.perform(
                post("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"country": "UZB", "orderDate": "%s", "scenario": "PICKUP", "cafeId": %d,
                         "slotStart": "%s", "contactName": "My Orders Test",
                         "contactPhone": "+998901234567", "expectedTotal": 138.32,
                         "items": [{"productId": 1, "quantity": 2, "modifierIds": []}]}
                        """
                            .formatted(tomorrow, 1, slot)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.orderNumber");
  }
}
