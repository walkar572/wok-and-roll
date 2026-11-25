package com.wokandroll.back.slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.time.CountryClock;
import com.wokandroll.back.slot.service.SlotService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Runs only when CI=true: GitHub Actions sets it and has PostgreSQL 16 on localhost:5432.
// Not @Transactional: the parallel reservations must really commit, like in POST /orders.
// The places of the test café are deleted before and after every test.
/** Time slots and the atomic reservation against the real database (#148, contract 3.8). */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class SlotApiIntegrationTest {

  private static final LocalTime NOON = LocalTime.of(12, 0);

  @Autowired private MockMvc mvc;

  @Autowired private JdbcClient jdbc;

  @Autowired private SlotService slotService;

  @Autowired private CountryClock countryClock;

  private long chilanzar;
  private LocalDate tomorrow;

  @BeforeEach
  void cleanCafe() {
    chilanzar =
        jdbc.sql("SELECT id FROM cafe WHERE name = 'Wok & Roll Chilanzar'")
            .query(Long.class)
            .single();
    tomorrow = countryClock.today("UZB").plusDays(1);
    deletePlaces();
  }

  @AfterEach
  void deletePlaces() {
    jdbc.sql("DELETE FROM slot_reservation WHERE cafe_id = :cafeId")
        .param("cafeId", chilanzar)
        .update();
  }

  @Test
  void twentyGuestsAtOnceGetExactlyFivePlaces() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(20);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      futures.add(pool.submit(() -> reserveWhenReady(go)));
    }
    go.countDown();
    List<String> results = new ArrayList<>();
    for (Future<String> future : futures) {
      results.add(future.get(30, TimeUnit.SECONDS));
    }
    pool.shutdown();

    assertThat(Collections.frequency(results, "OK")).isEqualTo(5);
    assertThat(Collections.frequency(results, "SLOT_FULL")).isEqualTo(15);
    assertThat(reserved(NOON)).isEqualTo(5);
  }

  @Test
  void releaseNeverGoesBelowZero() {
    slotService.reserve(chilanzar, tomorrow, NOON);
    slotService.reserve(chilanzar, tomorrow, NOON);

    slotService.release(chilanzar, tomorrow, NOON);
    slotService.release(chilanzar, tomorrow, NOON);
    slotService.release(chilanzar, tomorrow, NOON);

    assertThat(reserved(NOON)).isZero();
    slotService.reserve(chilanzar, tomorrow, NOON);
    assertThat(reserved(NOON)).isEqualTo(1);
  }

  @Test
  void dayOfTheCafeShowsTheFullSlot() throws Exception {
    for (int i = 0; i < 5; i++) {
      slotService.reserve(chilanzar, tomorrow, LocalTime.of(10, 30));
    }

    slotsOf(chilanzar, tomorrow)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeZone").value("Asia/Tashkent"))
        .andExpect(jsonPath("$.date").value(tomorrow.toString()))
        .andExpect(jsonPath("$.slots.length()").value(24))
        .andExpect(jsonPath("$.slots[0].start").value("10:00"))
        .andExpect(jsonPath("$.slots[0].available").value(true))
        .andExpect(jsonPath("$.slots[1].remaining").value(0))
        .andExpect(jsonPath("$.slots[1].reason").value("FULL"))
        .andExpect(jsonPath("$.slots[23].end").value("22:00"));
  }

  @Test
  void unknownCafeAndDateTooFarAreBadRequest() throws Exception {
    slotsOf(999_999, tomorrow)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_CAFE"));
    slotsOf(chilanzar, tomorrow.plusDays(7))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.date").exists());
  }

  private ResultActions slotsOf(long cafeId, LocalDate date) throws Exception {
    String id = String.valueOf(cafeId);
    String day = date.toString();
    return mvc.perform(get("/api/v1/time-slots").param("cafeId", id).param("date", day));
  }

  private String reserveWhenReady(CountDownLatch go) throws InterruptedException {
    go.await();
    try {
      slotService.reserve(chilanzar, tomorrow, NOON);
      return "OK";
    } catch (ApiException e) {
      return e.getCode();
    }
  }

  private int reserved(LocalTime slotStart) {
    return jdbc.sql(
            """
            SELECT reserved FROM slot_reservation
            WHERE cafe_id = :cafeId AND slot_date = :slotDate AND slot_start = :slotStart
            """)
        .param("cafeId", chilanzar)
        .param("slotDate", tomorrow)
        .param("slotStart", slotStart)
        .query(Integer.class)
        .single();
  }
}
