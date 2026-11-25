package com.wokandroll.back.assistant;

import com.wokandroll.back.assistant.AssistantDtos.ChatMessage;
import com.wokandroll.back.assistant.AssistantDtos.ChatRequest;
import com.wokandroll.back.assistant.AssistantDtos.StatusResponse;
import com.wokandroll.back.assistant.ChatModel.Outcome;
import com.wokandroll.back.assistant.ChatModel.Turn;
import com.wokandroll.back.cafe.service.CafeService;
import com.wokandroll.back.catalog.detail.ProductDetailRepository;
import com.wokandroll.back.catalog.detail.dto.ModifierResponse;
import com.wokandroll.back.catalog.dto.ProductResponse;
import com.wokandroll.back.catalog.service.CatalogService;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.time.CountryClock;
import com.wokandroll.back.country.dto.CountryResponse;
import com.wokandroll.back.country.service.CountryService;
import com.wokandroll.back.delivery.service.DeliveryZoneService;
import com.wokandroll.back.pricing.dto.DiscountTier;
import com.wokandroll.back.pricing.repository.PricingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Panko, the AI chat (api-contract.md section 3.21). The server keeps no conversation: the browser
 * sends it every time, and only its metadata (country, language, length, time) is logged, never the
 * text.
 */
@Service
public class AssistantService {

  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

  /** At most this many dish cards under one answer. */
  static final int MAX_DISHES = 4;

  static final String UNAVAILABLE = "ASSISTANT_UNAVAILABLE";

  private final ChatModel model;
  private final AssistantRateLimiter limiter;
  private final CountryService countries;
  private final CatalogService catalog;
  private final ProductDetailRepository details;
  private final CafeService cafes;
  private final DeliveryZoneService zones;
  private final PricingRepository pricing;
  private final CountryClock clock;

  public AssistantService(
      ChatModel model,
      AssistantRateLimiter limiter,
      CountryService countries,
      CatalogService catalog,
      ProductDetailRepository details,
      CafeService cafes,
      DeliveryZoneService zones,
      PricingRepository pricing,
      CountryClock clock) {
    this.model = model;
    this.limiter = limiter;
    this.countries = countries;
    this.catalog = catalog;
    this.details = details;
    this.cafes = cafes;
    this.zones = zones;
    this.pricing = pricing;
    this.clock = clock;
  }

  /** A question that passed every check, with its prompt, ready to be answered. */
  public record Chat(
      String country,
      String language,
      String instructions,
      String context,
      List<Turn> turns,
      Map<Long, ProductResponse> products) {}

  /** How an answer ended: the dish cards to show under it. */
  public record Answer(List<ProductResponse> dishes, boolean refused) {}

  public StatusResponse status() {
    return new StatusResponse(model.available(), model.model());
  }

  /**
   * Checks a question before the answer starts streaming: 503 without an API key, 400 when the
   * conversation is not valid, 429 when this IP asked too often.
   */
  public Chat prepare(ChatRequest request, String ip) {
    if (!model.available()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, "The AI chat is not switched on here");
    }
    List<Turn> turns = turns(request.messages());
    long wait = limiter.tryAcquire(ip);
    if (wait > 0) {
      throw new TooManyMessagesException(wait);
    }

    CountryResponse country =
        countries.getCountries().stream()
            .filter(c -> c.code().equals(request.country()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "UNKNOWN_COUNTRY",
                        "Unknown country code",
                        Map.of("country", "Unknown country code")));
    String code = country.code();
    List<ProductResponse> products = catalog.getProducts(code, null);
    Map<Long, List<ModifierResponse>> modifiers = new LinkedHashMap<>();
    for (ProductResponse product : products) {
      if (product.hasModifiers()) {
        modifiers.put(product.id(), details.findModifiers(product.id(), code));
      }
    }
    List<DiscountTier> discounts =
        pricing.findCountry(code).map(c -> pricing.findDiscountTiers(c.id())).orElse(List.of());
    AssistantPrompt.MenuData data =
        new AssistantPrompt.MenuData(
            country,
            catalog.getCategories(code),
            products,
            modifiers,
            cafes.getCafes(code).items(),
            zones.getZones(code).items(),
            discounts);

    // Only a city of the country goes into the prompt, never free text from the request.
    String city =
        request.city() != null && country.cities().contains(request.city()) ? request.city() : null;
    Map<Long, ProductResponse> byId = new LinkedHashMap<>();
    products.forEach(product -> byId.put(product.id(), product));
    return new Chat(
        code,
        request.language(),
        AssistantPrompt.instructions(data),
        AssistantPrompt.context(request.language(), city, country.name(), clock.now(code)),
        turns,
        byId);
  }

  /**
   * Streams the answer: every piece of text without the dish markers goes to {@code onText}; the
   * dishes of the markers (on the menu, in stock, at most 4) come back at the end.
   */
  public Answer answer(Chat chat, Consumer<String> onText) {
    long started = System.nanoTime();
    MarkerFilter filter = new MarkerFilter();
    int[] shown = {0};
    Consumer<String> show =
        text -> {
          if (!text.isEmpty()) {
            shown[0] += text.length();
            onText.accept(text);
          }
        };
    Outcome outcome =
        model.stream(
            chat.instructions(),
            chat.context(),
            chat.turns(),
            piece -> show.accept(filter.feed(piece)));
    show.accept(filter.flush());

    List<ProductResponse> dishes =
        filter.ids().stream()
            .map(chat.products()::get)
            .filter(product -> product != null && product.inStock())
            .limit(MAX_DISHES)
            .toList();
    log.info(
        "Assistant: {} {}, {} messages, {} chars, {} dishes, stop {}, {} ms",
        chat.country(),
        chat.language(),
        chat.turns().size(),
        shown[0],
        dishes.size(),
        outcome.stopReason(),
        (System.nanoTime() - started) / 1_000_000);
    return new Answer(dishes, outcome.refused());
  }

  /**
   * The conversation for the model: a greeting of the assistant before the first question is
   * dropped, messages of the same side in a row are joined, and the last one must be the guest's.
   */
  static List<Turn> turns(List<ChatMessage> messages) {
    Map<String, String> problems = new LinkedHashMap<>();
    for (int i = 0; i < messages.size(); i++) {
      ChatMessage message = messages.get(i);
      if (message.role().equals("user") && message.text().length() > AssistantDtos.MAX_USER_TEXT) {
        problems.put(
            "messages[" + i + "].text", "size must be at most " + AssistantDtos.MAX_USER_TEXT);
      }
    }
    if (!messages.get(messages.size() - 1).role().equals("user")) {
      problems.put("messages", "the last message must be from the user");
    }
    if (!problems.isEmpty()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", problems);
    }

    List<Turn> turns = new ArrayList<>();
    for (ChatMessage message : messages) {
      boolean fromGuest = message.role().equals("user");
      String text = message.text().strip();
      if (turns.isEmpty() && !fromGuest) {
        continue;
      }
      if (!turns.isEmpty() && turns.get(turns.size() - 1).fromGuest() == fromGuest) {
        Turn last = turns.remove(turns.size() - 1);
        turns.add(new Turn(fromGuest, last.text() + "\n\n" + text));
      } else {
        turns.add(new Turn(fromGuest, text));
      }
    }
    return turns;
  }

  /** 20 questions in 10 minutes from one IP. The controller adds the header Retry-After. */
  public static class TooManyMessagesException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyMessagesException(long retryAfterSeconds) {
      super(
          HttpStatus.TOO_MANY_REQUESTS,
          "TOO_MANY_MESSAGES",
          "Too many questions. Try again in "
              + Math.max(1, (retryAfterSeconds + 59) / 60)
              + " min");
      this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
      return retryAfterSeconds;
    }
  }
}
