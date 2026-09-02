package io.spring.core.report;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = {"articleId", "userId"})
public class ArticleReport {
  private String articleId;
  private String userId;
  private String reason;
  private DateTime createdAt;

  public ArticleReport(String articleId, String userId, String reason) {
    this.articleId = articleId;
    this.userId = userId;
    this.reason = reason;
    this.createdAt = new DateTime();
  }
}
