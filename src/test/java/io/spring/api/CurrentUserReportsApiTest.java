package io.spring.api;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.spring.JacksonCustomizations;
import io.spring.api.security.WebSecurityConfig;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.report.ArticleReport;
import io.spring.core.report.ArticleReportRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CurrentUserReportsApi.class)
@Import({WebSecurityConfig.class, JacksonCustomizations.class})
public class CurrentUserReportsApiTest extends TestWithCurrentUser {
  @Autowired private MockMvc mvc;

  @MockBean private ArticleReportRepository articleReportRepository;

  @MockBean private ArticleRepository articleRepository;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    RestAssuredMockMvc.mockMvc(mvc);
  }

  @Test
  public void should_list_reports_filed_by_the_current_user_across_articles() throws Exception {
    Article articleOne =
        new Article("title one", "desc", "body", Arrays.asList("java"), user.getId());
    Article articleTwo =
        new Article("title two", "desc", "body", Arrays.asList("java"), user.getId());
    ArticleReport reportOne = new ArticleReport(articleOne.getId(), user.getId(), "spam");
    ArticleReport reportTwo = new ArticleReport(articleTwo.getId(), user.getId(), "off-topic");

    when(articleReportRepository.findByReporterId(eq(user.getId())))
        .thenReturn(Arrays.asList(reportOne, reportTwo));
    when(articleRepository.findById(eq(articleOne.getId()))).thenReturn(Optional.of(articleOne));
    when(articleRepository.findById(eq(articleTwo.getId()))).thenReturn(Optional.of(articleTwo));

    given()
        .header("Authorization", "Token " + token)
        .when()
        .get("/user/reports")
        .then()
        .statusCode(200)
        .body("reports.size()", equalTo(2))
        .body("reports[0].articleSlug", equalTo(articleOne.getSlug()))
        .body("reports[1].articleSlug", equalTo(articleTwo.getSlug()));
  }

  @Test
  public void should_return_empty_list_when_current_user_has_no_reports() throws Exception {
    when(articleReportRepository.findByReporterId(eq(user.getId())))
        .thenReturn(Collections.emptyList());

    given()
        .header("Authorization", "Token " + token)
        .when()
        .get("/user/reports")
        .then()
        .statusCode(200)
        .body("reports.size()", equalTo(0));
  }

  @Test
  public void should_return_401_when_unauthenticated() throws Exception {
    given().when().get("/user/reports").then().statusCode(401);
  }
}
