package com.wokandroll.back.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks the access rules (api-contract.md section 8, one test per row) on fake endpoints with the
 * real paths, so the test does not depend on which feature controllers are already merged.
 */
@WebMvcTest(controllers = SecurityRulesTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityRulesTest.ProbeController.class})
class SecurityRulesTest {

  @RestController
  static class ProbeController {
    @GetMapping({
      "/api/health",
      "/api/v1/countries",
      "/api/v1/categories",
      "/api/v1/products",
      "/api/v1/products/{id}",
      "/api/v1/delivery-zones",
      "/api/v1/time-slots",
      "/api/v1/me",
      "/api/v1/me/orders",
      "/api/v1/staff/orders",
      "/api/v1/reports/sales-by-region",
      "/api/v1/admin/products"
    })
    String get() {
      return "ok";
    }

    @PostMapping({
      "/api/v1/cart/quote",
      "/api/v1/orders",
      "/api/v1/bookings",
      "/api/v1/auth/login",
      "/api/v1/auth/register"
    })
    String post() {
      return "ok";
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;

  @Value("${JWT_SECRET}")
  private String secret;

  private String bearer(String role) {
    return "Bearer " + jwt.createToken(new AuthUser(1, "user@mail.uz", role));
  }

  @Test
  void guestFlowWorksWithoutToken() throws Exception {
    for (String path :
        new String[] {
          "/api/health",
          "/api/v1/countries",
          "/api/v1/categories?country=UZB",
          "/api/v1/products?country=UZB",
          "/api/v1/products/5?country=UZB",
          "/api/v1/delivery-zones?country=UZB",
          "/api/v1/time-slots?cafeId=1&date=2026-11-20"
        }) {
      mvc.perform(get(path)).andExpect(status().isOk());
    }
    for (String path : new String[] {"/api/v1/cart/quote", "/api/v1/orders", "/api/v1/bookings"}) {
      mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isOk());
    }
  }

  @Test
  void guestFlowAlsoWorksWithAValidToken() throws Exception {
    mvc.perform(get("/api/v1/countries").header("Authorization", bearer("CUSTOMER")))
        .andExpect(status().isOk());
  }

  @Test
  void aBrokenTokenIs401EvenOnAPublicEndpoint() throws Exception {
    mvc.perform(get("/api/v1/countries").header("Authorization", "Bearer broken"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mvc.perform(
            post("/api/v1/cart/quote")
                .header("Authorization", "Bearer broken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anExpiredTokenIs401EvenOnAPublicEndpoint() throws Exception {
    Instant yesterday = Instant.now().minus(Duration.ofHours(25));
    String expired =
        new JwtService(secret, 24, Clock.fixed(yesterday, ZoneOffset.UTC))
            .createToken(new AuthUser(1, "user@mail.uz", "CUSTOMER"));

    mvc.perform(get("/api/v1/products?country=UZB").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
  }

  @Test
  void loginAndRegisterIgnoreAStaleToken() throws Exception {
    for (String path : new String[] {"/api/v1/auth/login", "/api/v1/auth/register"}) {
      mvc.perform(
              post(path)
                  .header("Authorization", "Bearer old-or-broken")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk());
    }
  }

  @Test
  void meNeedsLogin() throws Exception {
    mvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mvc.perform(get("/api/v1/me/orders").header("Authorization", "Bearer broken"))
        .andExpect(status().isUnauthorized());
    for (String role : new String[] {"CUSTOMER", "STAFF", "VIEWER", "ADMIN"}) {
      mvc.perform(get("/api/v1/me").header("Authorization", bearer(role)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void staffNeedsStaffOrAdmin() throws Exception {
    String path = "/api/v1/staff/orders";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", bearer("CUSTOMER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(get(path).header("Authorization", bearer("VIEWER")))
        .andExpect(status().isForbidden());
    mvc.perform(get(path).header("Authorization", bearer("STAFF"))).andExpect(status().isOk());
    mvc.perform(get(path).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
  }

  @Test
  void reportsNeedAdminOrViewer() throws Exception {
    String path = "/api/v1/reports/sales-by-region";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", bearer("CUSTOMER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(get(path).header("Authorization", bearer("STAFF")))
        .andExpect(status().isForbidden());
    mvc.perform(get(path).header("Authorization", bearer("VIEWER"))).andExpect(status().isOk());
    mvc.perform(get(path).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
  }

  @Test
  void adminNeedsAdmin() throws Exception {
    String path = "/api/v1/admin/products";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    for (String role : new String[] {"CUSTOMER", "STAFF", "VIEWER"}) {
      mvc.perform(get(path).header("Authorization", bearer(role)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get(path).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
  }

  @Test
  void anUnknownPathWithoutATokenIs401() throws Exception {
    mvc.perform(get("/api/v1/does-not-exist"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
  }
}
