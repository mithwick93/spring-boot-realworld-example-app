# Input Validation Task Breakdown

## Summary

This codebase validates external input primarily via `javax.validation` (Bean Validation) annotations such as `@NotBlank`, `@Email`, and `@Size` declared directly on request DTO fields, which are package-private classes nested at the bottom of each controller file rather than in separate files. Validation is triggered by annotating the controller method parameter with `@Valid @RequestBody`, and failures are caught centrally by `CustomizeExceptionHandler`, which returns a `{"errors": {"field": ["msg"]}}` shape (auth-related exceptions instead return a flat `{"message": "..."}`). Endpoints that only take path variables (e.g. slugs, usernames) with no request body typically skip a DTO entirely and rely on the resolved domain entity existing (via a lookup that 404s) rather than Bean Validation.

## Analysis Tasks

- TASK-1: src/main/java/io/spring/api/UsersApi.java - Do the registration and login request DTOs (RegistrationParam, LoginParam) validate required fields, email format, and enforce length bounds on user-supplied strings?
- TASK-2: src/main/java/io/spring/api/CurrentUserApi.java - Does the update-current-user request DTO validate optional/partial fields (email format, size bounds) before they are persisted?
- TASK-3: src/main/java/io/spring/api/ArticlesApi.java, src/main/java/io/spring/api/ArticleApi.java - Are article creation/update request DTOs validated with Bean Validation, and are list/feed query parameters (offset, limit, tag, author, favorited) checked or bounded before use?
- TASK-4: src/main/java/io/spring/api/CommentsApi.java - Is the new-comment request DTO validated for required/blank content, and are the article slug and comment id path variables checked before use?
- TASK-5: src/main/java/io/spring/api/ArticleFavoriteApi.java, src/main/java/io/spring/api/ArticleReportApi.java, src/main/java/io/spring/api/ProfileApi.java - For these path-variable-only endpoints (favorite/unfavorite, report, follow/unfollow), is the absence of a request DTO an acceptable design (per the "simple path-variable actions" convention), and are the path variables safely handled?
- TASK-6: src/main/java/io/spring/api/TagsApi.java, src/main/java/io/spring/api/CurrentUserReportsApi.java - Do these endpoints accept any external input at all (query params, path variables), and if so, is it validated or otherwise safe to leave unvalidated?
