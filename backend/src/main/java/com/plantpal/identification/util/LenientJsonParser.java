package com.plantpal.identification.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/**
 * Some local models (e.g. llava-phi3 via Ollama) don't reliably emit a single combined JSON object
 * for prompts that ask for one — observed emitting two sibling top-level objects back to back
 * instead, e.g. {@code {"advice":"..."}{"actionPlan":{...}}}, where GitHub Models/DeepSeek-R1
 * reliably emit the single combined object the prompt actually asks for. A plain {@code
 * ObjectMapper.readValue()} throws on that shape, which used to mean the raw, still-JSON-ish text
 * leaked into the UI as the "advice"/description instead of being parsed.
 */
public final class LenientJsonParser {

  private LenientJsonParser() {}

  /**
   * Merges every top-level JSON object found in {@code raw} into one node (later objects' fields
   * win on key collision). Returns {@code null} if no JSON object could be parsed at all — never
   * throws; callers should fall back to their own raw-text handling in that case.
   */
  public static JsonNode mergeConcatenatedObjects(ObjectMapper objectMapper, String raw) {
    try (JsonParser parser = objectMapper.getFactory().createParser(raw)) {
      ObjectNode merged = objectMapper.createObjectNode();
      MappingIterator<JsonNode> values = objectMapper.readValues(parser, JsonNode.class);
      boolean any = false;
      while (values.hasNextValue()) {
        JsonNode node = values.nextValue();
        if (node != null && node.isObject()) {
          merged.setAll((ObjectNode) node);
          any = true;
        }
      }
      return any ? merged : null;
    } catch (IOException e) {
      return null;
    }
  }
}
