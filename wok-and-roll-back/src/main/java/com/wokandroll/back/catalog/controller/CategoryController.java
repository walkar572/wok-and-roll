package com.wokandroll.back.catalog.controller;

import com.wokandroll.back.catalog.dto.CategoryResponse;
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
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/categories: the category filter of the menu (#109, api-contract.md 3.2). */
@RestController
@RequestMapping("/api/v1/categories")
@Validated
@Tag(name = "Categories")
public class CategoryController {

  private final CatalogService service;

  public CategoryController(CatalogService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Categories that have at least one dish in the chosen country",
      description = "Only visible categories with a visible dish priced in this country.")
  @ApiResponse(responseCode = "200", description = "Categories ordered by sort_order")
  @ApiResponse(
      responseCode = "400",
      description = "Country missing or unknown",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public List<CategoryResponse> getCategories(
      @Parameter(description = "Country code", example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country) {
    return service.getCategories(country);
  }
}
