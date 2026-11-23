package com.wokandroll.back.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * SHA-256 of an order or booking request, saved as {@code request_hash}. The same key with the same
 * hash is a replay; the same key with another hash is {@code IDEMPOTENCY_KEY_REUSED}
 * (api-contract.md §3.9). The request is written as JSON with sorted properties, so the hash does
 * not depend on the order of the fields in the client's text.
 */
@Component
public class RequestHasher {

  private final ObjectMapper mapper =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .build();

  /** 64 hex characters, lower case: fits {@code CHAR(64)}. */
  public String hash(Object request) {
    try {
      byte[] json = mapper.writeValueAsBytes(request);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (JsonProcessingException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot hash the request", e);
    }
  }
}
