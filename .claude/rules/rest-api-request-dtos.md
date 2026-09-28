---
paths:
  - "src/main/java/io/spring/api/ArticleReportApi.java"
  - "src/main/java/io/spring/api/CommentsApi.java"
---

# REST request DTO shape

- A request DTO for a REST endpoint is a package-private class declared at the bottom of
  the controller file it belongs to, after the public controller class's closing brace —
  never in its own file.
- The DTO class carries exactly these three annotations (any order): `@Getter`,
  `@NoArgsConstructor`, and `@JsonRootName("<key>")`, where `<key>` matches the JSON key the
  controller wraps its response body in (e.g. `@JsonRootName("comment")` on a DTO for an
  endpoint whose response is `{"comment": {...}}`).
- Every required string field on the DTO carries `@NotBlank` from
  `javax.validation.constraints` with an explicit `message` argument — never the library's
  default message for a blank value.
