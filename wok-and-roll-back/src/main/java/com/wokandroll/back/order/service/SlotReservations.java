package com.wokandroll.back.order.service;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Takes one place of a 30-minute slot of a café (api-contract.md §3.8). It must run inside the
 * order transaction: if the order fails later, the place is free again.
 *
 * <p>TEMPORARY: the real slot service is #148 (SlotService). Until it is merged this port has a
 * small JDBC adapter with the SQL of the contract. When #148 is in dev, make its service implement
 * this port (or call it from the adapter) and delete {@code JdbcSlotReservations}.
 */
public interface SlotReservations {

  /** Returns true if a place was taken, false if the slot is full. */
  boolean reserve(long cafeId, LocalDate slotDate, LocalTime slotStart, int capacity);
}
