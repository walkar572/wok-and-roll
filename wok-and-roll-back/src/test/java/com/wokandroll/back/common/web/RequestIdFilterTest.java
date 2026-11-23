package com.wokandroll.back.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.JwtService;
import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.error.NotFoundException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * X-Request-Id on a 200, on the errors of the global handler (400, 404, 500) and on the 401 and 403
 * of Spring Security, and the same id in the error body and the log (#152, api-contract.md §1, §4).
 * The probe endpoints use real public paths, so the security rules let them through.
 */
@WebMvcTest(controllers = RequestIdFilterTest.ProbeController.class)
@Import({SecurityConfig.class, RequestIdFilterTest.ProbeController.class})
@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterTest {

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @RestController
  static class ProbeController {

    @GetMapping("/api/v1/countries")
    String ok() {
      return "ok";
    }

    @GetMapping("/api/v1/products/{status}")
    String fail(@PathVariable int status) {
      if (status == 400) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request validation failed",
            Map.of("country", "must be one of UZB, KAZ, GEO, UKR, CHN"));
      }
      if (status == 404) {
        throw new NotFoundException("Product 404 is not sold in UZB");
      }
      throw new IllegalStateException("secret internal detail");
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;

  @Test
  void newIdWhenTheClientSendsNone() throws Exception {
    mvc.perform(get("/api/v1/countries"))
        .andExpect(status().isOk())
        .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
  }

  @Test
  void validIdFromTheClientIsKept() throws Exception {
    for (String id : new String[] {"qa-bug-152", "ABC123", "a".repeat(64)}) {
      mvc.perform(get("/api/v1/countries").header(RequestIdFilter.HEADER, id))
          .andExpect(status().isOk())
          .andExpect(header().string(RequestIdFilter.HEADER, id));
    }
  }

  @Test
  void wrongIdFromTheClientIsReplaced() throws Exception {
    for (String id : new String[] {"", "has space", "semi;colon", "a".repeat(65)}) {
      mvc.perform(get("/api/v1/countries").header(RequestIdFilter.HEADER, id))
          .andExpect(status().isOk())
          .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }
  }

  @Test
  void errorsOfTheGlobalHandlerHaveTheIdInHeaderAndBody() throws Exception {
    for (int code : new int[] {400, 404, 500}) {
      String id = "qa-" + code;
      mvc.perform(get("/api/v1/products/" + code).header(RequestIdFilter.HEADER, id))
          .andExpect(status().is(code))
          .andExpect(header().string(RequestIdFilter.HEADER, id))
          .andExpect(jsonPath("$.requestId").value(id));
    }
  }

  @Test
  void newIdIsTheSameInHeaderAndBody() throws Exception {
    MvcResult result =
        mvc.perform(get("/api/v1/products/404")).andExpect(status().isNotFound()).andReturn();

    String id = result.getResponse().getHeader(RequestIdFilter.HEADER);
    assertThat(id).matches(UUID_PATTERN);
    assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + id + "\"");
  }

  @Test
  void securityErrorsHaveTheIdToo() throws Exception {
    mvc.perform(get("/api/v1/me").header(RequestIdFilter.HEADER, "qa-401"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(RequestIdFilter.HEADER, "qa-401"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.requestId").value("qa-401"));

    String customer = "Bearer " + jwt.createToken(new AuthUser(1, "user@mail.uz", "CUSTOMER"));
    mvc.perform(
            get("/api/v1/reports/sales-by-region")
                .header("Authorization", customer)
                .header(RequestIdFilter.HEADER, "qa-403"))
        .andExpect(status().isForbidden())
        .andExpect(header().string(RequestIdFilter.HEADER, "qa-403"))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.requestId").value("qa-403"));
  }

  @Test
  void logLineOfAnErrorShowsTheId(CapturedOutput output) throws Exception {
    mvc.perform(get("/api/v1/products/500").header(RequestIdFilter.HEADER, "qa-log-500"))
        .andExpect(status().isInternalServerError());

    assertThat(output.getAll()).contains("[qa-log-500]");
  }
}
