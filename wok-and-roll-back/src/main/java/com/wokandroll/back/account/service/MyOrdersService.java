package com.wokandroll.back.account.service;

import com.wokandroll.back.account.dto.OrderSummaryResponse;
import com.wokandroll.back.account.repository.MyOrdersRepository;
import com.wokandroll.back.common.error.NotFoundException;
import com.wokandroll.back.common.web.PageRequest;
import com.wokandroll.back.common.web.PageResponse;
import com.wokandroll.back.order.dto.OrderResponse;
import com.wokandroll.back.order.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** "My orders" of the logged-in user (api-contract.md section 3.13, #177). */
@Service
public class MyOrdersService {

  private final MyOrdersRepository myOrders;
  private final OrderRepository orders;

  public MyOrdersService(MyOrdersRepository myOrders, OrderRepository orders) {
    this.myOrders = myOrders;
    this.orders = orders;
  }

  public PageResponse<OrderSummaryResponse> list(long userId, PageRequest page) {
    // One row more than the page: it only tells that there is a next page.
    List<OrderSummaryResponse> rows =
        myOrders.findSummaries(userId, page.pageSize() + 1, page.offset());
    return PageResponse.of(rows, page);
  }

  /**
   * The order with all lines, the same body as POST /orders. An unknown number, a guest order and
   * an order of another user are all 404: the API never says that the order exists (OWASP API1).
   */
  public OrderResponse get(long userId, String orderNumber) {
    boolean mine =
        myOrders
            .findOwner(orderNumber)
            .flatMap(owner -> owner)
            .map(id -> id == userId)
            .orElse(false);
    Optional<OrderResponse> order = mine ? orders.findResponse(orderNumber) : Optional.empty();
    return order.orElseThrow(() -> new NotFoundException("Order " + orderNumber + " not found"));
  }
}
