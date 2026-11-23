package com.wokandroll.back.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void notFoundReturnsCodeAndMessage() throws Exception {
    mvc.perform(get("/probe/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Product 7 not found"));
  }

  @Test
  void invalidBodyReturnsFields() throws Exception {
    mvc.perform(
            post("/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.quantity").exists());
  }

  @Test
  void brokenJsonReturnsMalformedRequest() throws Exception {
    mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{oops"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void unexpectedErrorHidesDetails() throws Exception {
    mvc.perform(get("/probe/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Something went wrong"));
  }

  @Test
  void askingForAFormatTheEndpointCannotGiveIs406NotA500() throws Exception {
    mvc.perform(get("/probe/pdf").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotAcceptable())
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
  }

  record QuantityRequest(@Min(value = 1, message = "must be 1 or more") int quantity) {}

  @RestController
  static class ProbeController {

    @GetMapping("/probe/not-found")
    String notFound() {
      throw new NotFoundException("Product 7 not found");
    }

    @PostMapping("/probe/validate")
    String validate(@Valid @RequestBody QuantityRequest request) {
      return "ok";
    }

    @GetMapping(value = "/probe/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    byte[] pdf() {
      return new byte[] {'%', 'P', 'D', 'F'};
    }

    @GetMapping("/probe/boom")
    String boom() {
      throw new IllegalStateException("secret internal detail");
    }
  }
}
