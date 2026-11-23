package com.wokandroll.back.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.report.dto.RegionSales;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import com.wokandroll.back.report.repository.FakeSalesReportRepository;
import com.wokandroll.back.report.repository.FakeSalesReportRepository.Order;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

  private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
  private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);
  private static final Instant NOW = Instant.parse("2026-11-20T09:15:00.123456Z");

  private final FakeSalesReportRepository orders = new FakeSalesReportRepository();
  private final ReportService service = new ReportService(orders, Clock.fixed(NOW, ZoneOffset.UTC));

  private static Order uzb(String day, String status, String total) {
    return new Order(
        "UZB", LocalDate.parse(day), status, "100.00", "15.00", "5.00", "10.00", total);
  }

  private static RegionSales region(SalesByRegionResponse r, String country) {
    return r.regions().stream().filter(x -> x.country().equals(country)).findFirst().orElseThrow();
  }

  @Test
  void allFiveCountriesInOrderWithZerosWhenThereAreNoOrders() {
    SalesByRegionResponse r = service.salesByRegion(NOV_1, NOV_30);

    assertThat(r.regions())
        .extracting(RegionSales::country)
        .containsExactly("UZB", "KAZ", "GEO", "UKR", "CHN");
    assertThat(r.totalOrders()).isZero();
    RegionSales china = region(r, "CHN");
    assertThat(china.currency()).isEqualTo("CNY");
    assertThat(china.orders()).isZero();
    assertThat(china.total()).isEqualByComparingTo("0").hasToString("0.00");
    assertThat(china.averageCheck()).hasToString("0.00");
  }

  @Test
  void sumsTheSavedValuesAndSkipsCancelledAndOtherDays() {
    orders
        .add(uzb("2026-11-01", "NEW", "120.00")) // first day is included
        .add(uzb("2026-11-30", "DONE", "120.00")) // last day is included
        .add(uzb("2026-11-15", "CONFIRMED", "120.00"))
        .add(uzb("2026-11-15", "CANCELLED", "999.00")) // not a sale
        .add(uzb("2026-10-31", "DONE", "999.00")) // before the period
        .add(uzb("2026-12-01", "DONE", "999.00")) // after the period
        .add(new Order("KAZ", NOV_1, "NEW", "50.00", "6.00", "0.00", "0.00", "56.00"));

    SalesByRegionResponse r = service.salesByRegion(NOV_1, NOV_30);

    RegionSales uz = region(r, "UZB");
    assertThat(uz.orders()).isEqualTo(3);
    assertThat(uz.subtotal()).hasToString("300.00");
    assertThat(uz.vat()).hasToString("45.00");
    assertThat(uz.discount()).hasToString("15.00");
    assertThat(uz.deliveryFee()).hasToString("30.00");
    assertThat(uz.total()).hasToString("360.00");
    assertThat(uz.averageCheck()).hasToString("120.00");
    assertThat(region(r, "KAZ").total()).hasToString("56.00");
    assertThat(r.totalOrders()).isEqualTo(4);
  }

  @Test
  void averageCheckIsRoundedHalfUp() {
    assertThat(ReportService.averageCheck(new BigDecimal("100.00"), 3)).hasToString("33.33");
    assertThat(ReportService.averageCheck(new BigDecimal("0.05"), 2)).hasToString("0.03");
    assertThat(ReportService.averageCheck(new BigDecimal("59240.65"), 120)).hasToString("493.67");
    assertThat(ReportService.averageCheck(BigDecimal.ZERO, 0)).hasToString("0.00");
  }

  @Test
  void keepsThePeriodAndTheTimeInUtcSeconds() {
    SalesByRegionResponse r = service.salesByRegion(NOV_1, NOV_30);
    assertThat(r.from()).isEqualTo(NOV_1);
    assertThat(r.to()).isEqualTo(NOV_30);
    assertThat(r.generatedAt()).isEqualTo(Instant.parse("2026-11-20T09:15:00Z"));
  }

  @Test
  void fromAfterToIs400() {
    assertThatThrownBy(() -> service.salesByRegion(NOV_30, NOV_1))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus().value()).isEqualTo(400);
              assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
              assertThat(e.getFields()).containsKey("to");
            });
  }

  @Test
  void oneDayAndExactly366DaysAreFineButNot367() {
    assertThat(service.salesByRegion(NOV_1, NOV_1).regions()).hasSize(5);
    LocalDate from = LocalDate.of(2027, 1, 1);
    assertThat(service.salesByRegion(from, from.plusDays(365)).regions()).hasSize(5);
    assertThatThrownBy(() -> service.salesByRegion(from, from.plusDays(366)))
        .isInstanceOf(ApiException.class);
  }
}
