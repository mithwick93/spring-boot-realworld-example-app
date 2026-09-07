package io.spring;

public class Util {
  public static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }

  public static String truncateExcerpt(String body, int maxLength) {
    if (body == null || body.isEmpty() || maxLength <= 0) {
      return "";
    }
    if (body.length() <= maxLength) {
      return body;
    }
    int cutIndex = -1;
    for (int i = maxLength; i >= 0; i--) {
      if (Character.isWhitespace(body.charAt(i))) {
        cutIndex = i;
        break;
      }
    }
    if (cutIndex == -1) {
      return body.substring(0, maxLength) + "...";
    }
    return body.substring(0, cutIndex).replaceAll("\\s+$", "") + "...";
  }
}
