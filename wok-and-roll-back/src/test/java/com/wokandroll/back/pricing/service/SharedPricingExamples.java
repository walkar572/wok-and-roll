package com.wokandroll.back.pricing.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/** Reads the shared file wok-and-roll-qa/test-data/pricing-examples.json (the team's examples). */
final class SharedPricingExamples {

  private SharedPricingExamples() {}

  /** Arguments (id, example) for a parameterized test, one per example of the file. */
  static Stream<Arguments> all() throws IOException {
    ObjectMapper mapper =
        new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    List<Arguments> arguments = new ArrayList<>();
    for (JsonNode example : mapper.readTree(file().toFile())) {
      arguments.add(Arguments.of(example.get("id").asText(), example));
    }
    return arguments.stream();
  }

  /** Maven runs from wok-and-roll-back; an IDE may run from the repo root. Try both. */
  private static Path file() {
    Path fromModule = Path.of("..", "wok-and-roll-qa", "test-data", "pricing-examples.json");
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    return Path.of("wok-and-roll-qa", "test-data", "pricing-examples.json");
  }
}
