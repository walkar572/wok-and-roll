package com.wokandroll.back.pricing.repository;

import com.wokandroll.back.pricing.dto.DiscountTier;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Reads the data that the pricing service needs. Nothing is written here. */
public interface PricingRepository {

  /** Finds a country by its 3-letter code (UZB, KAZ, ...). */
  Optional<CountryPricing> findCountry(String code);

  /** Products with their price in the country. Ids that do not exist are not in the result. */
  List<ProductPrice> findProducts(long countryId, Collection<Long> productIds);

  /** Modifiers with their price delta in the country. Unknown ids are not in the result. */
  List<ModifierPrice> findModifiers(long countryId, Collection<Long> modifierIds);

  /**
   * Ids of the products that have at least one size to choose in the country: a visible modifier of
   * type SIZE with a price there. Such a dish needs exactly one size in the cart.
   */
  List<Long> findProductIdsWithSizes(long countryId, Collection<Long> productIds);

  /** Discount tiers of the country (local currency), in any order. */
  List<DiscountTier> findDiscountTiers(long countryId);
}
