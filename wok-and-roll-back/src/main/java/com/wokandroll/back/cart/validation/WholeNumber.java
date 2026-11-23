package com.wokandroll.back.cart.validation;

import com.wokandroll.back.pricing.service.PricingLimits;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The number must be a whole number from {@code min} to {@code max} (a quantity: 1 to 99). We read
 * quantity as a decimal on purpose: if it were an Integer, Jackson would silently turn 1.5 into 1.
 */
@Documented
@Constraint(validatedBy = WholeNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface WholeNumber {

  String message() default "must be a whole number from {min} to {max}";

  int min() default 1;

  int max() default PricingLimits.MAX_QUANTITY;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
