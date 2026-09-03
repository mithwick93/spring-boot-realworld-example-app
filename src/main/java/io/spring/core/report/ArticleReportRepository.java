package io.spring.core.report;

import java.util.List;
import java.util.Optional;

public interface ArticleReportRepository {
  void save(ArticleReport articleReport);

  Optional<ArticleReport> find(String articleId, String userId);

  List<ArticleReport> findByReporterId(String userId);
}
