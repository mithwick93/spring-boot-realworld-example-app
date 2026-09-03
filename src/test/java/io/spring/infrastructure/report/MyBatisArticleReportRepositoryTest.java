package io.spring.infrastructure.report;

import io.spring.api.exception.DuplicateReportException;
import io.spring.core.report.ArticleReport;
import io.spring.core.report.ArticleReportRepository;
import io.spring.infrastructure.DbTestBase;
import io.spring.infrastructure.repository.MyBatisArticleReportRepository;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({MyBatisArticleReportRepository.class})
public class MyBatisArticleReportRepositoryTest extends DbTestBase {
  @Autowired private ArticleReportRepository articleReportRepository;

  @Autowired
  private io.spring.infrastructure.mybatis.mapper.ArticleReportMapper articleReportMapper;

  @Test
  public void should_save_and_fetch_articleReport_success() {
    ArticleReport articleReport = new ArticleReport("123", "456", "spam");
    articleReportRepository.save(articleReport);
    Assertions.assertNotNull(
        articleReportMapper.find(articleReport.getArticleId(), articleReport.getUserId()));
  }

  @Test
  public void should_throw_duplicate_report_exception_when_reporting_same_article_twice() {
    ArticleReport firstReport = new ArticleReport("123", "456", "spam");
    articleReportRepository.save(firstReport);

    ArticleReport secondReport = new ArticleReport("123", "456", "off-topic");
    Assertions.assertThrows(
        DuplicateReportException.class, () -> articleReportRepository.save(secondReport));

    // the first report is untouched — reason wasn't overwritten by the rejected second attempt
    Assertions.assertEquals(
        "spam", articleReportRepository.find("123", "456").get().getReason());
  }

  @Test
  public void should_allow_different_users_to_report_same_article() {
    articleReportRepository.save(new ArticleReport("123", "456", "spam"));
    articleReportRepository.save(new ArticleReport("123", "789", "off-topic"));

    Assertions.assertTrue(articleReportRepository.find("123", "456").isPresent());
    Assertions.assertTrue(articleReportRepository.find("123", "789").isPresent());
  }

  @Test
  public void should_find_all_reports_filed_by_a_reporter_across_articles() {
    articleReportRepository.save(new ArticleReport("123", "456", "spam"));
    articleReportRepository.save(new ArticleReport("999", "456", "off-topic"));
    articleReportRepository.save(new ArticleReport("123", "789", "abuse"));

    List<ArticleReport> reports = articleReportRepository.findByReporterId("456");

    Assertions.assertEquals(2, reports.size());
    Assertions.assertTrue(reports.stream().allMatch(r -> r.getUserId().equals("456")));
  }

  @Test
  public void should_return_empty_list_for_a_reporter_with_no_reports() {
    Assertions.assertTrue(articleReportRepository.findByReporterId("no-such-user").isEmpty());
  }
}
