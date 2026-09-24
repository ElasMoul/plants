package com.plantpal.localization;

import com.plantpal.shared.exception.PlantPalException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps numeric instructions intact during a bounded translation repair. */
final class ProtectedTranslationText {
  private static final Pattern VALUE =
      Pattern.compile(
          "\\d+(?:[.,]\\d+)?(?:\\s*(?:mL|ml|mg|kg|g|L|l|cm|mm|m|ppm|%|°C|°F|tsp|tbsp|oz)(?![a-zA-Z]))?");
  private final List<String> values = new ArrayList<>();
  private final String masked;

  ProtectedTranslationText(String text) {
    masked =
        VALUE
            .matcher(text)
            .replaceAll(
                match -> {
                  values.add(match.group());
                  return "__PP_VALUE_" + (values.size() - 1) + "__";
                });
  }

  String masked() {
    return masked;
  }

  String restore(String text) {
    for (int i = 0; i < values.size(); i++) {
      String token = "__PP_VALUE_" + i + "__";
      int at = text.indexOf(token);
      if (at < 0 || text.indexOf(token, at + token.length()) >= 0)
        throw new PlantPalException("Translation changed protected instructions", 503);
      text = text.replace(token, values.get(i));
    }
    if (text.contains("__PP_VALUE_"))
      throw new PlantPalException("Unexpected translation placeholder", 503);
    return text;
  }
}
