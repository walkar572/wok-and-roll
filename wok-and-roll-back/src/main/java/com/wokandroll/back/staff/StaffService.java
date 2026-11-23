package com.wokandroll.back.staff;

import com.wokandroll.back.cafe.repository.CafeRepository;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.error.NotFoundException;
import com.wokandroll.back.common.web.PageRequest;
import com.wokandroll.back.common.web.PageResponse;
import com.wokandroll.back.order.dto.OrderResponse;
import com.wokandroll.back.order.repository.OrderRepository;
import com.wokandroll.back.slot.service.SlotService;
import com.wokandroll.back.staff.StaffDtos.StaffBookingResponse;
import com.wokandroll.back.staff.StaffDtos.StaffOrderResponse;
import com.wokandroll.back.staff.StaffRepository.OrderState;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The café work list for STAFF and ADMIN (api-contract.md section 3.14, #176). Staff see all cafés:
 * the MVP has no link between a user and a café.
 */
@Service
public class StaffService {

  /** Allowed status changes: NEW → CONFIRMED → DONE, NEW or CONFIRMED → CANCELLED. */
  static final Map<String, Set<String>> NEXT =
      Map.of(
          "NEW", Set.of("CONFIRMED", "CANCELLED"),
          "CONFIRMED", Set.of("DONE", "CANCELLED"),
          "DONE", Set.of(),
          "CANCELLED", Set.of());

  private final StaffRepository staff;
  private final CafeRepository cafes;
  private final OrderRepository orders;
  private final SlotService slots;

  public StaffService(
      StaffRepository staff, CafeRepository cafes, OrderRepository orders, SlotService slots) {
    this.staff = staff;
    this.cafes = cafes;
    this.orders = orders;
    this.slots = slots;
  }

  public PageResponse<StaffOrderResponse> orders(long cafeId, LocalDate date, PageRequest page) {
    checkCafe(cafeId);
    return PageResponse.of(
        staff.findOrders(cafeId, date, page.pageSize() + 1, page.offset()), page);
  }

  public List<StaffBookingResponse> bookings(long cafeId, LocalDate date) {
    checkCafe(cafeId);
    return staff.findBookings(cafeId, date);
  }

  /** Changes the status in one transaction; a cancel gives the slot place back. */
  @Transactional
  public OrderResponse changeStatus(String orderNumber, String status) {
    OrderState state =
        staff
            .lockOrder(orderNumber)
            .orElseThrow(() -> new NotFoundException("Order " + orderNumber + " not found"));
    String next = status == null ? "" : status.trim();
    if (!NEXT.containsKey(next)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "Request validation failed",
          Map.of("status", "must be one of CONFIRMED, DONE, CANCELLED"));
    }
    if (!NEXT.getOrDefault(state.status(), Set.of()).contains(next)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVALID_STATUS_CHANGE",
          "An order cannot go from " + state.status() + " to " + next,
          Map.of("status", "An order cannot go from " + state.status() + " to " + next));
    }
    staff.setStatus(orderNumber, next);
    if ("CANCELLED".equals(next)) {
      slots.release(state.cafeId(), state.orderDate(), state.slotStart());
    }
    return orders.findResponse(orderNumber).orElseThrow();
  }

  private void checkCafe(long cafeId) {
    if (cafes.findActive(cafeId).isEmpty()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "UNKNOWN_CAFE",
          "Unknown café",
          Map.of("cafeId", "Café " + cafeId + " does not exist or is closed"));
    }
  }
}
