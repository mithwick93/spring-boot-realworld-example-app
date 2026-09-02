package io.spring.api.exception;

public class DuplicateReportException extends RuntimeException {

  public DuplicateReportException() {
    super("article already reported");
  }
}
