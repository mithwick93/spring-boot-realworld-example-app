package io.spring.application;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class Page {
  private int offset = 0;
  private int limit = 20;

  public Page(int offset, int limit) {
    setOffset(offset);
    setLimit(limit);
  }

  private void setOffset(int offset) {
    this.offset = offset;
  }

  private void setLimit(int limit) {
    this.limit = limit;
  }
}
