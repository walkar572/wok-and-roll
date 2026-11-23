package com.wokandroll.back.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Simple check that the app is running. It also makes sure Swagger shows something. */
@RestController
@RequestMapping("/api")
@Tag(name = "Health")
public class HealthController {

  @GetMapping("/health")
  @Operation(summary = "Application status")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }
}
