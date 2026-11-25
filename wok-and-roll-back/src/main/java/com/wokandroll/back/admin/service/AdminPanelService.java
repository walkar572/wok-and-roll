package com.wokandroll.back.admin.service;

import com.wokandroll.back.admin.dto.AdminPanelDtos.AccountCounts;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminBookingSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminCafeListResponse;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminOrderResponse;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminOrderSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminUserSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.OrderCounts;
import com.wokandroll.back.admin.dto.AdminPanelDtos.OverviewResponse;
import com.wokandroll.back.admin.dto.AdminPanelDtos.RegionRevenue;
import com.wokandroll.back.admin.repository.AdminPanelRepository;
import com.wokandroll.back.admin.repository.AdminPanelRepository.ListFilter;
import com.wokandroll.back.admin.repository.AdminPanelRepository.OrderExtras;
import com.wokandroll.back.admin.repository.AdminPanelRepository.ScenarioCounts;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.error.NotFoundException;
import com.wokandroll.back.common.web.PageRequest;
import com.wokandroll.back.common.web.PageResponse;
import com.wokandroll.back.order.dto.OrderResponse;
import com.wokandroll.back.order.repository.OrderRepository;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import com.wokandroll.back.report.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The admin panel: everything the shop knows, read only (api-contract.md section 3.20, v1.11).
 * Changes go through the APIs that already own them: order status through the staff API (section
 * 3.14), the menu through the admin catalog API (section 3.15).
 */
@Service
public class AdminPanelService {

  /** How many dishes the overview lists. */
  static final int TOP_DISHES = 5;

  private final AdminPanelRepository panel;
  private final OrderRepository orders;
  private final ReportService reports;

  public AdminPanelService(
      AdminPanelRepository panel, OrderRepository orders, ReportService reports) {
    this.panel = panel;
    this.orders = orders;
    this.reports = reports;
  }

  /**
   * The numbers of a period. The money comes from the sales-by-region report, so the panel and the
   * reports page always show the same revenue; that call also checks the period (at most 366 days,
   * from not after to).
   */
  public OverviewResponse overview(LocalDate from, LocalDate to) {
    SalesByRegionResponse sales = reports.salesByRegion(from, to);
    Map<String, Long> byStatus = panel.ordersByStatus(from, to);
    ScenarioCounts scenarios = panel.ordersByScenario(from, to);
    Map<String, Long> roles = panel.accountsByRole();
    List<RegionRevenue> regions =
        sales.regions().stream()
            .map(
                r ->
                    new RegionRevenue(
                        r.country(), r.currency(), r.orders(), r.total(), r.averageCheck()))
            .toList();
    return new OverviewResponse(
        from,
        to,
        sales.generatedAt(),
        new OrderCounts(sum(byStatus), byStatus, scenarios.pickup(), scenarios.delivery()),
        panel.bookingCounts(from, to),
        new AccountCounts(sum(roles), panel.newAccounts(from, to), roles),
        regions,
        panel.ordersByDay(from, to),
        panel.topDishes(from, to, TOP_DISHES));
  }

  public PageResponse<AdminOrderSummary> orders(ListFilter filter, PageRequest page) {
    checkDates(filter);
    return PageResponse.of(panel.findOrders(filter, page.pageSize() + 1, page.offset()), page);
  }

  public AdminOrderResponse order(String orderNumber) {
    OrderResponse order =
        orders
            .findResponse(orderNumber)
            .orElseThrow(() -> new NotFoundException("Order " + orderNumber + " not found"));
    OrderExtras extras = panel.findOrderExtras(orderNumber).orElseThrow();
    return new AdminOrderResponse(
        order, extras.contactEmail(), extras.comment(), extras.account(), extras.updatedAt());
  }

  public PageResponse<AdminBookingSummary> bookings(ListFilter filter, PageRequest page) {
    checkDates(filter);
    return PageResponse.of(panel.findBookings(filter, page.pageSize() + 1, page.offset()), page);
  }

  public PageResponse<AdminUserSummary> users(String role, String query, PageRequest page) {
    return PageResponse.of(panel.findUsers(role, query, page.pageSize() + 1, page.offset()), page);
  }

  public AdminCafeListResponse cafes() {
    return new AdminCafeListResponse(panel.findCafes());
  }

  /** The search text: trimmed, and null when there is nothing to look for. */
  public static String query(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }

  private static void checkDates(ListFilter filter) {
    if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "Request validation failed",
          Map.of("to", "must be on or after from"));
    }
  }

  private static long sum(Map<String, Long> counts) {
    return counts.values().stream().mapToLong(Long::longValue).sum();
  }
}
