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
 * GET /api/v1/reports/sales-by-region on the real customer_order table (#144, #219). The orders are
 * in March 2031, so no other data falls into the period.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class SalesByRegionApiIntegrationTest {

  private static final String MARCH_2031 =
      "/api/v1/reports/sales-by-region?from=2031-03-01&to=2031-03-31";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private JwtService jwt;

  private int number;

  private String viewer() {
    return "Bearer " + jwt.createToken(new AuthUser(1, "viewer@wokandroll.test", "VIEWER"));
  }

  /** A pickup order at the first café of the country, with the money values given. */
  private void order(
      String country,
      String day,
      String status,
      String subtotal,
      String vat,
      String discount,
      String fee,
      String total) {
    jdbc.sql(
            """
            INSERT INTO customer_order (
                order_number, country_id, order_date, scenario, cafe_id, slot_start, slot_end,
                contact_name, contact_phone, currency_code, subtotal, vat_rate, vat_amount,
                amount_incl_vat, discount_rate, discount_amount, delivery_fee, total, status,
                request_hash)
            SELECT :number, c.id, :day, 'PICKUP',
                   (SELECT min(cf.id) FROM cafe cf WHERE cf.country_id = c.id),
                   TIME '12:00', TIME '12:30', 'Report Test', '+998900000000', c.currency_code,
                   :subtotal, 15.00, :vat, :inclVat, 0, :discount, :fee, :total, :status,
                   repeat('a', 64)
            FROM country c
            WHERE c.code = :country
            """)
        .param("number", "WR-T153-" + (++number))
        .param("day", LocalDate.parse(day))
        .param("subtotal", new BigDecimal(subtotal))
        .param("vat", new BigDecimal(vat))
        .param("inclVat", new BigDecimal(subtotal).add(new BigDecimal(vat)))
        .param("discount", new BigDecimal(discount))
        .param("fee", new BigDecimal(fee))
        .param("total", new BigDecimal(total))
        .param("status", status)
        .param("country", country)
        .update();
  }

  @Test
  void aPeriodWithoutOrdersGivesEveryCountryWithZeros() throws Exception {
    mvc.perform(get(MARCH_2031).header("Authorization", viewer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalOrders").value(0))
        .andExpect(jsonPath("$.regions.length()").value(6))
        .andExpect(jsonPath("$.regions[0].country").value("UZB"))
        .andExpect(jsonPath("$.regions[0].countryName").value("Uzbekistan"))
        .andExpect(jsonPath("$.regions[0].currency").value("UZS"))
        .andExpect(jsonPath("$.regions[4].country").value("CHN"))
        .andExpect(jsonPath("$.regions[4].currency").value("CNY"))
        .andExpect(jsonPath("$.regions[4].orders").value(0))
        .andExpect(jsonPath("$.regions[4].total").value(0.00))
        .andExpect(jsonPath("$.regions[4].averageCheck").value(0.00))
        .andExpect(jsonPath("$.regions[5].country").value("GBR"))
        .andExpect(jsonPath("$.regions[5].currency").value("GBP"))
        .andExpect(jsonPath("$.regions[5].orders").value(0));
  }

  @Test
  void sumsTheSavedValuesOfSalesInThePeriod() throws Exception {
    order("UZB", "2031-03-01", "NEW", "100.00", "15.00", "5.00", "10.00", "120.00");
    order("UZB", "2031-03-31", "DONE", "200.00", "30.00", "10.00", "0.00", "220.00");
    order("UZB", "2031-03-15", "CANCELLED", "999.00", "0.00", "0.00", "0.00", "999.00");
    order("UZB", "2031-04-01", "DONE", "999.00", "0.00", "0.00", "0.00", "999.00");
    order("KAZ", "2031-03-10", "CONFIRMED", "50.00", "6.00", "0.00", "0.00", "56.00");

    mvc.perform(get(MARCH_2031).header("Authorization", viewer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalOrders").value(3))
        .andExpect(jsonPath("$.regions[0].country").value("UZB"))
        .andExpect(jsonPath("$.regions[0].orders").value(2))
        .andExpect(jsonPath("$.regions[0].subtotal").value(300.00))
        .andExpect(jsonPath("$.regions[0].vat").value(45.00))
        .andExpect(jsonPath("$.regions[0].discount").value(15.00))
        .andExpect(jsonPath("$.regions[0].deliveryFee").value(10.00))
        .andExpect(jsonPath("$.regions[0].total").value(340.00))
        .andExpect(jsonPath("$.regions[0].averageCheck").value(170.00))
        .andExpect(jsonPath("$.regions[1].country").value("KAZ"))
        .andExpect(jsonPath("$.regions[1].orders").value(1))
        .andExpect(jsonPath("$.regions[1].total").value(56.00))
        .andExpect(jsonPath("$.regions[2].orders").value(0));
  }

  @Test
  void customerCannotSeeTheReport() throws Exception {
    String customer =
        "Bearer " + jwt.createToken(new AuthUser(2, "customer@wokandroll.test", "CUSTOMER"));
    mvc.perform(get(MARCH_2031).header("Authorization", customer))
        .andExpect(status().isForbidden());
  }
}
