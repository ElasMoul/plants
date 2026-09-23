package com.plantpal.localization;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TranslationTextExtractorTest {
  @Test
  void extractsOnlyProseAndKeepsTheSourceUntouched() throws Exception {
    var mapper = new ObjectMapper();
    var data =
        mapper.readTree(
            """
        {"id":42,"scientificName":"Monstera deliciosa","nickname":"My fern",
         "notes":"Private note","status":"COMPLETED","frequencyDays":7,
         "description":"A climbing plant.","healthNotes":"Healthy leaves.",
         "careCards":[{"title":"Watering","type":"WATERING","detail":"Use 5 ml.",
           "actionPlan":{"steps":[{"instruction":"Wait 7 days.","dueOffsetDays":7}]}}]}
        """);
    var before = data.deepCopy();
    assertThat(new TranslationTextExtractor().extract(data))
        .containsExactlyInAnyOrder(
            "A climbing plant.", "Healthy leaves.", "Watering", "Use 5 ml.", "Wait 7 days.");
    assertThat(data).isEqualTo(before);
  }
}
