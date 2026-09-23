package com.plantpal.localization;

import java.util.Map;

public record TranslationResult(
    String id, String language, String status, Map<String, String> texts) {}
