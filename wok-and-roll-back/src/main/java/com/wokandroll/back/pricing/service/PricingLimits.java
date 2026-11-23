package com.wokandroll.back.pricing.service;

/** Size limits of one cart. Checked in the request validation and again in the pricing service. */
public final class PricingLimits {

  /** The biggest quantity of one line. */
  public static final int MAX_QUANTITY = 99;

  /** The biggest number of lines in one cart. */
  public static final int MAX_LINES = 50;

  private PricingLimits() {}
}
