package com.wokandroll.back.common.error;

import com.wokandroll.back.common.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * The one error format of the whole API: {code, message, fields, requestId} (api-contract.md §4).
 * The constructor without {@code requestId} and {@link #of} add the id of the current request
 * ({@link RequestIdFilter}), so the body, the {@code X-Request-Id} header and the log show the same
 * id.
 */
@Schema(description = "Error response")
public record ErrorResponse(
    @Schema(example = "VALIDATION_ERROR") String code,
    @Schema(example = "Request validation failed") String message,
    @Schema(description = "Field name -> problem. Empty if the error is not about fields.")
        Map<String, String> fields,
    @Schema(
            description = "Id of the request, the same as the X-Request-Id header",
            example = "9f1c2e7a-3b4d-4c55-9d1e-0a6b7c8d9e10")
        String requestId) {

  /** An error of the current request, with its request id. */
  public ErrorResponse(String code, String message, Map<String, String> fields) {
    this(code, message, fields, RequestIdFilter.currentId());
  }

  public static ErrorResponse of(String code, String message) {
    return new ErrorResponse(code, message, Map.of());
  }
}
