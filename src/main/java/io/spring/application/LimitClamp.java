package io.spring.application;

class LimitClamp {
  // Symmetric clamping: over-limit values are clamped to maxLimit, and non-positive
  // values are floor-clamped to 1, instead of being silently replaced by currentValue.
  static int resolve(int candidate, int currentValue, int maxLimit) {
    return candidate > maxLimit ? maxLimit : candidate > 0 ? candidate : 1;
  }
}
