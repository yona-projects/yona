# --- !Ups

ALTER TABLE issue ALTER COLUMN date RENAME TO created_date;
ALTER TABLE issue_comment ALTER COLUMN date RENAME TO created_date;
ALTER TABLE posting ALTER COLUMN date RENAME TO created_date;
ALTER TABLE posting_comment ALTER COLUMN date RENAME TO created_date;
ALTER TABLE project ALTER COLUMN date RENAME TO created_date;
ALTER TABLE n4user ALTER COLUMN date RENAME TO created_date;

# --- !Downs

ALTER TABLE issue ALTER COLUMN created_date RENAME TO date;
ALTER TABLE issue_comment ALTER COLUMN created_date RENAME TO date;
ALTER TABLE posting ALTER COLUMN created_date RENAME TO date;
ALTER TABLE posting_comment ALTER COLUMN created_date RENAME TO date;
ALTER TABLE project ALTER COLUMN created_date RENAME TO date;
ALTER TABLE n4user ALTER COLUMN created_date RENAME TO date;
