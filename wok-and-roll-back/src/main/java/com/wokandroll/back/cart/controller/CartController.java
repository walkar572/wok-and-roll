package com.wokandroll.back.cart.controller;

import com.wokandroll.back.cart.dto.CartQuoteRequest;
import com.wokandroll.back.cart.dto.CartQuoteResponse;
import com.wokandroll.back.cart.service.CartService;
import com.wokandroll.back.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart")
public class CartController {

  private final CartService cartService;

  public CartController(CartService cartService) {
    this.cartService = cartService;
  }

  @PostMapping("/quote")
  @Operation(
      summary = "Price a cart: subtotal, VAT, discount, delivery fee and total",
      description =
          "Prices come from the database for the given country. All money has 2 decimals, "
              + "rounded half-up after each step. scenario is PICKUP (default) or DELIVERY; "
              + "DELIVERY needs deliveryZoneId and adds the fee of the zone. "
              + "The guest flow is public.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Wrong request (VALIDATION_ERROR: quantity 1 to 99, 1 to 50 lines) or a wrong cart:"
              + " UNKNOWN_COUNTRY, UNKNOWN_DELIVERY_ZONE, UNKNOWN_PRODUCT, PRODUCT_NOT_SOLD,"
              + " OUT_OF_STOCK, INVALID_MODIFIER (a dish with sizes needs exactly one). The fields"
              + " map shows every problem, for example items[0].productId.",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public CartQuoteResponse quote(@Valid @RequestBody CartQuoteRequest request) {
    return cartService.quote(request);
  }
}
