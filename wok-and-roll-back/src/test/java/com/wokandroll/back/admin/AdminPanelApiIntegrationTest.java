package com.wokandroll.back.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.JwtService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true (PostgreSQL on localhost:5432). Each test is rolled back.
/**
 * The admin panel API (api-contract.md section 3.20, v1.11) on the seed data: the sales history of
 * Jun - Nov 2026 and the booking history of 26 Oct - 23 Nov 2026. The expected numbers are counted
 * with SQL, so the tests also hold when the seed grows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class AdminPanelApiIntegrationTest {

  private static final String[] ENDPOINTS = {
    "/api/v1/admin/overview?from=2026-11-01&to=2026-11-22",
    "/api/v1/admin/orders",
    "/api/v1/admin/orders/WR-00000000",
    "/api/v1/admin/bookings",
    "/api/v1/admin/users",
    "/api/v1/admin/cafes"
  };

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @Autowired private JdbcClient jdbc;

  private ResultActions asAdmin(String path) throws Exception {
    return mvc.perform(get(path).header("Authorization", bearer("ADMIN")));
  }

  private String bearer(String role) {
    return "Bearer "
        + jwt.createToken(new AuthUser(1, role.toLowerCase() + "@wokandroll.test", role));
  }

  private long count(String sql) {
    return jdbc.sql(sql).query(Long.class).single();
  }

  @Test
  void onlyAdminsOpenThePanel() throws Exception {
    for (String path : ENDPOINTS) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      for (String role : new String[] {"CUSTOMER", "STAFF", "VIEWER"}) {
        mvc.perform(get(path).header("Authorization", bearer(role)))
            .andExpect(status().isForbidden());
      }
    }
  }

  // ---- overview ------------------------------------------------------------------------------

  @Test
  void theOverviewCountsEveryOrderBookingAndAccountOfThePeriod() throws Exception {
    long orders =
        count(
            "SELECT count(*) FROM customer_order"
                + " WHERE order_date BETWEEN '2026-11-01' AND '2026-11-22'");
    long cancelled =
        count(
            "SELECT count(*) FROM customer_order WHERE status = 'CANCELLED'"
                + " AND order_date BETWEEN '2026-11-01' AND '2026-11-22'");
    long delivery =
        count(
            "SELECT count(*) FROM customer_order WHERE scenario = 'DELIVERY'"
                + " AND order_date BETWEEN '2026-11-01' AND '2026-11-22'");
    long bookings =
        count(
            "SELECT count(*) FROM booking WHERE booking_date BETWEEN '2026-11-01' AND '2026-11-22'");
    long accounts = count("SELECT count(*) FROM app_user");

    asAdmin("/api/v1/admin/overview?from=2026-11-01&to=2026-11-22")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-11-01"))
        .andExpect(jsonPath("$.to").value("2026-11-22"))
        .andExpect(jsonPath("$.generatedAt", notNullValue()))
        .andExpect(jsonPath("$.orders.total").value(orders))
        .andExpect(jsonPath("$.orders.byStatus.CANCELLED").value(cancelled))
        .andExpect(jsonPath("$.orders.delivery").value(delivery))
        .andExpect(jsonPath("$.orders.pickup").value(orders - delivery))
        .andExpect(jsonPath("$.bookings.total").value(bookings))
        .andExpect(jsonPath("$.accounts.total").value(accounts))
        .andExpect(jsonPath("$.accounts.byRole.ADMIN").value(1))
        .andExpect(jsonPath("$.regions", hasSize(7)))
        .andExpect(jsonPath("$.regions[0].country").value("UZB"))
        .andExpect(jsonPath("$.regions[0].currency").value("UZS"))
        // every day of the period, zeros too
        .andExpect(jsonPath("$.days", hasSize(22)))
        .andExpect(jsonPath("$.days[0].date").value("2026-11-01"))
        .andExpect(jsonPath("$.topDishes", hasSize(5)));
  }

  @Test
  void theStatusesAreAllFourInTheOrderOfTheirLife() throws Exception {
    String body =
        asAdmin("/api/v1/admin/overview?from=2026-11-01&to=2026-11-22")
            .andReturn()
            .getResponse()
            .getContentAsString();
    Map<String, Object> byStatus = JsonPath.read(body, "$.orders.byStatus");
    assertThat(byStatus.keySet()).containsExactly("NEW", "CONFIRMED", "DONE", "CANCELLED");
  }

  @Test
  void theRevenueIsTheSameAsInTheSalesReport() throws Exception {
    BigDecimal revenue =
        jdbc.sql(
                "SELECT coalesce(sum(o.total), 0) FROM customer_order o"
                    + " JOIN country c ON c.id = o.country_id"
                    + " WHERE c.code = 'GEO' AND o.status <> 'CANCELLED'"
                    + " AND o.order_date BETWEEN '2026-11-01' AND '2026-11-22'")
            .query(BigDecimal.class)
            .single();
    String overview =
        asAdmin("/api/v1/admin/overview?from=2026-11-01&to=2026-11-22")
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Double> geo = JsonPath.read(overview, "$.regions[?(@.country == 'GEO')].revenue");
    assertThat(BigDecimal.valueOf(geo.get(0))).isEqualByComparingTo(revenue);
  }

  @Test
  void theTopDishesGoFromTheMostOrdered() throws Exception {
    String body =
        asAdmin("/api/v1/admin/overview?from=2026-06-01&to=2026-11-22")
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Integer> quantities = JsonPath.read(body, "$.topDishes[*].quantity");
    assertThat(quantities).hasSize(5).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
  }

  @Test
  void theOverviewPeriodIsCheckedLikeTheReports() throws Exception {
    asAdmin("/api/v1/admin/overview?from=2026-11-22&to=2026-11-01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.to").exists());
    asAdmin("/api/v1/admin/overview?from=2025-01-01&to=2026-11-22")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    asAdmin("/api/v1/admin/overview?from=2026-11-01").andExpect(status().isBadRequest());
  }

  // ---- orders --------------------------------------------------------------------------------

  @Test
  void theOrdersComeNewestDayFirstInPages() throws Exception {
    String body =
        asAdmin("/api/v1/admin/orders?pageSize=5")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(5)))
            .andExpect(jsonPath("$.nextPageToken", notNullValue()))
            .andExpect(jsonPath("$.items[0].orderNumber", notNullValue()))
            .andExpect(jsonPath("$.items[0].cafe.name", notNullValue()))
            .andExpect(jsonPath("$.items[0].itemCount", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> days = JsonPath.read(body, "$.items[*].orderDate");
    assertThat(days).isSortedAccordingTo((a, b) -> b.compareTo(a));
  }

  @Test
  void theOrdersCanBeFilteredByCountryStatusAndDates() throws Exception {
    long expected =
        count(
            "SELECT count(*) FROM customer_order o JOIN country c ON c.id = o.country_id"
                + " WHERE c.code = 'GEO' AND o.status = 'CANCELLED'"
                + " AND o.order_date BETWEEN '2026-09-01' AND '2026-11-22'");
    asAdmin(
            "/api/v1/admin/orders?country=GEO&status=CANCELLED&from=2026-09-01&to=2026-11-22"
                + "&pageSize=100")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize((int) expected)))
        .andExpect(jsonPath("$.items[*].country", everyItem(is("GEO"))))
        .andExpect(jsonPath("$.items[*].currency", everyItem(is("GEL"))))
        .andExpect(jsonPath("$.items[*].status", everyItem(is("CANCELLED"))));
  }

  @Test
  void theOrdersOfOneCafe() throws Exception {
    asAdmin("/api/v1/admin/orders?cafeId=5&pageSize=20")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.items[*].cafe.id", everyItem(is(5))));
  }

  @Test
  void theSearchFindsAnOrderByNumberNameOrAccount() throws Exception {
    String number =
        jdbc.sql("SELECT order_number FROM customer_order ORDER BY id LIMIT 1")
            .query(String.class)
            .single();
    asAdmin("/api/v1/admin/orders?q=" + number.toLowerCase())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].orderNumber").value(number));

    long ofTheCustomer =
        count(
            "SELECT count(*) FROM customer_order o JOIN app_user u ON u.id = o.user_id"
                + " WHERE u.email = 'customer@wokandroll.test'");
    asAdmin("/api/v1/admin/orders?q=customer@wokandroll&pageSize=100")
        .andExpect(jsonPath("$.items", hasSize((int) Math.min(ofTheCustomer, 100))))
        .andExpect(jsonPath("$.items[0].account.email").value("customer@wokandroll.test"));

    // % and _ are text, not wildcards
    asAdmin("/api/v1/admin/orders?q=%25").andExpect(jsonPath("$.items", hasSize(0)));
  }

  @Test
  void aWrongOrderFilterIs400() throws Exception {
    asAdmin("/api/v1/admin/orders?status=LOST")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    asAdmin("/api/v1/admin/orders?country=FRA")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    asAdmin("/api/v1/admin/orders?from=2026-11-22&to=2026-11-01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.to").exists());
    asAdmin("/api/v1/admin/orders?q=" + "x".repeat(101))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    asAdmin("/api/v1/admin/orders?pageSize=101")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.pageSize").exists());
  }

  @Test
  void oneOrderHasItsLinesTotalsAndWhatOnlyTheAdminSees() throws Exception {
    String number =
        jdbc.sql(
                "SELECT o.order_number FROM customer_order o JOIN app_user u ON u.id = o.user_id"
                    + " WHERE u.email = 'customer@wokandroll.test' ORDER BY o.id LIMIT 1")
            .query(String.class)
            .single();
    asAdmin("/api/v1/admin/orders/" + number)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.orderNumber").value(number))
        .andExpect(jsonPath("$.order.lines", not(hasSize(0))))
        .andExpect(jsonPath("$.order.total", notNullValue()))
        .andExpect(jsonPath("$.account.email").value("customer@wokandroll.test"))
        .andExpect(jsonPath("$.updatedAt", notNullValue()));
  }

  @Test
  void aGuestOrderHasNoAccount() throws Exception {
    String number =
        jdbc.sql(
                "SELECT order_number FROM customer_order WHERE user_id IS NULL ORDER BY id LIMIT 1")
            .query(String.class)
            .single();
    asAdmin("/api/v1/admin/orders/" + number)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account", nullValue()));
  }

  @Test
  void anUnknownOrderIs404() throws Exception {
    asAdmin("/api/v1/admin/orders/WR-00000000")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // ---- bookings ------------------------------------------------------------------------------

  @Test
  void theBookingHistoryIsThereForEveryCafe() throws Exception {
    // the seed: 26 Oct - 23 Nov, all 14 cafés, 1 to 4 a café a day
    assertThat(
            count("SELECT count(DISTINCT cafe_id) FROM booking WHERE booking_date < '2026-11-24'"))
        .isEqualTo(count("SELECT count(*) FROM cafe WHERE active"));
    asAdmin("/api/v1/admin/bookings?from=2026-11-23&to=2026-11-23&pageSize=100")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.items",
                hasSize(
                    (int) count("SELECT count(*) FROM booking WHERE booking_date = '2026-11-23'"))))
        .andExpect(jsonPath("$.items[0].date").value("2026-11-23"))
        .andExpect(jsonPath("$.items[0].cafe.name", notNullValue()))
        .andExpect(jsonPath("$.items[0].tableSeats", notNullValue()));
  }

  @Test
  void theBookingsCanBeFilteredAndSearched() throws Exception {
    asAdmin("/api/v1/admin/bookings?country=KAZ&status=CANCELLED&pageSize=100")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].country", everyItem(is("KAZ"))))
        .andExpect(jsonPath("$.items[*].status", everyItem(is("CANCELLED"))));
    String number =
        jdbc.sql("SELECT booking_number FROM booking ORDER BY id LIMIT 1")
            .query(String.class)
            .single();
    asAdmin("/api/v1/admin/bookings?q=" + number)
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].bookingNumber").value(number));
    asAdmin("/api/v1/admin/bookings?status=NEW")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // ---- users and cafés -----------------------------------------------------------------------

  @Test
  void theAccountsHaveTheirRoleAndOrdersButNoPassword() throws Exception {
    long customerOrders =
        count(
            "SELECT count(*) FROM customer_order o JOIN app_user u ON u.id = o.user_id"
                + " WHERE u.email = 'customer@wokandroll.test'");
    asAdmin("/api/v1/admin/users?pageSize=100")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize((int) count("SELECT count(*) FROM app_user"))))
        .andExpect(jsonPath("$.items[*].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.items[*].password_hash").doesNotExist());
    asAdmin("/api/v1/admin/users?role=CUSTOMER&q=demo")
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].email").value("customer@wokandroll.test"))
        .andExpect(jsonPath("$.items[0].role").value("CUSTOMER"))
        .andExpect(jsonPath("$.items[0].orders").value(customerOrders))
        .andExpect(jsonPath("$.items[0].lastOrderAt", notNullValue()));
    asAdmin("/api/v1/admin/users?role=ADMIN")
        .andExpect(jsonPath("$.items[*].email", contains("admin@wokandroll.test")))
        .andExpect(jsonPath("$.items[0].orders").value(0))
        .andExpect(jsonPath("$.items[0].lastOrderAt", nullValue()));
    asAdmin("/api/v1/admin/users?role=OWNER").andExpect(status().isBadRequest());
  }

  @Test
  void everyCafeWithItsTablesAndItsDay() throws Exception {
    asAdmin("/api/v1/admin/cafes")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize((int) count("SELECT count(*) FROM cafe"))))
        .andExpect(jsonPath("$.items[0].country").value("UZB"))
        .andExpect(jsonPath("$.items[0].city").value("Tashkent"))
        .andExpect(jsonPath("$.items[0].timeZone").value("Asia/Tashkent"))
        .andExpect(jsonPath("$.items[0].tables").value(6))
        .andExpect(jsonPath("$.items[0].seats").value(26))
        .andExpect(jsonPath("$.items[0].opensAt").value("10:00"))
        .andExpect(jsonPath("$.items[0].today", notNullValue()))
        .andExpect(jsonPath("$.items[0].todayOrders", notNullValue()));
  }
}
