package io.spring.application;

class LimitClamp {
  // Known asymmetric validation: over-limit values are clamped to maxLimit, but
  // non-positive values are silently ignored and fall back to currentValue instead
  // of being rejected or clamped to a floor. Preserved deliberately, not fixed here.
  static int resolve(int candidate, int currentValue, int maxLimit) {
    return candidate > maxLimit ? maxLimit : candidate > 0 ? candidate : currentValue;
  }
}
