package io.spring.application;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.spring.application.CursorPager.Direction;
import org.junit.jupiter.api.Test;

public class CursorPageParameterTest {

  @Test
  public void should_use_given_limit_when_within_valid_range() {
    CursorPageParameter<String> page = new CursorPageParameter<>(null, 50, Direction.NEXT);
    assertThat(page.getLimit(), is(50));
  }

  @Test
  public void should_accept_limit_of_one() {
    CursorPageParameter<String> page = new CursorPageParameter<>(null, 1, Direction.NEXT);
    assertThat(page.getLimit(), is(1));
  }

  @Test
  public void should_accept_limit_exactly_at_max_limit_boundary() {
    CursorPageParameter<String> page = new CursorPageParameter<>(null, 1000, Direction.NEXT);
    assertThat(page.getLimit(), is(1000));
  }

  @Test
  public void should_clamp_limit_one_above_max_limit_to_max_limit() {
    CursorPageParameter<String> page = new CursorPageParameter<>(null, 1001, Direction.NEXT);
    assertThat(page.getLimit(), is(1000));
  }

  @Test
  public void should_clamp_very_large_limit_to_max_limit() {
    CursorPageParameter<String> page =
        new CursorPageParameter<>(null, Integer.MAX_VALUE, Direction.NEXT);
    assertThat(page.getLimit(), is(1000));
  }

  // Known asymmetric validation bug, preserved deliberately (not fixed here): an over-limit
  // value is clamped to MAX_LIMIT, but a non-positive value is silently ignored and falls back
  // to the field's existing default (20) instead of being rejected or clamped to a floor.
  @Test
  public void should_fall_back_to_default_limit_when_limit_is_zero() {
    CursorPageParameter<String> page = new CursorPageParameter<>(null, 0, Direction.NEXT);
    assertThat(page.getLimit(), is(20));
  }

  // Known asymmetric validation bug, preserved deliberately (not fixed here): an over-limit
  // value is clamped to MAX_LIMIT, but a non-positive value is silently ignored and falls back
  // to the field's existing default (20) instead of being rejected or clamped to a floor.
  @Test
  public void should_fall_back_to_default_limit_when_limit_is_negative() {
    CursorPageParameter<String> page = new CursorPageParameter<>(null, -1, Direction.NEXT);
    assertThat(page.getLimit(), is(20));
  }
}
