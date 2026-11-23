package com.wokandroll.back.catalog.controller;

import com.wokandroll.back.catalog.dto.ProductResponse;
import com.wokandroll.back.catalog.service.CatalogService;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.CountryCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/products: the menu cards of one country (#109, api-contract.md 3.3). */
@RestController
@RequestMapping("/api/v1/products")
@Validated
@Tag(name = "Products")
public class ProductController {

  private final CatalogService service;

  public ProductController(CatalogService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Dishes with their price in the chosen country",
      description =
          "Only visible dishes that have a price in this country. Out-of-stock dishes are"
              + " returned with inStock false. Order: category sort_order, then dish id.")
  @ApiResponse(responseCode = "200", description = "Dishes, can be empty")
  @ApiResponse(
      responseCode = "400",
      description = "Country missing or unknown, categoryId is not a positive number",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public List<ProductResponse> getProducts(
      @Parameter(description = "Country code", example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @Parameter(description = "Category id. Without it, all categories", example = "2")
          @RequestParam(required = false)
          @Positive(message = "must be a positive number")
          Long categoryId) {
    return service.getProducts(country, categoryId);
  }
}
