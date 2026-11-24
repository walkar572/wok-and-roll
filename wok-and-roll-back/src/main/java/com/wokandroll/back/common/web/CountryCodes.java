package com.wokandroll.back.common.web;

/** Shared rule for the country query parameter, so every endpoint gives the same message. */
public final class CountryCodes {

  public static final String PATTERN = "UZB|KAZ|GEO|UKR|CHN|GBR";
  public static final String MESSAGE = "must be one of UZB, KAZ, GEO, UKR, CHN, GBR";

  private CountryCodes() {}
}
