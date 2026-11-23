package com.wokandroll.back.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id (api-contract.md §1, #152). A valid {@code X-Request-Id} from the
 * client is kept, otherwise a new UUID is created. The id is sent back in the same header, shown in
 * every log line (MDC key {@code requestId}) and put into every error body ({@code ErrorResponse}).
 *
 * <p>It runs first, before Spring Security, so the 401 and 403 answers have the id too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";

  /** Key of the id in the logging MDC. The log pattern in application.yml prints it. */
  public static final String MDC_KEY = "requestId";

  /** What a client may send (api-contract.md §1): letters, digits and "-", at most 64. */
  private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

  /** The id of the request on this thread, or null outside a request. */
  public static String currentId() {
    return MDC.get(MDC_KEY);
  }

  /** Keeps a valid id from the client. A missing or wrong one is replaced by a new UUID. */
  static String requestIdOf(String fromClient) {
    if (fromClient != null && VALID_ID.matcher(fromClient).matches()) {
      return fromClient;
    }
    return UUID.randomUUID().toString();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = requestIdOf(request.getHeader(HEADER));
    MDC.put(MDC_KEY, id);
    response.setHeader(HEADER, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
