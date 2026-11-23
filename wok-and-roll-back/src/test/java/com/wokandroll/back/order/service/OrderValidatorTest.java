package com.wokandroll.back.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wokandroll.back.cart.dto.CartItemRequest;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.time.CountryClock;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository.DeliveryZoneRow;
import com.wokandroll.back.order.dto.OrderRequest;
import com.wokandroll.back.order.repository.CafeRow;
import com.wokandroll.back.order.repository.OrderRepository;
import com.wokandroll.back.pricing.dto.PriceBreakdown;
import com.wokandroll.back.pricing.dto.PricingRequest;
import com.wokandroll.back.pricing.dto.Scenario;
import com.wokandroll.back.pricing.service.PricingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * Every rule of the order validation (#150, api-contract.md §3.9). The clock, the repositories and
 * the pricing service are mocks, so the tests need no database. "Now" is 2026-11-19 12:10 in UZB.
 */
class OrderValidatorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
  private static final String TOMORROW = "2026-11-20";

  private CountryClock clock;
  private DeliveryZoneRepository zones;
  private OrderRepository orders;
  private PricingService pricing;
  private OrderValidator validator;

  @BeforeEach
  void setUp() {
    clock = mock(CountryClock.class);
    zones = mock(DeliveryZoneRepository.class);
    orders = mock(OrderRepository.class);
    pricing = mock(PricingService.class);
    validator = new OrderValidator(clock, zones, orders, pricing);

    when(clock.zone("UZB")).thenReturn(ZoneId.of("Asia/Tashkent"));
    when(clock.zone("XXX")).thenThrow(new IllegalArgumentException("Unknown country: XXX"));
    when(clock.today("UZB")).thenReturn(TODAY);
    when(clock.now("UZB")).thenReturn(LocalDateTime.of(2026, 11, 19, 12, 10));

    // Café 1 is open 10:00-22:00, so the slots are 10:00 ... 21:30. Zone 1 is cooked in café 1.
    CafeRow cafe =
        new CafeRow(
            1,
            "Wok & Roll Chilanzar",
            "12 Bunyodkor Ave",
            LocalTime.of(10, 0),
            LocalTime.of(22, 0),
            5);
    when(orders.findActiveCafe("UZB", 1L)).thenReturn(Optional.of(cafe));
    when(zones.findActive("UZB", 1L))
        .thenReturn(
            Optional.of(
                new DeliveryZoneRow(
                    1, "UZB", "Tashkent: Center", 1, new BigDecimal("20.00"), "UZS", 45)));
    when(pricing.calculate(any(PricingRequest.class))).thenReturn(price());
  }

  // ---- a good request ----------------------------------------------------------------------

  @Test
  void aGoodPickupRequestIsCleanedAndPriced() {
    OrderRequest request =
        pickup()
            .contactName("  Aziz Karimov ")
            .contactEmail(" Aziz@Mail.uz ")
            .comment("   ")
            .build();

    ValidOrder order = validator.validate(request);

    assertThat(order.countryCode()).isEqualTo("UZB");
    assertThat(order.scenario()).isEqualTo("PICKUP");
    assertThat(order.cafe().id()).isEqualTo(1);
    assertThat(order.deliveryZoneId()).isNull();
    assertThat(order.deliveryAddress()).isNull();
    assertThat(order.slotStart()).isEqualTo(LocalTime.of(12, 30));
    assertThat(order.slotEnd()).isEqualTo(LocalTime.of(13, 0));
    assertThat(order.contactName()).isEqualTo("Aziz Karimov");
    assertThat(order.contactEmail()).isEqualTo("aziz@mail.uz");
    assertThat(order.comment()).isNull();
    assertThat(order.price()).isNotNull();
  }

  @Test
  void aGoodDeliveryRequestTakesTheCafeOfTheZone() {
    ValidOrder order = validator.validate(delivery().deliveryAddress(" Amir Temur 15 ").build());

    assertThat(order.scenario()).isEqualTo("DELIVERY");
    assertThat(order.cafe().id()).isEqualTo(1);
    assertThat(order.deliveryZoneId()).isEqualTo(1);
    assertThat(order.deliveryAddress()).isEqualTo("Amir Temur 15");
  }

  @Test
  void theCountryIsTrimmedAndUpperCased() {
    assertThat(validator.validate(pickup().country(" uzb ").build()).countryCode())
        .isEqualTo("UZB");
  }

  // ---- country and scenario ----------------------------------------------------------------

  @Test
  void anUnknownCountryIsUnknownCountry() {
    ApiException e = rejected(pickup().country("XXX").build());

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getCode()).isEqualTo("UNKNOWN_COUNTRY");
    assertThat(e.getFields()).containsKey("country");
  }

  @Test
  void aMissingCountryIsAValidationError() {
    ApiException e = rejected(pickup().country(null).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("country");
  }

  @ParameterizedTest
  @ValueSource(strings = {"TAKEAWAY", "pickup", "Delivery", " "})
  void aWrongScenarioIsAValidationError(String scenario) {
    ApiException e = rejected(pickup().scenario(scenario).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("scenario");
  }

  @Test
  void aMissingScenarioIsAValidationError() {
    assertThat(rejected(pickup().scenario(null).build()).getFields()).containsKey("scenario");
  }

  // ---- café and zone -----------------------------------------------------------------------

  @Test
  void pickupNeedsACafe() {
    ApiException e = rejected(pickup().cafeId(null).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("cafeId");
  }

  @Test
  void anUnknownCafeIsUnknownCafe() {
    ApiException e = rejected(pickup().cafeId(999L).build());

    assertThat(e.getCode()).isEqualTo("UNKNOWN_CAFE");
    assertThat(e.getFields()).containsKey("cafeId");
  }

  @Test
  void pickupMustNotHaveAZoneOrAnAddress() {
    ApiException e = rejected(pickup().deliveryZoneId(1L).deliveryAddress("Amir Temur 15").build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields())
        .containsEntry("deliveryZoneId", "must be empty for pickup")
        .containsEntry("deliveryAddress", "must be empty for pickup");
  }

  @Test
  void deliveryNeedsAZone() {
    ApiException e = rejected(delivery().deliveryZoneId(null).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("deliveryZoneId");
  }

  @Test
  void anUnknownZoneIsUnknownDeliveryZone() {
    ApiException e = rejected(delivery().deliveryZoneId(999L).build());

    assertThat(e.getCode()).isEqualTo("UNKNOWN_DELIVERY_ZONE");
    assertThat(e.getFields()).containsKey("deliveryZoneId");
  }

  @Test
  void deliveryMustNotHaveACafe() {
    ApiException e = rejected(delivery().cafeId(1L).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsEntry("cafeId", "must be empty for delivery");
  }

  // ---- date --------------------------------------------------------------------------------

  @Test
  void aDateBeforeTodayIsPastDate() {
    ApiException e = rejected(pickup().orderDate("2026-11-18").build());

    assertThat(e.getCode()).isEqualTo("PAST_DATE");
    assertThat(e.getFields()).containsKey("orderDate");
  }

  @Test
  void todayAndSevenDaysAheadAreAllowed() {
    // later today is fine for a slot that starts in 30 minutes or more
    assertThat(validator.validate(pickup().orderDate("2026-11-19").slotStart("13:00").build()))
        .isNotNull();
    assertThat(validator.validate(pickup().orderDate("2026-11-26").build())).isNotNull();
  }

  @Test
  void moreThanSevenDaysAheadIsAValidationError() {
    ApiException e = rejected(pickup().orderDate("2026-11-27").build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("orderDate");
  }

  @ParameterizedTest
  @ValueSource(strings = {"20-11-2026", "2026-13-45", "2026-11-31", "tomorrow", "2026-11-2"})
  void aWrongDateFormatIsAValidationError(String date) {
    ApiException e = rejected(pickup().orderDate(date).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("orderDate");
  }

  @Test
  void aMissingDateIsAValidationError() {
    assertThat(rejected(pickup().orderDate(null).build()).getFields()).containsKey("orderDate");
  }

  // ---- slot --------------------------------------------------------------------------------

  @Test
  void theFirstAndTheLastSlotOfTheDayAreAllowed() {
    assertThat(validator.validate(pickup().slotStart("10:00").build())).isNotNull();
    assertThat(validator.validate(pickup().slotStart("21:30").build())).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"12:15", "09:30", "22:00", "22:30", "00:00"})
  void aTimeThatIsNotASlotOfTheCafeIsInvalidSlot(String slot) {
    ApiException e = rejected(pickup().slotStart(slot).build());

    assertThat(e.getCode()).isEqualTo("INVALID_SLOT");
    assertThat(e.getFields()).containsKey("slotStart");
  }

  @Test
  void aSlotThatStartsInLessThanThirtyMinutesIsInvalidSlot() {
    // now is 12:10: 12:30 starts in 20 minutes, 12:00 has started
    for (String slot : new String[] {"12:00", "12:30"}) {
      ApiException e = rejected(pickup().orderDate("2026-11-19").slotStart(slot).build());

      assertThat(e.getCode()).isEqualTo("INVALID_SLOT");
      assertThat(e.getFields().get("slotStart")).contains("30 minutes");
    }
  }

  @Test
  void aSlotThatStartsInExactlyThirtyMinutesIsAllowed() {
    when(clock.now("UZB")).thenReturn(LocalDateTime.of(2026, 11, 19, 12, 0));

    assertThat(validator.validate(pickup().orderDate("2026-11-19").slotStart("12:30").build()))
        .isNotNull();
  }

  @Test
  void theThirtyMinuteRuleIsOnlyForToday() {
    // 10:00 tomorrow is far in the future even though 10:00 today has passed
    assertThat(validator.validate(pickup().orderDate(TOMORROW).slotStart("10:00").build()))
        .isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"12:30:00", "9:30", "1230", "12.30", "ab:cd", "25:00", "12:60"})
  void aWrongTimeFormatIsAValidationError(String slot) {
    ApiException e = rejected(pickup().slotStart(slot).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("slotStart");
  }

  @Test
  void aMissingSlotIsAValidationError() {
    assertThat(rejected(pickup().slotStart(null).build()).getFields()).containsKey("slotStart");
  }

  @Test
  void theSlotIsNotCheckedWhenTheCafeIsUnknown() {
    ApiException e = rejected(pickup().cafeId(999L).slotStart("12:15").build());

    assertThat(e.getCode()).isEqualTo("UNKNOWN_CAFE");
    assertThat(e.getFields()).doesNotContainKey("slotStart");
  }

  // ---- contact and comment: lengths --------------------------------------------------------

  @Test
  void theContactNameIsTwoToOneHundredCharacters() {
    assertThat(validator.validate(pickup().contactName("Al").build())).isNotNull();
    assertThat(validator.validate(pickup().contactName("a".repeat(100)).build())).isNotNull();

    assertThat(rejected(pickup().contactName("A").build()).getFields()).containsKey("contactName");
    assertThat(rejected(pickup().contactName("a".repeat(101)).build()).getFields())
        .containsKey("contactName");
    assertThat(rejected(pickup().contactName("   ").build()).getFields())
        .containsEntry("contactName", "is required");
  }

  @Test
  void theDeliveryAddressIsFiveToTwoHundredCharacters() {
    assertThat(validator.validate(delivery().deliveryAddress("a".repeat(5)).build())).isNotNull();
    assertThat(validator.validate(delivery().deliveryAddress("a".repeat(200)).build())).isNotNull();

    assertThat(rejected(delivery().deliveryAddress("a".repeat(4)).build()).getFields())
        .containsKey("deliveryAddress");
    assertThat(rejected(delivery().deliveryAddress("a".repeat(201)).build()).getFields())
        .containsKey("deliveryAddress");
    assertThat(rejected(delivery().deliveryAddress(null).build()).getFields())
        .containsEntry("deliveryAddress", "is required");
  }

  @Test
  void theCommentIsAtMostThreeHundredCharacters() {
    assertThat(validator.validate(pickup().comment("a".repeat(300)).build()).comment())
        .hasSize(300);
    assertThat(validator.validate(pickup().comment(null).build()).comment()).isNull();

    assertThat(rejected(pickup().comment("a".repeat(301)).build()).getFields())
        .containsKey("comment");
  }

  @Test
  void theEmailIsOptionalAndAtMost254Characters() {
    String atMax = "a".repeat(254 - "@mail.uz".length()) + "@mail.uz";

    assertThat(validator.validate(pickup().contactEmail(null).build()).contactEmail()).isNull();
    assertThat(validator.validate(pickup().contactEmail(" ").build()).contactEmail()).isNull();
    assertThat(validator.validate(pickup().contactEmail(atMax).build()).contactEmail())
        .hasSize(254);

    assertThat(rejected(pickup().contactEmail("a" + atMax).build()).getFields())
        .containsKey("contactEmail");
  }

  @ParameterizedTest
  @ValueSource(strings = {"nope", "a@b", "a b@mail.uz", "@mail.uz", "a@@mail.uz", "a@mail."})
  void aBrokenEmailIsAValidationError(String email) {
    ApiException e = rejected(pickup().contactEmail(email).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("contactEmail");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"998901234567", "+1234567", "+1234567890123456", "+998 90 123 45 67", "+99890abc"})
  void aPhoneNeedsAPlusAndEightToFifteenDigits(String phone) {
    ApiException e = rejected(pickup().contactPhone(phone).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("contactPhone");
  }

  @ParameterizedTest
  @ValueSource(strings = {"+12345678", "+998901234567", "+123456789012345"})
  void aPhoneOfEightToFifteenDigitsIsAllowed(String phone) {
    assertThat(validator.validate(pickup().contactPhone(phone).build()).contactPhone())
        .isEqualTo(phone);
  }

  @Test
  void aMissingPhoneIsAValidationError() {
    assertThat(rejected(pickup().contactPhone(null).build()).getFields())
        .containsEntry("contactPhone", "is required");
  }

  // ---- expected total ----------------------------------------------------------------------

  @Test
  void aNegativeExpectedTotalIsAValidationError() {
    ApiException e = rejected(pickup().expectedTotal(new BigDecimal("-0.01")).build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("expectedTotal");
  }

  @Test
  void theExpectedTotalIsOptionalAndZeroIsAllowed() {
    assertThat(validator.validate(pickup().expectedTotal(null).build())).isNotNull();
    assertThat(validator.validate(pickup().expectedTotal(BigDecimal.ZERO).build())).isNotNull();
  }

  // ---- items -------------------------------------------------------------------------------

  @Test
  void emptyOrMissingItemsAreAValidationError() {
    assertThat(rejected(pickup().items(List.of()).build()).getFields())
        .containsEntry("items", "must not be empty");
    assertThat(rejected(pickup().items(null).build()).getFields()).containsKey("items");
    verify(pricing, never()).calculate(any(PricingRequest.class));
  }

  @Test
  void moreThanFiftyLinesAreAValidationError() {
    List<CartItemRequest> lines = new ArrayList<>();
    for (int i = 0; i < 51; i++) {
      lines.add(new CartItemRequest(1L, BigDecimal.ONE, List.of()));
    }

    assertThat(rejected(pickup().items(lines).build()).getFields()).containsKey("items");
  }

  @Test
  void everyLineProblemIsReportedWithItsIndex() {
    ApiException e =
        rejected(
            pickup()
                .items(
                    List.of(
                        new CartItemRequest(1L, new BigDecimal("0.5"), List.of()),
                        new CartItemRequest(null, new BigDecimal("100"), List.of()),
                        new CartItemRequest(1L, BigDecimal.ZERO, List.of()),
                        new CartItemRequest(1L, null, List.of()),
                        new CartItemRequest(1L, BigDecimal.ONE, java.util.Arrays.asList(5L, null))))
                .build());

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields())
        .containsEntry("items[0].quantity", "must be a whole number")
        .containsEntry("items[1].productId", "is required")
        .containsEntry("items[1].quantity", "must be 99 or less")
        .containsEntry("items[2].quantity", "must be 1 or more")
        .containsEntry("items[3].quantity", "is required")
        .containsKey("items[4].modifierIds[1]");
    // the pricing service is not asked about a cart that is wrong already
    verify(pricing, never()).calculate(any(PricingRequest.class));
  }

  @Test
  void theProblemsOfThePricingServiceAreAddedWithTheirCode() {
    when(pricing.calculate(any(PricingRequest.class)))
        .thenThrow(
            new ApiException(
                HttpStatus.BAD_REQUEST,
                "OUT_OF_STOCK",
                "Some cart items cannot be ordered",
                Map.of("items[0].productId", "Tuna nigiri is out of stock")));

    ApiException e = rejected(pickup().build());

    assertThat(e.getCode()).isEqualTo("OUT_OF_STOCK");
    assertThat(e.getFields()).containsKey("items[0].productId");
  }

  @Test
  void aServerProblemOfThePricingServiceIsNotTurnedIntoAClientError() {
    when(pricing.calculate(any(PricingRequest.class)))
        .thenThrow(
            new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR, "PRICING_CONFIG_MISSING", "VAT rate is not set"));

    ApiException e = rejected(pickup().build());

    assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(e.getCode()).isEqualTo("PRICING_CONFIG_MISSING");
  }

  // ---- all problems together ---------------------------------------------------------------

  @Test
  void allProblemsAreReturnedTogetherAndTheCodeIsTheFirstOne() {
    ApiException e =
        rejected(
            delivery()
                .orderDate("2020-01-01")
                .cafeId(1L)
                .deliveryZoneId(999L)
                .deliveryAddress("ab")
                .slotStart("12:15x")
                .contactName("A")
                .contactPhone("12345")
                .contactEmail("nope")
                .items(List.of())
                .build());

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getCode()).isEqualTo("PAST_DATE"); // orderDate comes first in the contract
    assertThat(e.getFields())
        .containsKeys(
            "orderDate",
            "cafeId",
            "deliveryZoneId",
            "deliveryAddress",
            "slotStart",
            "contactName",
            "contactPhone",
            "contactEmail",
            "items");
  }

  @Test
  void anEmptyRequestNamesEveryRequiredField() {
    ApiException e =
        rejected(
            new OrderRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null));

    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields())
        .containsKeys(
            "country",
            "orderDate",
            "scenario",
            "slotStart",
            "contactName",
            "contactPhone",
            "items");
  }

  // ---- helpers -----------------------------------------------------------------------------

  private ApiException rejected(OrderRequest request) {
    try {
      validator.validate(request);
    } catch (ApiException e) {
      return e;
    }
    throw new AssertionError("The request must be rejected");
  }

  private static Req pickup() {
    return new Req().scenario("PICKUP").cafeId(1L);
  }

  private static Req delivery() {
    return new Req()
        .scenario("DELIVERY")
        .deliveryZoneId(1L)
        .deliveryAddress("Amir Temur 15, apt 4");
  }

  private static PriceBreakdown price() {
    return new PriceBreakdown(
        "UZB",
        "UZS",
        Scenario.PICKUP,
        List.of(),
        new BigDecimal("124.00"),
        new BigDecimal("15.00"),
        new BigDecimal("18.60"),
        new BigDecimal("142.60"),
        new BigDecimal("3.00"),
        new BigDecimal("4.28"),
        new BigDecimal("0.00"),
        new BigDecimal("138.32"));
  }

  /** A valid pickup request for tomorrow at 12:30; each test changes the field it is about. */
  private static final class Req {
    private String country = "UZB";
    private String orderDate = TOMORROW;
    private String scenario;
    private Long cafeId;
    private Long deliveryZoneId;
    private String deliveryAddress;
    private String slotStart = "12:30";
    private String contactName = "Aziz Karimov";
    private String contactPhone = "+998901234567";
    private String contactEmail;
    private String comment;
    private BigDecimal expectedTotal = new BigDecimal("138.32");
    private List<CartItemRequest> items =
        List.of(new CartItemRequest(1L, new BigDecimal("2"), List.of()));

    Req country(String v) {
      country = v;
      return this;
    }

    Req orderDate(String v) {
      orderDate = v;
      return this;
    }

    Req scenario(String v) {
      scenario = v;
      return this;
    }

    Req cafeId(Long v) {
      cafeId = v;
      return this;
    }

    Req deliveryZoneId(Long v) {
      deliveryZoneId = v;
      return this;
    }

    Req deliveryAddress(String v) {
      deliveryAddress = v;
      return this;
    }

    Req slotStart(String v) {
      slotStart = v;
      return this;
    }

    Req contactName(String v) {
      contactName = v;
      return this;
    }

    Req contactPhone(String v) {
      contactPhone = v;
      return this;
    }

    Req contactEmail(String v) {
      contactEmail = v;
      return this;
    }

    Req comment(String v) {
      comment = v;
      return this;
    }

    Req expectedTotal(BigDecimal v) {
      expectedTotal = v;
      return this;
    }

    Req items(List<CartItemRequest> v) {
      items = v;
      return this;
    }

    OrderRequest build() {
      return new OrderRequest(
          country,
          orderDate,
          scenario,
          cafeId,
          deliveryZoneId,
          deliveryAddress,
          slotStart,
          contactName,
          contactPhone,
          contactEmail,
          comment,
          expectedTotal,
          items);
    }
  }
}
