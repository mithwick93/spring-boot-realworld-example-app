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

  // Non-positive values are floor-clamped to 1, symmetric with the existing clamp-to-MAX_LIMIT
  // behavior for over-limit values, instead of being silently replaced by the default.
  @Test
  public void should_floor_clamp_limit_of_zero_to_one() {
    CursorPageParameter<String> page = new CursorPageParameter<>(null, 0, Direction.NEXT);
    assertThat(page.getLimit(), is(1));
  }

  // Non-positive values are floor-clamped to 1, symmetric with the existing clamp-to-MAX_LIMIT
  // behavior for over-limit values, instead of being silently replaced by the default.
  @Test
  public void should_floor_clamp_negative_limit_to_one() {
    CursorPageParameter<String> page = new CursorPageParameter<>(null, -1, Direction.NEXT);
    assertThat(page.getLimit(), is(1));
  }
}
