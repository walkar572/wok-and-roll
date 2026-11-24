package com.wokandroll.back.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.report.dto.MonthSales;
import com.wokandroll.back.report.dto.SalesByMonthResponse;
import com.wokandroll.back.report.repository.FakeSalesReportRepository;
import com.wokandroll.back.report.repository.FakeSalesReportRepository.Order;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SalesByMonthServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-20T09:15:00.5Z");

  private final FakeSalesReportRepository orders = new FakeSalesReportRepository();
  private final ReportService service = new ReportService(orders, Clock.fixed(NOW, ZoneOffset.UTC));

  private static Order order(String country, String day, String status, String total) {
    return new Order(
        country, LocalDate.parse(day), status, "100.00", "15.00", "5.00", "10.00", total);
  }

  private static MonthSales month(SalesByMonthResponse r, int month) {
    return r.months().get(month - 1);
  }

  @Test
  void twelveMonthsWithZerosAndTheCountryHeader() {
    SalesByMonthResponse r = service.salesByMonth("GEO", 2026);

    assertThat(r.country()).isEqualTo("GEO");
    assertThat(r.countryName()).isEqualTo("Georgia");
    assertThat(r.currency()).isEqualTo("GEL");
    assertThat(r.year()).isEqualTo(2026);
    assertThat(r.generatedAt()).isEqualTo(Instant.parse("2026-11-20T09:15:00Z"));
    assertThat(r.months())
        .extracting(MonthSales::month)
        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    assertThat(r.months()).allSatisfy(m -> assertThat(m.total()).hasToString("0.00"));
    assertThat(r.totals().orders()).isZero();
    assertThat(r.totals().averageCheck()).hasToString("0.00");
  }

  @Test
  void ordersGoToTheMonthOfTheirOrderDate() {
    orders
        .add(order("UZB", "2026-01-01", "NEW", "120.00")) // first day of the year
        .add(order("UZB", "2026-01-31", "DONE", "100.00"))
        .add(order("UZB", "2026-02-01", "CONFIRMED", "80.00"))
        .add(order("UZB", "2026-12-31", "DONE", "50.00")) // last day of the year
        .add(order("UZB", "2026-02-10", "CANCELLED", "999.00")) // not a sale
        .add(order("UZB", "2025-12-31", "DONE", "999.00")) // another year
        .add(order("UZB", "2027-01-01", "DONE", "999.00")) // another year
        .add(order("KAZ", "2026-01-15", "DONE", "999.00")); // another country

    SalesByMonthResponse r = service.salesByMonth("UZB", 2026);

    assertThat(month(r, 1).orders()).isEqualTo(2);
    assertThat(month(r, 1).total()).hasToString("220.00");
    assertThat(month(r, 1).averageCheck()).hasToString("110.00");
    assertThat(month(r, 1).subtotal()).hasToString("200.00");
    assertThat(month(r, 2).orders()).isEqualTo(1);
    assertThat(month(r, 2).total()).hasToString("80.00");
    assertThat(month(r, 3).orders()).isZero();
    assertThat(month(r, 12).total()).hasToString("50.00");
  }

  @Test
  void totalsAreTheSumOfTheMonthsWithAHalfUpAverage() {
    orders
        .add(order("UZB", "2026-03-01", "NEW", "100.00"))
        .add(order("UZB", "2026-04-01", "NEW", "100.00"))
        .add(order("UZB", "2026-05-01", "NEW", "100.01"));

    SalesByMonthResponse r = service.salesByMonth("UZB", 2026);

    assertThat(r.totals().orders()).isEqualTo(3);
    assertThat(r.totals().subtotal()).hasToString("300.00");
    assertThat(r.totals().vat()).hasToString("45.00");
    assertThat(r.totals().discount()).hasToString("15.00");
    assertThat(r.totals().deliveryFee()).hasToString("30.00");
    assertThat(r.totals().total()).hasToString("300.01");
    assertThat(r.totals().averageCheck()).hasToString("100.00"); // 100.0033 rounds down
  }

  @Test
  void yearMustBeFrom2024ToNextYear() {
    assertThat(service.salesByMonth("UZB", 2024).months()).hasSize(12);
    assertThat(service.salesByMonth("UZB", 2027).months()).hasSize(12);
    for (int year : new int[] {2023, 2028}) {
      assertThatThrownBy(() -> service.salesByMonth("UZB", year))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> {
                assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
                assertThat(e.getFields()).containsKey("year");
              });
    }
  }

  @Test
  void unknownCountryIs400() {
    assertThatThrownBy(() -> service.salesByMonth("FRA", 2026))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getFields()).containsKey("country"));
  }
}
