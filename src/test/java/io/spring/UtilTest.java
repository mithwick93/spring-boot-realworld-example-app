package io.spring;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

public class UtilTest {

  @Test
  public void should_return_empty_string_for_null_body() {
    assertThat(Util.truncateExcerpt(null, 10), is(""));
  }

  @Test
  public void should_return_empty_string_for_empty_body() {
    assertThat(Util.truncateExcerpt("", 10), is(""));
  }

  @Test
  public void should_return_empty_string_when_max_length_is_zero() {
    assertThat(Util.truncateExcerpt("anything", 0), is(""));
  }

  @Test
  public void should_return_empty_string_when_max_length_is_negative() {
    assertThat(Util.truncateExcerpt("anything", -5), is(""));
  }

  @Test
  public void should_return_body_unchanged_when_shorter_than_max_length() {
    assertThat(
        Util.truncateExcerpt("The quick brown fox jumps over the lazy dog", 100),
        is("The quick brown fox jumps over the lazy dog"));
  }

  @Test
  public void should_return_body_unchanged_when_equal_to_max_length() {
    String body = "exact";
    assertThat(Util.truncateExcerpt(body, body.length()), is(body));
  }

  @Test
  public void should_truncate_at_last_whitespace_before_max_length() {
    assertThat(
        Util.truncateExcerpt("The quick brown fox jumps over the lazy dog", 15),
        is("The quick brown..."));
  }

  @Test
  public void should_trim_trailing_whitespace_before_appending_ellipsis() {
    assertThat(
        Util.truncateExcerpt("The quick brown fox jumps over the lazy dog", 12),
        is("The quick..."));
  }

  @Test
  public void should_hard_cut_single_long_word_with_no_whitespace() {
    assertThat(
        Util.truncateExcerpt("Supercalifragilisticexpialidocious", 10), is("Supercalif..."));
  }
}
