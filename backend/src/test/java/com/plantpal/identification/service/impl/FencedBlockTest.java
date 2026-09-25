package com.plantpal.identification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IdentificationServiceImpl.fencedBlock() — AI JSON fence extraction")
class FencedBlockTest {

  @Test
  @DisplayName("extracts the body of ```json, ``` and ~~~ fences, ignoring surrounding prose")
  void extractsFencedBodies() {
    assertThat(IdentificationServiceImpl.fencedBlock("```json\n{\"a\":1}\n```"))
        .contains("{\"a\":1}");
    assertThat(IdentificationServiceImpl.fencedBlock("Here you go:\n```\n{\"a\":1}\n```\nDone."))
        .contains("{\"a\":1}");
    assertThat(IdentificationServiceImpl.fencedBlock("~~~json {\"a\":1} ~~~"))
        .contains("{\"a\":1}");
    assertThat(IdentificationServiceImpl.fencedBlock("```{\"a\":1}~~~")).contains("{\"a\":1}");
  }

  @Test
  @DisplayName("takes the first closed block when there are several")
  void firstBlockWins() {
    assertThat(IdentificationServiceImpl.fencedBlock("```json {\"a\":1}``` then ```{\"b\":2}```"))
        .contains("{\"a\":1}");
  }

  @Test
  @DisplayName("no fence or an unclosed fence yields nothing")
  void noClosedFence() {
    assertThat(IdentificationServiceImpl.fencedBlock("{\"a\":1}")).isEmpty();
    assertThat(IdentificationServiceImpl.fencedBlock("```json\n{\"a\":")).isEmpty();
  }

  @Test
  @DisplayName("stays linear on a truncated reply full of whitespace (regex took ~19s here)")
  void linearOnUnclosedWhitespace() {
    String truncated = "```json {" + " ".repeat(40_000) + "x";

    Optional<String> result =
        assertTimeoutPreemptively(
            Duration.ofSeconds(1), () -> IdentificationServiceImpl.fencedBlock(truncated));

    assertThat(result).isEmpty();
  }
}
