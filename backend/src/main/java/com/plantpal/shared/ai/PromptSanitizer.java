package com.plantpal.shared.ai;

/** Utility for delimiting user-supplied content in AI prompts to prevent injection. */
public final class PromptSanitizer {

  private PromptSanitizer() {}

  /**
   * Wraps user-supplied text in XML delimiters so the model sees it as data, not instructions.
   * Strips any pre-existing delimiter tags the caller might have injected to escape the boundary.
   */
  public static String delimit(String userContent) {
    if (userContent == null) {
      return "<user_input></user_input>";
    }
    String sanitized =
        userContent
            .replace("<user_input>", "")
            .replace("</user_input>", "")
            .replace("<system>", "")
            .replace("</system>", "");
    return "<user_input>" + sanitized + "</user_input>";
  }
}
