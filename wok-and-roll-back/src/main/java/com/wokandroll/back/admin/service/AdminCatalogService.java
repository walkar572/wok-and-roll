package com.wokandroll.back.admin.service;

import com.wokandroll.back.admin.dto.AdminDtos.AdminProductResponse;
import com.wokandroll.back.admin.dto.AdminDtos.CategoryResponse;
import com.wokandroll.back.admin.dto.AdminDtos.CreateCategoryRequest;
import com.wokandroll.back.admin.dto.AdminDtos.CreateProductRequest;
import com.wokandroll.back.admin.dto.AdminDtos.PriceRequest;
import com.wokandroll.back.admin.dto.AdminDtos.PriceResponse;
import com.wokandroll.back.admin.dto.AdminDtos.UpdateCategoryRequest;
import com.wokandroll.back.admin.dto.AdminDtos.UpdateProductRequest;
import com.wokandroll.back.admin.repository.AdminCatalogRepository;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.error.NotFoundException;
import com.wokandroll.back.common.web.PageRequest;
import com.wokandroll.back.common.web.PageResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin catalog (api-contract.md section 3.15, #178): products, prices and stock per country,
 * visibility, categories. There is no delete: hiding keeps old orders and reports correct. The
 * public menu shows every change at once (no cache).
 */
@Service
public class AdminCatalogService {

  static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

  private final AdminCatalogRepository repository;

  public AdminCatalogService(AdminCatalogRepository repository) {
    this.repository = repository;
  }

  public PageResponse<AdminProductResponse> products(String country, PageRequest page) {
    return PageResponse.of(
        repository.findProducts(country, page.pageSize() + 1, page.offset()), page);
  }

  public AdminProductResponse product(long id, String country) {
    return repository
        .findProduct(id, country)
        .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
  }

  @Transactional
  public AdminProductResponse createProduct(CreateProductRequest request, String country) {
    Problems problems = new Problems();
    Long categoryId = request.categoryId();
    if (categoryId == null) {
      problems.add("categoryId", "is required");
    } else if (!repository.categoryExists(categoryId)) {
      problems.add("categoryId", "Unknown category");
    }
    String name = text("name", request.name(), 150, true, problems);
    String description = text("description", request.description(), 1000, false, problems);
    String imageUrl = imageUrl(request.imageUrl(), problems);
    problems.throwIfAny();

    long id =
        repository.insertProduct(
            categoryId,
            name,
            description,
            imageUrl,
            request.visible() == null || request.visible());
    return product(id, country);
  }

  @Transactional
  public AdminProductResponse updateProduct(long id, UpdateProductRequest request, String country) {
    if (!repository.productExists(id)) {
      throw new NotFoundException("Product " + id + " not found");
    }
    Problems problems = new Problems();
    if (request.version() == null) {
      problems.add("version", "is required");
    }
    if (request.categoryId() != null && !repository.categoryExists(request.categoryId())) {
      problems.add("categoryId", "Unknown category");
    }
    String name = request.name() == null ? null : text("name", request.name(), 150, true, problems);
    String description =
        request.description() == null
            ? null
            : text("description", request.description(), 1000, false, problems);
    String imageUrl = request.imageUrl() == null ? null : imageUrl(request.imageUrl(), problems);
    problems.throwIfAny();

    boolean updated =
        repository.updateProduct(
            id,
            request.version(),
            request.categoryId(),
            name,
            request.description() != null,
            description,
            request.imageUrl() != null,
            imageUrl,
            request.visible());
    if (!updated) {
      throw versionConflict();
    }
    return product(id, country);
  }

  /** Creates the price of a country when it is missing, otherwise changes it (with version). */
  @Transactional
  public PriceResponse putPrice(long productId, String country, PriceRequest request) {
    if (!repository.productExists(productId)) {
      throw new NotFoundException("Product " + productId + " not found");
    }
    Problems problems = new Problems();
    BigDecimal price = request.price();
    if (price == null) {
      problems.add("price", "is required");
    } else if (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0) {
      problems.add("price", "must be from 0.00 to 9999999999.99");
    } else if (price.stripTrailingZeros().scale() > 2) {
      problems.add("price", "must have at most 2 decimals");
    }
    if (request.inStock() == null) {
      problems.add("inStock", "is required");
    }
    Integer current = repository.priceVersion(productId, country).orElse(null);
    if (current != null && request.version() == null) {
      problems.add("version", "is required");
    }
    problems.throwIfAny();

    boolean saved =
        current == null
            ? repository.insertPrice(productId, country, price, request.inStock())
            : repository.updatePrice(
                productId, country, price, request.inStock(), request.version());
    if (!saved) {
      throw versionConflict();
    }
    return repository.findPrice(productId, country).orElseThrow();
  }

  public List<CategoryResponse> categories() {
    return repository.findCategories();
  }

  @Transactional
  public CategoryResponse createCategory(CreateCategoryRequest request) {
    Problems problems = new Problems();
    String name = text("name", request.name(), 100, true, problems);
    if (name != null && repository.categoryNameTaken(name, null)) {
      problems.add("name", "A category with this name already exists");
    }
    problems.throwIfAny();
    long id =
        repository.insertCategory(
            name,
            request.sortOrder() == null ? 0 : request.sortOrder(),
            request.visible() == null || request.visible());
    return repository.findCategory(id).orElseThrow();
  }

  @Transactional
  public CategoryResponse updateCategory(long id, UpdateCategoryRequest request) {
    if (repository.findCategory(id).isEmpty()) {
      throw new NotFoundException("Category " + id + " not found");
    }
    Problems problems = new Problems();
    if (request.version() == null) {
      problems.add("version", "is required");
    }
    String name = request.name() == null ? null : text("name", request.name(), 100, true, problems);
    if (name != null && repository.categoryNameTaken(name, id)) {
      problems.add("name", "A category with this name already exists");
    }
    problems.throwIfAny();
    if (!repository.updateCategory(
        id, request.version(), name, request.sortOrder(), request.visible())) {
      throw versionConflict();
    }
    return repository.findCategory(id).orElseThrow();
  }

  private static String text(
      String field, String raw, int max, boolean required, Problems problems) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      if (required) {
        problems.add(field, "is required");
      }
      return null;
    }
    if (text.length() > max) {
      problems.add(field, "must be " + max + " characters or fewer");
      return null;
    }
    return text;
  }

  /** Only a path served by the frontend: the API never loads a URL (OWASP API7). */
  private static String imageUrl(String raw, Problems problems) {
    String url = raw == null ? "" : raw.trim();
    if (url.isEmpty()) {
      return null;
    }
    if (!url.startsWith("/images/") || url.length() > 500 || url.contains("..")) {
      problems.add("imageUrl", "must be a path that starts with /images/");
      return null;
    }
    return url;
  }

  private static ApiException versionConflict() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "VERSION_CONFLICT",
        "Changed by someone else, reload",
        Map.of("version", "Changed by someone else, reload"));
  }

  /** Collects all field problems into one 400 VALIDATION_ERROR. */
  private static final class Problems {
    private final Map<String, String> fields = new LinkedHashMap<>();

    void add(String field, String message) {
      fields.putIfAbsent(field, message);
    }

    void throwIfAny() {
      if (!fields.isEmpty()) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fields);
      }
    }
  }
}
