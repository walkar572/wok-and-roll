package com.wokandroll.back.cafe.service;

import com.wokandroll.back.cafe.dto.CafeListResponse;
import com.wokandroll.back.cafe.dto.CafeResponse;
import com.wokandroll.back.cafe.repository.CafeRepository;
import com.wokandroll.back.cafe.repository.CafeRepository.CafeRow;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/** Cafés for checkout and booking (#146): pickup points and booking venues of a country. */
@Service
public class CafeService {

  /** A clock time in the API is HH:mm, 24 h (api-contract.md section 1). */
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final CafeRepository repository;

  public CafeService(CafeRepository repository) {
    this.repository = repository;
  }

  public CafeListResponse getCafes(String countryCode) {
    List<CafeResponse> items =
        repository.findAllActive(countryCode).stream().map(CafeService::toResponse).toList();
    return new CafeListResponse(items);
  }

  private static CafeResponse toResponse(CafeRow c) {
    return new CafeResponse(
        c.id(),
        c.countryCode(),
        c.name(),
        c.address(),
        c.phone(),
        c.opensAt().format(HH_MM),
        c.closesAt().format(HH_MM),
        c.timeZone(),
        c.latitude(),
        c.longitude());
  }
}
