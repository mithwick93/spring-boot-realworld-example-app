package io.spring.application.article;

import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.Arrays;
import java.util.UUID;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ArticleCommandServiceTest {
  @Autowired private ArticleCommandService articleCommandService;

  @Autowired private ArticleRepository articleRepository;

  @Autowired private UserRepository userRepository;

  private Article articleA;
  private Article articleB;

  @BeforeEach
  public void setUp() {
    String suffix = UUID.randomUUID().toString();
    User user =
        new User(
            "article-command-service-" + suffix + "@test.com",
            "article-command-service-" + suffix,
            "123",
            "",
            "");
    userRepository.save(user);
    articleA =
        new Article("article one " + suffix, "desc", "body", Arrays.asList("java"), user.getId());
    articleRepository.save(articleA);
    articleB =
        new Article("article two " + suffix, "desc", "body", Arrays.asList("spring"), user.getId());
    articleRepository.save(articleB);
  }

  @Test
  public void should_update_article_success_when_title_unchanged() {
    UpdateArticleParam param =
        new UpdateArticleParam(articleB.getTitle(), "new body", "new description");

    Article updated = articleCommandService.updateArticle(articleB, param);

    Assertions.assertEquals("new body", updated.getBody());
    Assertions.assertEquals("new description", updated.getDescription());
    Assertions.assertEquals(articleB.getSlug(), updated.getSlug());
  }

  @Test
  public void should_update_article_success_when_title_renamed() {
    UpdateArticleParam param = new UpdateArticleParam("brand new title", "body", "desc");

    Article updated = articleCommandService.updateArticle(articleB, param);

    Assertions.assertEquals("brand new title", updated.getTitle());
    Assertions.assertEquals(Article.toSlug("brand new title"), updated.getSlug());
  }

  @Test
  public void should_throw_exception_when_title_duplicated_with_another_article() {
    UpdateArticleParam param = new UpdateArticleParam(articleA.getTitle(), "body", "desc");

    Assertions.assertThrows(
        ConstraintViolationException.class,
        () -> articleCommandService.updateArticle(articleB, param));
  }
}
