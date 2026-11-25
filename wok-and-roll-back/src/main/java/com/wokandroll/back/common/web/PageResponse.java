package com.wokandroll.back.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** One page of a list (api-contract.md section 1.1). No total count, on purpose. */
public record PageResponse<T>(
    List<T> items,
    @Schema(
            example = "b2Zmc2V0OjIw",
            nullable = true,
            description = "Copy it into pageToken for the next page; null on the last page")
        String nextPageToken) {

  /**
   * Builds the page from a list read with {@code pageSize + 1} rows: the extra row only says that
   * there is a next page and is not returned.
   */
  public static <T> PageResponse<T> of(List<T> rowsPlusOne, PageRequest page) {
    boolean hasMore = rowsPlusOne.size() > page.pageSize();
    List<T> items = hasMore ? rowsPlusOne.subList(0, page.pageSize()) : rowsPlusOne;
    return new PageResponse<>(List.copyOf(items), page.nextToken(items.size(), hasMore));
  }
}
