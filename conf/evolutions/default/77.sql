# --- !Ups

create table mention (
  id                        bigint not null,
  resource_type             varchar(16),
  resource_id               varchar(255),
  user_id                   bigint,
  constraint ck_mention_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','COMMIT','COMMENT_THREAD','REVIEW_COMMENT','NOT_A_RESOURCE')),
  constraint pk_mention primary key (id))
;

create sequence mention_seq;
alter table mention add constraint fk_mention_user_13 foreign key (user_id) references n4user (id) on delete restrict on update restrict;
create index ix_mention_user_30 on mention (user_id);
create index ix_mention_resource_type_31 on mention (resource_type);

--- issues mentioning someone
INSERT INTO mention (
    id,
    resource_type,
    resource_id,
    user_id
)
SELECT
    nextval('mention_seq'),
    'ISSUE_POST',
    issue.id,
    n4user.id
FROM n4user, issue
WHERE issue.body LIKE CONCAT(CONCAT('%', n4user.login_id), '%');

--- issue comments mentioning someone
INSERT INTO mention (
    id,
    resource_type,
    resource_id,
    user_id
)
SELECT
    nextval('mention_seq'),
    'ISSUE_COMMENT',
    issue_comment.id,
    n4user.id
FROM n4user, issue_comment
WHERE issue_comment.contents LIKE CONCAT(CONCAT('%', n4user.login_id), '%');

# --- !Downs

drop table if exists mention;
drop sequence if exists mention_seq;
