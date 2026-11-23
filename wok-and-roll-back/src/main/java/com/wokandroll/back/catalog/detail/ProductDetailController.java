package com.wokandroll.back.catalog.detail;

import com.wokandroll.back.catalog.detail.dto.ProductDetailResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/products/{id}: the product modal with modifiers (#113, api-contract.md 3.4). */
@RestController
@RequestMapping("/api/v1/products")
@Validated
@Tag(name = "Products")
public class ProductDetailController {

  private final ProductDetailService service;

  public ProductDetailController(ProductDetailService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "One dish with its modifiers and prices in the chosen country",
      description =
          "Used by the product modal. Modifiers: only visible ones with a price in this country,"
              + " SIZE first, then EXTRA, then id.")
  @ApiResponse(responseCode = "200", description = "Dish found")
  @ApiResponse(
      responseCode = "400",
      description = "Country missing or unknown, id is not a number",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Dish does not exist, the dish or its category is hidden, or it has no price in this country",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ProductDetailResponse getProduct(
      @Parameter(description = "Dish id", example = "5") @PathVariable long id,
      @Parameter(description = "Country code", example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country) {
    return service.getProduct(id, country);
  }
}
