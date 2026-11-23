package com.wokandroll.back.order.service;

/** Size limits of an order request (api-contract.md §3.9). */
public final class OrderLimits {

  /** The order date can be today or up to this many days ahead. */
  public static final int MAX_DAYS_AHEAD = 7;

  /** A slot is this many minutes long. */
  public static final int SLOT_MINUTES = 30;

  /** A slot that starts in less than this many minutes cannot be booked. */
  public static final int MIN_LEAD_MINUTES = 30;

  private OrderLimits() {}
}
