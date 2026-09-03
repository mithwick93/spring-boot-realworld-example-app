package io.spring.api;

import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.report.ArticleReport;
import io.spring.core.report.ArticleReportRepository;
import io.spring.core.user.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// openspec/changes/surface-article-reports-to-admins/ — self-visibility only, no admin
// visibility (this codebase has no authorization/role concept; see that change's proposal.md).
@RestController
@RequestMapping(path = "/user/reports")
@AllArgsConstructor
public class CurrentUserReportsApi {
  private ArticleReportRepository articleReportRepository;
  private ArticleRepository articleRepository;

  @GetMapping
  public ResponseEntity<?> myReports(@AuthenticationPrincipal User user) {
    List<ArticleReport> reports = articleReportRepository.findByReporterId(user.getId());
    // One articleRepository.findById() lookup per report to resolve articleId -> slug (the
    // response shape mirrors ArticleReportApi's create response, which is slug-keyed, not
    // id-keyed). Not batched -- not discovered as a concern at design time (design.md didn't
    // address it), only noticed here at implementation. Accepted as-is: this endpoint returns
    // one reporter's own reports, an expected-tiny list, not a paginated admin-scale query, so
    // an N+1 pattern here has no realistic performance impact at this scale. Worth revisiting
    // only if this capability's scope ever grows beyond self-visibility.
    List<Map<String, Object>> reportData =
        reports.stream().map(this::reportResponseEntry).collect(Collectors.toList());
    return ResponseEntity.ok(
        new HashMap<String, Object>() {
          {
            put("reports", reportData);
          }
        });
  }

  private Map<String, Object> reportResponseEntry(ArticleReport articleReport) {
    String slug =
        articleRepository
            .findById(articleReport.getArticleId())
            .map(Article::getSlug)
            .orElse(null);
    return new HashMap<String, Object>() {
      {
        put("articleSlug", slug);
        put("reason", articleReport.getReason());
        put("createdAt", articleReport.getCreatedAt());
      }
    };
  }
}
