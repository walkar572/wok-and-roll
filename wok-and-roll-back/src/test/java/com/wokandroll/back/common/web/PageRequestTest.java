package com.wokandroll.back.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wokandroll.back.common.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Paging rules of api-contract.md section 1.1. */
class PageRequestTest {

  @Test
  void defaultsToTwentyFromTheStart() {
    PageRequest page = PageRequest.of(null, null);
    assertThat(page.pageSize()).isEqualTo(20);
    assertThat(page.offset()).isZero();
  }

  @Test
  void theNextTokenContinuesAfterThisPage() {
    PageRequest first = PageRequest.of(2, null);
    String token = first.nextToken(2, true);

    PageRequest second = PageRequest.of(2, token);

    assertThat(second.offset()).isEqualTo(2);
    assertThat(first.nextToken(2, false)).isNull();
    assertThat(token).doesNotContain("=", "+", "/");
  }

  @Test
  void pageSizeMustBeFromOneToHundred() {
    assertThat(PageRequest.of(100, null).pageSize()).isEqualTo(100);
    assertThatThrownBy(() -> PageRequest.of(0, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getFields()).containsKey("pageSize"));
    assertThatThrownBy(() -> PageRequest.of(101, null)).isInstanceOf(ApiException.class);
  }

  @Test
  void aBrokenTokenIsAValidationError() {
    for (String token : List.of("broken!", "aGVsbG8", PageRequest.encode(-1))) {
      assertThatThrownBy(() -> PageRequest.of(20, token))
          .isInstanceOf(ApiException.class)
          .satisfies(e -> assertThat(((ApiException) e).getFields()).containsKey("pageToken"));
    }
  }

  @Test
  void aPageReadWithOneExtraRowKnowsThereIsMore() {
    PageRequest page = PageRequest.of(2, null);

    PageResponse<String> full = PageResponse.of(List.of("a", "b", "c"), page);
    PageResponse<String> last = PageResponse.of(List.of("a"), page);

    assertThat(full.items()).containsExactly("a", "b");
    assertThat(full.nextPageToken()).isNotNull();
    assertThat(last.items()).containsExactly("a");
    assertThat(last.nextPageToken()).isNull();
  }
}
