package com.wokandroll.back.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger / OpenAPI settings. UI: /swagger-ui.html, JSON: /v3/api-docs. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI wokAndRollOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Wok & Roll API")
                .version("v1")
                .description(
                    "Menu with regional prices, cart quote, orders, booking and reports. "
                        + "Prices are calculated only on the backend (pricing service)."));
  }
}
