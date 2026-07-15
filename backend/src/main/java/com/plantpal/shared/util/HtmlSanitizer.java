package com.plantpal.shared.util;

import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Strips HTML/script content from free-text user input before it's persisted — plant nickname,
 * notes, location (T-DEPLOY.3). Applied in the service layer (PlantServiceImpl), never the
 * controller or entity, per house convention.
 *
 * <p>These fields are plain-text labels, not rich content, so the policy allows no markup at all —
 * {@link PolicyFactory#sanitize(String)} drops any disallowed tag (and its content, for something
 * like {@code <script>...</script>}) as a structural element, which is exactly what neutralizes an
 * injection attempt. What's left over is real character data, but the library always HTML-escapes
 * it defensively as it writes output (so the result is safe to re-embed in any HTML context) —
 * including encoding every non-ASCII character, emoji included, as a numeric character reference
 * (e.g. {@code &#x1f33f;}). That's correct for HTML embedding but wrong for a plain-text DB column,
 * so the second step below reverses exactly that escaping. It never resurrects anything the policy
 * actually stripped — those characters (e.g. real {@code <}/{@code >} that formed a tag) were
 * removed as elements, not encoded as text, so there's nothing left for the unescape step to act
 * on.
 */
public final class HtmlSanitizer {

  private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();

  // The sanitizer's Encoding class only ever emits these five escape forms for plain text —
  // matching that exactly (rather than pulling in a general-purpose HTML-entity-decoding
  // dependency) keeps this a precise inverse of what sanitize() just did, nothing more.
  private static final Pattern HEX_CHAR_REF = Pattern.compile("&#x([0-9a-fA-F]+);");
  private static final Pattern DECIMAL_CHAR_REF = Pattern.compile("&#(\\d+);");

  private HtmlSanitizer() {}

  /** Null-safe: returns null for null input so callers can keep their existing null checks. */
  public static String sanitize(String input) {
    if (input == null) {
      return null;
    }
    String sanitized = PLAIN_TEXT_POLICY.sanitize(input);
    return unescapeEntities(sanitized);
  }

  private static String unescapeEntities(String html) {
    String result = HEX_CHAR_REF.matcher(html).replaceAll(m -> codePointToString(m.group(1), 16));
    result = DECIMAL_CHAR_REF.matcher(result).replaceAll(m -> codePointToString(m.group(1), 10));
    return result
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        // Must run last: an original literal "&" is what produced this escape in the first
        // place, so unescaping it first would corrupt any of the other entities above.
        .replace("&amp;", "&");
  }

  private static String codePointToString(String digits, int radix) {
    return new String(Character.toChars(Integer.parseInt(digits, radix)));
  }
}
