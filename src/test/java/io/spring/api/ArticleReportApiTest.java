package io.spring.api;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.spring.JacksonCustomizations;
import io.spring.api.exception.DuplicateReportException;
import io.spring.api.security.WebSecurityConfig;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.report.ArticleReport;
import io.spring.core.report.ArticleReportRepository;
import io.spring.core.user.User;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ArticleReportApi.class)
@Import({WebSecurityConfig.class, JacksonCustomizations.class})
public class ArticleReportApiTest extends TestWithCurrentUser {
  @Autowired private MockMvc mvc;

  @MockBean private ArticleRepository articleRepository;

  @MockBean private ArticleReportRepository articleReportRepository;

  private Article article;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    RestAssuredMockMvc.mockMvc(mvc);
    article = new Article("title", "desc", "body", Arrays.asList("java"), user.getId());
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));
  }

  private Map<String, Object> reportParam(String reason) {
    return new HashMap<String, Object>() {
      {
        put(
            "report",
            new HashMap<String, Object>() {
              {
                put("reason", reason);
              }
            });
      }
    };
  }

  @Test
  public void should_report_an_article_success() throws Exception {
    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(reportParam("This article contains plagiarized content from another site."))
        .when()
        .post("/articles/{slug}/report", article.getSlug())
        .then()
        .statusCode(201)
        .body("report.articleSlug", equalTo(article.getSlug()))
        .body(
            "report.reason", equalTo("This article contains plagiarized content from another site."));

    verify(articleReportRepository).save(any(ArticleReport.class));
  }

  @Test
  public void should_return_422_when_article_already_reported() throws Exception {
    doThrow(new DuplicateReportException())
        .when(articleReportRepository)
        .save(any(ArticleReport.class));

    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(reportParam("spam"))
        .when()
        .post("/articles/{slug}/report", article.getSlug())
        .then()
        .statusCode(422)
        .body("message", equalTo("article already reported"));
  }

  @Test
  public void should_allow_a_different_user_to_report_the_same_article() throws Exception {
    User anotherUser = new User("jane@example.com", "jane", "123", "", "");
    when(userRepository.findByUsername(eq(anotherUser.getUsername())))
        .thenReturn(Optional.of(anotherUser));
    when(userRepository.findById(eq(anotherUser.getId())))
        .thenReturn(Optional.of(anotherUser));
    String anotherToken = "another-token";
    when(jwtService.getSubFromToken(eq(anotherToken))).thenReturn(Optional.of(anotherUser.getId()));

    given()
        .contentType("application/json")
        .header("Authorization", "Token " + anotherToken)
        .body(reportParam("off-topic"))
        .when()
        .post("/articles/{slug}/report", article.getSlug())
        .then()
        .statusCode(201);

    verify(articleReportRepository).save(any(ArticleReport.class));
  }

  @Test
  public void should_return_404_when_article_does_not_exist() throws Exception {
    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(reportParam("spam"))
        .when()
        .post("/articles/{slug}/report", "no-such-article")
        .then()
        .statusCode(404);
  }

  @Test
  public void should_return_401_when_unauthenticated() throws Exception {
    given()
        .contentType("application/json")
        .body(reportParam("spam"))
        .when()
        .post("/articles/{slug}/report", article.getSlug())
        .then()
        .statusCode(401);
  }

  @Test
  public void should_return_422_when_reason_is_blank() throws Exception {
    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(reportParam(""))
        .when()
        .post("/articles/{slug}/report", article.getSlug())
        .then()
        .statusCode(422)
        .body("errors.reason[0]", equalTo("can't be blank"));
  }

  @Test
  public void should_return_422_when_reason_exceeds_max_length() throws Exception {
    String tooLong = "a".repeat(1001);

    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(reportParam(tooLong))
        .when()
        .post("/articles/{slug}/report", article.getSlug())
        .then()
        .statusCode(422)
        .body("errors.reason[0]", equalTo("size must be between 0 and 1000"));
  }
}
