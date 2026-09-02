package io.spring.infrastructure.mybatis.mapper;

import io.spring.core.report.ArticleReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArticleReportMapper {
  ArticleReport find(@Param("articleId") String articleId, @Param("userId") String userId);

  void insert(@Param("articleReport") ArticleReport articleReport);
}
