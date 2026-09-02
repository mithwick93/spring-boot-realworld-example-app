create table article_report (
  article_id varchar(255) not null,
  user_id varchar(255) not null,
  reason varchar(1000) not null,
  created_at TIMESTAMP NOT NULL,
  primary key (article_id, user_id)
);
