package com.wokandroll.back.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true: GitHub Actions sets it and has PostgreSQL 16 on localhost:5432.
// Locally: start the database, then run CI=true mvn verify. Each test is rolled back.
/**
 * GET /api/v1/reports/sales-by-month on the real customer_order table (#144). The test orders are
 * for Georgia in 2024, a year with no other data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class SalesByMonthApiIntegrationTest {

  private static final String GEO_2024 = "/api/v1/reports/sales-by-month?country=GEO&year=2024";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private JwtService jwt;

  private int number;

  private String viewer() {
    return "Bearer " + jwt.createToken(new AuthUser(1, "viewer@wokandroll.test", "VIEWER"));
  }

  private void order(String day, String status, String total) {
    jdbc.sql(
            """
            INSERT INTO customer_order (
                order_number, country_id, order_date, scenario, cafe_id, slot_start, slot_end,
                contact_name, contact_phone, currency_code, subtotal, vat_rate, vat_amount,
                amount_incl_vat, discount_rate, discount_amount, delivery_fee, total, status,
                request_hash)
            SELECT :number, c.id, :day, 'PICKUP',
                   (SELECT min(cf.id) FROM cafe cf WHERE cf.country_id = c.id),
                   TIME '12:00', TIME '12:30', 'Report Test', '+995500000000', c.currency_code,
                   100.00, 18.00, 18.00, 118.00, 0, 0.00, 0.00, :total, :status, repeat('b', 64)
            FROM country c
            WHERE c.code = 'GEO'
            """)
        .param("number", "WR-T154-" + (++number))
        .param("day", LocalDate.parse(day))
        .param("total", new BigDecimal(total))
        .param("status", status)
        .update();
  }

  @Test
  void twelveMonthsWithZerosForAYearWithoutOrders() throws Exception {
    mvc.perform(get(GEO_2024).header("Authorization", viewer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.country").value("GEO"))
        .andExpect(jsonPath("$.countryName").value("Georgia"))
        .andExpect(jsonPath("$.currency").value("GEL"))
        .andExpect(jsonPath("$.months.length()").value(12))
        .andExpect(jsonPath("$.months[0].month").value(1))
        .andExpect(jsonPath("$.months[11].month").value(12))
        .andExpect(jsonPath("$.months[5].total").value(0.00))
        .andExpect(jsonPath("$.totals.orders").value(0));
  }

  @Test
  void ordersAreCountedInTheMonthOfTheirDate() throws Exception {
    order("2024-01-01", "NEW", "118.00");
    order("2024-01-31", "DONE", "118.00");
    order("2024-02-29", "CONFIRMED", "100.00"); // leap day
    order("2024-12-31", "DONE", "50.00");
    order("2024-03-10", "CANCELLED", "999.00");
    order("2025-01-01", "DONE", "999.00");

    mvc.perform(get(GEO_2024).header("Authorization", viewer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.months[0].orders").value(2))
        .andExpect(jsonPath("$.months[0].total").value(236.00))
        .andExpect(jsonPath("$.months[0].averageCheck").value(118.00))
        .andExpect(jsonPath("$.months[1].orders").value(1))
        .andExpect(jsonPath("$.months[2].orders").value(0))
        .andExpect(jsonPath("$.months[11].total").value(50.00))
        .andExpect(jsonPath("$.totals.orders").value(4))
        .andExpect(jsonPath("$.totals.total").value(386.00))
        .andExpect(jsonPath("$.totals.averageCheck").value(96.50));
  }
}
