package com.wokandroll.back.cafe.controller;

import com.wokandroll.back.cafe.dto.CafeListResponse;
import com.wokandroll.back.cafe.service.CafeService;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.CountryCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/cafes: pickup points and booking venues (#146, api-contract.md 3.6). */
@RestController
@RequestMapping("/api/v1/cafes")
@Validated
@Tag(name = "Cafés")
public class CafeController {

  private final CafeService service;

  public CafeController(CafeService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Active cafés of a country",
      description = "Ordered by name. Opening hours are local times (HH:mm) in timeZone.")
  @ApiResponse(responseCode = "200", description = "Cafés of the country")
  @ApiResponse(
      responseCode = "400",
      description = "Country missing or unknown",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public CafeListResponse getCafes(
      @Parameter(description = "Country code", example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country) {
    return service.getCafes(country);
  }
}
