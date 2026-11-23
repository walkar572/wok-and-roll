package com.wokandroll.back.booking.service;

/** The numbers of the booking rules (api-contract.md section 3.10), in one place. */
public final class BookingRules {

  /** A booking takes the table for this long. */
  public static final int DURATION_MINUTES = 90;

  /** Booking times are on this step: 18:00, 18:30, … */
  public static final int STEP_MINUTES = 30;

  /** Today a booking must start at least this long from now. */
  public static final int MIN_LEAD_MINUTES = 60;

  /** The guest can book from today to this many days ahead. */
  public static final int MAX_DAYS_AHEAD = 30;

  public static final int MIN_GUESTS = 1;
  public static final int MAX_GUESTS = 8;

  private BookingRules() {}
}
