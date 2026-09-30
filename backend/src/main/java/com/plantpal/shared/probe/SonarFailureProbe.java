package com.plantpal.shared.probe;

public class SonarFailureProbe {

  public int branchy(int a, int b, int c) {
    // TODO probe: deliberately uncovered code to make the quality gate fail
    if (a > 0) {
      if (b > 0) {
        return c > 0 ? a + b + c : a + b;
      }
      return a;
    }
    if (b > 0) {
      return c > 0 ? b + c : b;
    }
    if (c > 0) {
      return c;
    }
    return a * b * c + a - b + c;
  }
}
