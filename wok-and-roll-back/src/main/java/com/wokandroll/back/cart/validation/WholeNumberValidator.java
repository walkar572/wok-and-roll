package com.wokandroll.back.cart.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/** Accepts 1, 2, 3 ... up to max and 2.0, rejects 0, -1, 1.5 and numbers above max. */
public class WholeNumberValidator implements ConstraintValidator<WholeNumber, BigDecimal> {

  private BigDecimal min = BigDecimal.ONE;
  private BigDecimal max = BigDecimal.ONE;

  @Override
  public void initialize(WholeNumber annotation) {
    min = BigDecimal.valueOf(annotation.min());
    max = BigDecimal.valueOf(annotation.max());
  }

  @Override
  public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
    if (value == null) {
      return true; // a missing value is reported by @NotNull
    }
    return value.compareTo(min) >= 0
        && value.compareTo(max) <= 0
        && value.stripTrailingZeros().scale() <= 0;
  }
}
