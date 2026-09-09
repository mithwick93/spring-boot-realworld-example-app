package io.spring.application;

import io.spring.application.CursorPager.Direction;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CursorPageParameter<T> {
  private static final int MAX_LIMIT = 1000;
  private int limit = 20;
  private T cursor;
  private Direction direction;

  public CursorPageParameter(T cursor, int limit, Direction direction) {
    setLimit(limit);
    setCursor(cursor);
    setDirection(direction);
  }

  public boolean isNext() {
    return direction == Direction.NEXT;
  }

  public int getQueryLimit() {
    return limit + 1;
  }

  private void setCursor(T cursor) {
    this.cursor = cursor;
  }

  private void setLimit(int limit) {
    this.limit = resolveLimit(limit, this.limit);
  }

  private int resolveLimit(int candidate, int currentValue) {
    return candidate > MAX_LIMIT ? MAX_LIMIT : candidate > 0 ? candidate : currentValue;
  }
}
