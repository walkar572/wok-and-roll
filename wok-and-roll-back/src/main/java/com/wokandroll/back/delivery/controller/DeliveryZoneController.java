package com.wokandroll.back.delivery.controller;

import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.CountryCodes;
import com.wokandroll.back.delivery.dto.DeliveryZoneListResponse;
import com.wokandroll.back.delivery.service.DeliveryZoneService;
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

/** GET /api/v1/delivery-zones: zones with a fixed fee for checkout (#147, api-contract.md 3.7). */
@RestController
@RequestMapping("/api/v1/delivery-zones")
@Validated
@Tag(name = "Delivery zones")
public class DeliveryZoneController {

  private final DeliveryZoneService service;

  public DeliveryZoneController(DeliveryZoneService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Active delivery zones of a country with their fee",
      description = "Ordered by name. Each zone is served by one café (cafeId).")
  @ApiResponse(responseCode = "200", description = "Zones of the country")
  @ApiResponse(
      responseCode = "400",
      description = "Country missing or unknown",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public DeliveryZoneListResponse getZones(
      @Parameter(description = "Country code", example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country) {
    return service.getZones(country);
  }
}
