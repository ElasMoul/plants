package com.plantpal.user.service;

public interface UsageService {
  void requirePlantCapacity(Long userId);

  void consumeAi(Long userId, boolean scan);

  Usage usage(Long userId);

  record Usage(long plants, long scansToday, long aiToday) {}
}
