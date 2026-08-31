package io.spring.application.article;

import io.spring.Util;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraintvalidation.SupportedValidationTarget;
import javax.validation.constraintvalidation.ValidationTarget;
import org.springframework.beans.factory.annotation.Autowired;

@SupportedValidationTarget(ValidationTarget.PARAMETERS)
class DuplicatedArticleUpdateValidator
    implements ConstraintValidator<DuplicatedArticleConstraint, Object[]> {

  @Autowired private ArticleRepository articleRepository;

  @Override
  public boolean isValid(Object[] value, ConstraintValidatorContext context) {
    Article article = (Article) value[0];
    UpdateArticleParam updateArticleParam = (UpdateArticleParam) value[1];
    String title = updateArticleParam.getTitle();
    if (Util.isEmpty(title)) {
      return true;
    }
    String newSlug = Article.toSlug(title);
    if (newSlug.equals(article.getSlug())) {
      return true;
    }
    boolean duplicated =
        articleRepository
            .findBySlug(newSlug)
            .filter(existing -> !existing.getId().equals(article.getId()))
            .isPresent();
    if (!duplicated) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context
        .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
        .addParameterNode(1)
        .addPropertyNode("title")
        .addConstraintViolation();
    return false;
  }
}
