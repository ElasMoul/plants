package com.plantpal.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.export.service.CsvEscaper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CsvEscaper - Unit Tests")
class CsvEscaperTest {

  @Test
  @DisplayName("quotes commas, quotes and newlines")
  void quotesSpecialCharacters() {
    assertThat(CsvEscaper.escape("a,b")).isEqualTo("\"a,b\"");
    assertThat(CsvEscaper.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    assertThat(CsvEscaper.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
  }

  @Test
  @DisplayName("neutralises leading formula characters in text")
  void neutralisesFormulas() {
    assertThat(CsvEscaper.escape("=SUM(A1)")).isEqualTo("\"'=SUM(A1)\"");
    assertThat(CsvEscaper.escape("+1")).isEqualTo("\"'+1\"");
    assertThat(CsvEscaper.escape("-cmd")).isEqualTo("\"'-cmd\"");
    assertThat(CsvEscaper.escape("@home")).isEqualTo("\"'@home\"");
  }

  @Test
  @DisplayName("keeps numbers and nulls intact")
  void keepsNumbersAndNulls() {
    assertThat(CsvEscaper.escape(-1.5)).isEqualTo("-1.5");
    assertThat(CsvEscaper.escape(null)).isEmpty();
    assertThat(CsvEscaper.row(Arrays.asList("a", null, 3))).isEqualTo("a,,3\r\n");
    assertThat(CsvEscaper.row(List.of("x"))).isEqualTo("x\r\n");
  }
}
