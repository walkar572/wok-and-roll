package com.wokandroll.back.order.service;

import com.wokandroll.back.cart.dto.CartLineResponse;
import com.wokandroll.back.cart.dto.CartModifierResponse;
import com.wokandroll.back.cart.dto.CartQuoteResponse;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.order.dto.OrderRequest;
import com.wokandroll.back.order.repository.NewOrder;
import com.wokandroll.back.order.repository.OrderRepository;
import com.wokandroll.back.order.repository.OrderRepository.SavedOrder;
import com.wokandroll.back.pricing.dto.PriceBreakdown;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Places an order (#149, api-contract.md §3.9). One database transaction: validate, price with the
 * same service as the quote, compare {@code expectedTotal}, take a place in the slot, insert the
 * order with its lines and modifiers. If anything fails, nothing is saved and the place is free.
 *
 * <p>Idempotency: the same key and the same request again returns the first order (a replay); the
 * same key with another request is {@code IDEMPOTENCY_KEY_REUSED}.
 */
@Service
public class OrderService {

  static final String SLOT_FULL = "SLOT_FULL";
  static final String PRICE_CHANGED = "PRICE_CHANGED";
  static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";

  /** A new number is drawn when the number is taken. 32^8 numbers: one retry is already rare. */
  private static final int MAX_NUMBER_ATTEMPTS = 5;

  private final OrderValidator validator;
  private final OrderRepository repository;
  private final SlotReservations slots;
  private final OrderNumberGenerator numbers;
  private final RequestHasher hasher;
  private final TransactionTemplate transaction;

  public OrderService(
      OrderValidator validator,
      OrderRepository repository,
      SlotReservations slots,
      OrderNumberGenerator numbers,
      RequestHasher hasher,
      PlatformTransactionManager transactionManager) {
    this.validator = validator;
    this.repository = repository;
    this.slots = slots;
    this.numbers = numbers;
    this.hasher = hasher;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /**
   * @param key the Idempotency-Key header, or null
   * @param userId the id of the logged-in user, or null for a guest
   */
  public OrderResult place(OrderRequest request, UUID key, Long userId) {
    String hash = hasher.hash(request);
    if (key != null) {
      Optional<OrderResult> replay = replay(key, hash);
      if (replay.isPresent()) {
        return replay.get();
      }
    }
    try {
      return transaction.execute(status -> create(request, key, userId, hash));
    } catch (SameKeyInProgress e) {
      // Another request with this key was saved a moment ago. Our transaction is rolled back.
      return replay(key, hash).orElseThrow(() -> e);
    }
  }

  private Optional<OrderResult> replay(UUID key, String hash) {
    Optional<SavedOrder> saved = repository.findByKey(key);
    if (saved.isEmpty()) {
      return Optional.empty();
    }
    if (!saved.get().requestHash().equals(hash)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          IDEMPOTENCY_KEY_REUSED,
          "This Idempotency-Key was already used with a different request");
    }
    return repository.findResponse(saved.get().orderNumber()).map(o -> new OrderResult(o, true));
  }

  private OrderResult create(OrderRequest request, UUID key, Long userId, String hash) {
    ValidOrder order = validator.validate(request);
    PriceBreakdown price = order.price();
    if (request.expectedTotal() != null && request.expectedTotal().compareTo(price.total()) != 0) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          PRICE_CHANGED,
          "The price has changed: the total is now " + price.total().toPlainString());
    }

    boolean reserved =
        slots.reserve(
            order.cafe().id(), order.orderDate(), order.slotStart(), order.cafe().slotCapacity());
    if (!reserved) {
      if (key != null && repository.findByKey(key).isPresent()) {
        throw new SameKeyInProgress(); // the last place was taken by this very request
      }
      throw new ApiException(
          HttpStatus.CONFLICT, SLOT_FULL, "This time slot is full, choose another one");
    }

    for (int attempt = 0; attempt < MAX_NUMBER_ATTEMPTS; attempt++) {
      String number = numbers.next();
      Optional<Long> id = repository.insertOrder(toNewOrder(order, number, key, userId, hash));
      if (id.isPresent()) {
        saveLines(id.get(), price);
        OrderResult result =
            repository
                .findResponse(number)
                .map(o -> new OrderResult(o, false))
                .orElseThrow(() -> new IllegalStateException("Saved order not found: " + number));
        return result;
      }
      // Nothing was inserted: the key or the number is taken.
      if (key != null && repository.findByKey(key).isPresent()) {
        throw new SameKeyInProgress();
      }
    }
    throw new IllegalStateException("Could not find a free order number");
  }

  private void saveLines(long orderId, PriceBreakdown price) {
    for (CartLineResponse line : CartQuoteResponse.from(price).lines()) {
      long itemId = repository.insertItem(orderId, line);
      for (CartModifierResponse modifier : line.modifiers()) {
        repository.insertModifier(itemId, modifier);
      }
    }
  }

  private static NewOrder toNewOrder(
      ValidOrder o, String number, UUID key, Long userId, String hash) {
    PriceBreakdown p = o.price();
    return new NewOrder(
        number,
        userId,
        o.countryCode(),
        o.orderDate(),
        o.scenario(),
        o.cafe().id(),
        o.deliveryZoneId(),
        o.slotStart(),
        o.slotEnd(),
        o.deliveryAddress(),
        o.contactName(),
        o.contactPhone(),
        o.contactEmail(),
        o.comment(),
        p.currency(),
        p.subtotal(),
        p.vatRate(),
        p.vat(),
        p.amountInclVat(),
        p.discountRate(),
        p.discount(),
        p.deliveryFee(),
        p.total(),
        key,
        hash);
  }

  /** Inside the transaction: the request with this key is already saved. Rolls the work back. */
  private static final class SameKeyInProgress extends RuntimeException {
    SameKeyInProgress() {
      super("Another request with the same Idempotency-Key was saved first", null, false, false);
    }
  }
}
