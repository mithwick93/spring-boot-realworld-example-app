package io.spring.api;

import com.fasterxml.jackson.annotation.JsonRootName;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.report.ArticleReport;
import io.spring.core.report.ArticleReportRepository;
import io.spring.core.user.User;
import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/articles/{slug}/report")
@AllArgsConstructor
public class ArticleReportApi {
  private ArticleRepository articleRepository;
  private ArticleReportRepository articleReportRepository;

  @PostMapping
  public ResponseEntity<?> reportArticle(
      @PathVariable("slug") String slug,
      @AuthenticationPrincipal User user,
      @Valid @RequestBody ReportArticleParam reportArticleParam) {
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    ArticleReport articleReport =
        new ArticleReport(article.getId(), user.getId(), reportArticleParam.getReason());
    articleReportRepository.save(articleReport);
    return ResponseEntity.status(201).body(reportResponse(article.getSlug(), articleReport));
  }

  private Map<String, Object> reportResponse(String slug, ArticleReport articleReport) {
    return new HashMap<String, Object>() {
      {
        put(
            "report",
            new HashMap<String, Object>() {
              {
                put("articleSlug", slug);
                put("reason", articleReport.getReason());
                put("createdAt", articleReport.getCreatedAt());
              }
            });
      }
    };
  }
}

@Getter
@NoArgsConstructor
@JsonRootName("report")
class ReportArticleParam {
  // Custom message required to match spec-2026-08.md AC6 exactly ("can't be blank") — the
  // sibling NewCommentParam/CommentsApi.java convention uses "can't be empty" for the same
  // annotation, which would NOT satisfy AC6's wire contract if copied verbatim. Caught while
  // implementing this task (2.5), not assumed from the pattern.
  @NotBlank(message = "can't be blank")
  // No custom message here: the default Bean Validation @Size message is literally
  // "size must be between {min} and {max}" -> "size must be between 0 and 1000", which already
  // matches AC7 exactly.
  @Size(max = 1000)
  private String reason;
}
