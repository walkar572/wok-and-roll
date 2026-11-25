package com.wokandroll.back.admin.controller;

import com.wokandroll.back.admin.dto.AdminDtos.AdminProductResponse;
import com.wokandroll.back.admin.dto.AdminDtos.CategoryResponse;
import com.wokandroll.back.admin.dto.AdminDtos.CreateCategoryRequest;
import com.wokandroll.back.admin.dto.AdminDtos.CreateProductRequest;
import com.wokandroll.back.admin.dto.AdminDtos.PriceRequest;
import com.wokandroll.back.admin.dto.AdminDtos.PriceResponse;
import com.wokandroll.back.admin.dto.AdminDtos.UpdateCategoryRequest;
import com.wokandroll.back.admin.dto.AdminDtos.UpdateProductRequest;
import com.wokandroll.back.admin.service.AdminCatalogService;
import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.CountryCodes;
import com.wokandroll.back.common.web.PageRequest;
import com.wokandroll.back.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin catalog API, ADMIN only (api-contract.md section 3.15, #178). */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
@Tag(name = "Admin catalog")
@ApiResponse(
    responseCode = "401",
    description = "UNAUTHORIZED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(
    responseCode = "403",
    description = "FORBIDDEN: only ADMIN",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class AdminCatalogController {

  private final AdminCatalogService service;

  public AdminCatalogController(AdminCatalogService service) {
    this.service = service;
  }

  @GetMapping("/products")
  @Operation(
      summary = "All products, hidden ones too, with the price and stock of one country",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public PageResponse<AdminProductResponse> products(
      @Parameter(example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String pageToken) {
    return service.products(country, PageRequest.of(pageSize, pageToken));
  }

  @PostMapping("/products")
  @Operation(
      summary = "Create a product",
      description = "The response has the price fields of the country (null: not sold yet).",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "201", description = "Created")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<AdminProductResponse> createProduct(
      @RequestBody CreateProductRequest request,
      @Parameter(example = "UZB")
          @RequestParam(defaultValue = "UZB")
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country) {
    AdminProductResponse created = service.createProduct(request, country);
    return ResponseEntity.created(URI.create("/api/v1/admin/products/" + created.id()))
        .body(created);
  }

  @PatchMapping("/products/{id}")
  @Operation(
      summary = "Change a product: only the sent fields, version required",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "Changed")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "VERSION_CONFLICT: changed by someone else, reload",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public AdminProductResponse updateProduct(
      @PathVariable long id,
      @RequestBody UpdateProductRequest request,
      @Parameter(example = "UZB")
          @RequestParam(defaultValue = "UZB")
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country) {
    return service.updateProduct(id, request, country);
  }

  @PutMapping("/products/{id}/prices/{country}")
  @Operation(
      summary = "Set the price and stock of a product in a country",
      description = "Creates the price when it is missing (version is then ignored).",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "Saved")
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "VERSION_CONFLICT",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public PriceResponse putPrice(
      @PathVariable long id,
      @PathVariable @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @RequestBody PriceRequest request) {
    return service.putPrice(id, country, request);
  }

  @GetMapping("/categories")
  @Operation(
      summary = "All categories, hidden ones too",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public List<CategoryResponse> categories() {
    return service.categories();
  }

  @PostMapping("/categories")
  @Operation(
      summary = "Create a category",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "201", description = "Created")
  public ResponseEntity<CategoryResponse> createCategory(
      @RequestBody CreateCategoryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createCategory(request));
  }

  @PatchMapping("/categories/{id}")
  @Operation(
      summary = "Change a category: only the sent fields, version required",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(
      responseCode = "409",
      description = "VERSION_CONFLICT",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public CategoryResponse updateCategory(
      @PathVariable long id, @RequestBody UpdateCategoryRequest request) {
    return service.updateCategory(id, request);
  }
}
