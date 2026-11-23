package com.wokandroll.back.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.dto.AuthResponse;
import com.wokandroll.back.auth.dto.MeResponse;
import com.wokandroll.back.common.error.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  private static final String LOGIN_BODY =
      """
      {"email":"aziz@mail.uz","password":"wrong-pass"}
      """;

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @MockitoBean private AuthService service;

  @Test
  void registerReturns201WithTokenAndRole() throws Exception {
    when(service.register(any())).thenReturn(new AuthResponse("token-123", "CUSTOMER"));

    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"aziz@mail.uz","password":"secret123","fullName":"Aziz Karimov"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").value("token-123"))
        .andExpect(jsonPath("$.role").value("CUSTOMER"));
  }

  @Test
  void registerValidatesEmailAndPassword() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"not-an-email","password":"short","fullName":""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.email").value("must be a valid email"))
        .andExpect(jsonPath("$.fields.password").value("must be 8 to 72 characters"))
        .andExpect(jsonPath("$.fields.fullName").exists());
    verifyNoInteractions(service);
  }

  @Test
  void takenEmailIs409() throws Exception {
    when(service.register(any()))
        .thenThrow(
            new ApiException(
                HttpStatus.CONFLICT,
                "EMAIL_TAKEN",
                "This email is already registered",
                Map.of("email", "is already registered")));

    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"aziz@mail.uz","password":"secret123","fullName":"Aziz"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void wrongLoginIs401WithWwwAuthenticate() throws Exception {
    when(service.login(any(), anyString())).thenThrow(new InvalidCredentialsException());

    mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void tooManyLoginsIs429WithRetryAfter() throws Exception {
    when(service.login(any(), anyString())).thenThrow(new TooManyAttemptsException(600));

    mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "600"))
        .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
        .andExpect(jsonPath("$.fields").isEmpty());
  }

  @Test
  void meReturnsTheUserFromTheToken() throws Exception {
    AuthUser aziz = new AuthUser(15, "aziz@mail.uz", "CUSTOMER");
    when(service.me(aziz))
        .thenReturn(new MeResponse(15, "aziz@mail.uz", "Aziz Karimov", null, "CUSTOMER"));

    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + jwt.createToken(aziz)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("aziz@mail.uz"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }
}
