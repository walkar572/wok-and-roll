package com.wokandroll.back.report.service;

import com.wokandroll.back.report.repository.SalesReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Builds a ReportService with a fixed clock for tests in other packages. */
public final class ReportServiceForTests {

  private ReportServiceForTests() {}

  public static ReportService at(String instant, SalesReportRepository repository) {
    return new ReportService(repository, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }
}
