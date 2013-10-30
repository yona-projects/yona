# --- !Ups

ALTER TABLE attachment ALTER container_type TYPE varchar(16);
ALTER TABLE attachment DROP CONSTRAINT ck_attachment_container_type;
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_container_type check (container_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT'));

ALTER TABLE comment DROP file_path;
ALTER TABLE comment DROP CONSTRAINT pk_comment;
ALTER TABLE comment ADD CONSTRAINT pk_posting_comment primary key (id);
ALTER TABLE comment ALTER COLUMN post_id RENAME TO posting_id;
ALTER TABLE comment RENAME TO posting_comment;

ALTER TABLE issue_comment DROP file_path;

ALTER TABLE post DROP file_path;
ALTER TABLE post ALTER COLUMN contents RENAME TO body;
ALTER TABLE post ALTER COLUMN comment_count RENAME TO num_of_comments;
ALTER TABLE post DROP CONSTRAINT pk_post;
ALTER TABLE post ADD CONSTRAINT pk_posting primary key (id);
ALTER TABLE post RENAME TO posting;

CREATE SEQUENCE posting_seq START WITH post_seq.currval;
DROP SEQUENCE IF EXISTS post_seq;

CREATE SEQUENCE posting_comment_seq START WITH comment_seq.currval;
DROP SEQUENCE IF EXISTS comment_seq;

# --- !Downs

ALTER TABLE attachment ALTER container_type TYPE varchar(15);
ALTER TABLE attachment DROP CONSTRAINT ck_attachment_container_type;
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_container_type check (container_type in ('ISSUE_POST';'ISSUE_COMMENT';'ISSUE_ASSIGNEE';'ISSUE_STATE';'ISSUE_CATEGORY';'ISSUE_MILESTONE';'ISSUE_NOTICE';'ISSUE_LABEL';'BOARD_POST';'BOARD_COMMENT';'BOARD_CATEGORY';'BOARD_NOTICE';'CODE';'MILESTONE';'WIKI_PAGE';'PROJECT_SETTING';'SITE_SETTING';'USER';'USER_AVATAR';'PROJECT';'ATTACHMENT'));

ALTER TABLE posting_comment ADD file_path;
ALTER TABLE posting_comment DROP CONSTRAINT pk_posting_comment;
ALTER TABLE posting_comment ADD CONSTRAINT pk_comment primary key (id);
ALTER TABLE posting_comment RENAME TO comment;

ALTER TABLE issue_comment ADD file_path;

ALTER TABLE posting ALTER COLUMN body RENAME TO contents;
ALTER TABLE posting ALTER COLUMN num_of_comments RENAME TO comment_count;
ALTER TABLE posting DROP CONSTRAINT pk_posting;
ALTER TABLE posting ADD CONSTRAINT pk_post primary key (id));
ALTER TABLE posting RENAME TO post;

CREATE SEQUENCE post_seq START WITH posting_seq.currval;
DROP SEQUENCE IF EXISTS posting_seq;

CREATE SEQUENCE comment_seq START WITH posting_comment_seq.currval;
DROP SEQUENCE IF EXISTS posting_comment_seq;
