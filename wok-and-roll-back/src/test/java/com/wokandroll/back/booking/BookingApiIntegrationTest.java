package com.wokandroll.back.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.common.time.CountryClock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Runs only when CI=true (PostgreSQL on localhost:5432). Not @Transactional: every booking try
// commits on its own (that is how the service works), so the test deletes its rows afterwards.
/**
 * {@code POST /api/v1/bookings} against the real database (#174): the smallest free table, 409 when
 * every fitting table is taken, idempotency, and no double booking with parallel requests. The seed
 * café 2 (Wok & Roll Yunusabad) has tables of 2, 2, 4, 4, 6 and 8 seats.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class BookingApiIntegrationTest {

  private static final long CAFE = 2;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private CountryClock clock;

  @AfterEach
  void deleteTestBookings() {
    jdbc.sql("DELETE FROM booking WHERE contact_name = 'Booking Test'").update();
  }

  /** A day 20 days ahead, so the test never meets a real booking or "now". */
  private String day() {
    return clock.today("UZB").plusDays(20).toString();
  }

  @Test
  void theSmallestFittingTableIsTaken() throws Exception {
    book(day(), "19:00", 3, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.bookingNumber").value(matchesPattern("BK-[0-9A-HJKMNP-TV-Z]{8}")))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.cafe.id").value(CAFE))
        .andExpect(jsonPath("$.time").value("19:00"))
        .andExpect(jsonPath("$.endTime").value("20:30"))
        .andExpect(jsonPath("$.guests").value(3))
        .andExpect(jsonPath("$.tableSeats").value(4));
  }

  @Test
  void whenEveryFittingTableIsTakenTheAnswerIs409() throws Exception {
    book(day(), "18:00", 8, null).andExpect(status().isCreated());

    book(day(), "19:00", 7, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_TABLE_AVAILABLE"))
        .andExpect(jsonPath("$.fields.time").exists());
    // 90 minutes later the 8-seat table is free again
    book(day(), "19:30", 7, null).andExpect(status().isCreated());
  }

  @Test
  void theSameKeyReplaysAndAnotherBodyIs422() throws Exception {
    String key = UUID.randomUUID().toString();
    String first =
        book(day(), "12:00", 2, key)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String second =
        book(day(), "12:00", 2, key)
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(second).isEqualTo(first);

    book(day(), "12:30", 2, key)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void validationProblemsComeTogether() throws Exception {
    mvc.perform(
            post("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"cafeId": 999, "date": "2020-01-01", "time": "19:15", "guests": 12,
                     "contactName": "", "contactPhone": "123"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_CAFE"))
        .andExpect(jsonPath("$.fields.cafeId").exists())
        .andExpect(jsonPath("$.fields.guests").exists())
        .andExpect(jsonPath("$.fields.contactName").exists())
        .andExpect(jsonPath("$.fields.contactPhone").exists());
  }

  @Test
  void parallelRequestsNeverBookOneTableTwice() throws Exception {
    // Two 8-seat requests at once: the café has one 8-seat table, so exactly one wins.
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Callable<Integer>> calls = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        calls.add(() -> book(day(), "13:00", 7, null).andReturn().getResponse().getStatus());
      }
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> f : pool.invokeAll(calls)) {
        statuses.add(f.get());
      }
      assertThat(statuses).containsOnly(201, 409);
      assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
    } finally {
      pool.shutdown();
    }
  }

  private ResultActions book(String date, String time, int guests, String key) throws Exception {
    var request =
        post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"cafeId": %d, "date": "%s", "time": "%s", "guests": %d,
                 "contactName": "Booking Test", "contactPhone": "+998901234567"}
                """
                    .formatted(CAFE, date, time, guests));
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return mvc.perform(request);
  }
}
