package com.wokandroll.back.catalog.service;

import com.wokandroll.back.catalog.dto.SetItemResponse;
import com.wokandroll.back.catalog.repository.CatalogRepository.DishText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a dish has, read from its name and ingredients (the menu data is in English): meat, fish or
 * seafood, spice. A set has what its dishes have. Nothing is known about a dish without
 * ingredients, so it is never called vegetarian. The same reading gives the marks of the menu cards
 * (vegetarian, spicy) and the words of Panko's menu, so they never disagree.
 */
public final class DishFacts {

  /** What one dish (or set) has. */
  public record Facts(boolean meat, boolean fish, boolean spicy) {

    /** Neither meat nor fish nor seafood. */
    public boolean vegetarian() {
      return !meat && !fish;
    }

    private Facts and(Facts other) {
      return new Facts(meat || other.meat, fish || other.fish, spicy || other.spicy);
    }
  }

  private static final Pattern MEAT =
      Pattern.compile(
          "\\b(chicken|beef|pork|duck|lamb|veal|turkey|bacon|ham|sausages?|meat|meatballs?)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern FISH =
      Pattern.compile(
          "\\b(fish|seafood|salmon|tuna|eel|crabs?|shrimps?|prawns?|squid|octopus|scallops?|"
              + "mussels?|oysters?|lobsters?|caviar|roe|cod|mackerel|yellowtail|hamachi|"
              + "anchov(y|ies)|bonito|dashi)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern SPICY =
      Pattern.compile(
          "\\b(chili|chilli|chilies|chillies|spicy|kimchi|firecracker|sriracha|jalape(n|ñ)os?)\\b",
          Pattern.CASE_INSENSITIVE);

  private DishFacts() {}

  /** What a dish with these ingredients has; empty when it has no ingredients to read. */
  public static Optional<Facts> ofDish(String name, String description) {
    if (description == null || description.isBlank()) {
      return Optional.empty();
    }
    String text = (name == null ? "" : name) + " " + description;
    return Optional.of(
        new Facts(
            MEAT.matcher(text).find(), FISH.matcher(text).find(), SPICY.matcher(text).find()));
  }

  /** A set has what its dishes have; empty when it has none or one of them is not known. */
  public static Optional<Facts> ofSet(List<Optional<Facts>> dishes) {
    if (dishes.isEmpty()) {
      return Optional.empty();
    }
    Facts all = new Facts(false, false, false);
    for (Optional<Facts> dish : dishes) {
      if (dish.isEmpty()) {
        return Optional.empty();
      }
      all = all.and(dish.get());
    }
    return Optional.of(all);
  }

  /**
   * What every dish and every set has, by id: a dish from its ingredients, a set from its dishes.
   * Ids with nothing known are left out.
   */
  public static Map<Long, Facts> of(
      Map<Long, DishText> dishes, Map<Long, List<SetItemResponse>> sets) {
    Map<Long, Facts> facts = new HashMap<>();
    dishes.forEach(
        (id, dish) -> ofDish(dish.name(), dish.description()).ifPresent(f -> facts.put(id, f)));
    sets.forEach(
        (id, items) -> {
          if (items.isEmpty()) {
            return; // a dish, not a set
          }
          List<Optional<Facts>> parts = new ArrayList<>();
          for (SetItemResponse item : items) {
            DishText dish = dishes.get(item.productId());
            parts.add(dish == null ? Optional.empty() : ofDish(dish.name(), dish.description()));
          }
          // A set is known by its dishes only, never by the words of its own description.
          facts.remove(id);
          ofSet(parts).ifPresent(f -> facts.put(id, f));
        });
    return facts;
  }
}
