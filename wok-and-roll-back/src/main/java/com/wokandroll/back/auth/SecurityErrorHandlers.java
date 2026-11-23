package com.wokandroll.back.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wokandroll.back.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 401 and 403 come from Spring Security before any controller, so the global handler does not see
 * them. These handlers write the same {code, message, fields} JSON as the rest of the API. Every
 * 401 has the header {@code WWW-Authenticate: Bearer} (RFC 6750, api-contract.md section 4).
 */
public class SecurityErrorHandlers {

  static final String WWW_AUTHENTICATE_VALUE = "Bearer";

  private final ObjectMapper objectMapper;

  public SecurityErrorHandlers(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** No token on a closed endpoint. */
  public AuthenticationEntryPoint unauthorized() {
    return (request, response, ex) ->
        write401(response, ErrorResponse.of("UNAUTHORIZED", "Log in to use this endpoint"));
  }

  /** A bad or expired token on any endpoint. */
  public AuthenticationEntryPoint badToken() {
    return (request, response, ex) ->
        write401(
            response,
            ErrorResponse.of("UNAUTHORIZED", "Your session is not valid any more, log in again"));
  }

  public AccessDeniedHandler forbidden() {
    return (request, response, ex) ->
        write(response, 403, ErrorResponse.of("FORBIDDEN", "Your role cannot use this endpoint"));
  }

  private void write401(HttpServletResponse response, ErrorResponse body) throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_VALUE);
    write(response, 401, body);
  }

  private void write(HttpServletResponse response, int status, ErrorResponse body)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
