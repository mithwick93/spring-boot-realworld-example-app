package io.spring.infrastructure.repository;

import io.spring.api.exception.DuplicateReportException;
import io.spring.core.report.ArticleReport;
import io.spring.core.report.ArticleReportRepository;
import io.spring.infrastructure.mybatis.mapper.ArticleReportMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Repository;
import org.sqlite.SQLiteException;

@Repository
public class MyBatisArticleReportRepository implements ArticleReportRepository {
  private ArticleReportMapper mapper;

  @Autowired
  public MyBatisArticleReportRepository(ArticleReportMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(ArticleReport articleReport) {
    // No check-then-insert here on purpose (unlike MyBatisArticleFavoriteRepository) — a
    // duplicate report must be rejected, not silently no-op'd, so the composite primary key
    // (article_id, user_id) is the actual enforcement mechanism. This catch translates the
    // resulting DB-level violation into a report-specific exception rather than letting a raw
    // DataIntegrityViolationException escape, which would otherwise collide with the generic
    // DataIntegrityViolationException handler added for the signup race fix (homework 2.2) and
    // return that feature's error message instead of this one's.
    //
    // Two catch blocks, not one: this project's datasource is SQLite (application.properties),
    // and Spring's default SQLErrorCodesTranslator has no SQLite-specific error codes, so a
    // UNIQUE/PRIMARY KEY violation from the xerial driver comes back as UncategorizedSQLException,
    // not DataIntegrityViolationException — confirmed by running this against the real test DB
    // rather than assumed. DataIntegrityViolationException is kept as well in case this project
    // ever moves to a DB Spring does have error codes for (Postgres/MySQL/etc.), where the
    // standard translation would apply.
    try {
      mapper.insert(articleReport);
    } catch (DataIntegrityViolationException e) {
      throw new DuplicateReportException();
    } catch (UncategorizedSQLException e) {
      if (isConstraintViolation(e)) {
        throw new DuplicateReportException();
      }
      throw e;
    }
  }

  private boolean isConstraintViolation(UncategorizedSQLException e) {
    SQLException sqlException = e.getSQLException();
    return sqlException instanceof SQLiteException
        && ((SQLiteException) sqlException).getResultCode().name().startsWith("SQLITE_CONSTRAINT");
  }

  @Override
  public Optional<ArticleReport> find(String articleId, String userId) {
    return Optional.ofNullable(mapper.find(articleId, userId));
  }

  @Override
  public List<ArticleReport> findByReporterId(String userId) {
    return mapper.findByReporterId(userId);
  }
}
