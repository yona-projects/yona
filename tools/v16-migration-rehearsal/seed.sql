insert into n4user (id,name,login_id,password,password_salt,email,remember_me,state,created_date,lang,token,is_guest) values
(1,'Admin','admin','pC8gT79Jw0YYokES0SkgAYq9zaLFxPeG+qAQLnzQtB8=','saltadmin1','admin@example.com',0,'ACTIVE',now(),'ko-KR',NULL,0),
(2,'Alice','alice','B/Eg1YGWdXW5ZoINoSqMvVjGJTJHzRhMw/tE9pOCRCo=','saltalice2','alice@example.com',0,'ACTIVE',now(),'ko-KR',NULL,0),
(3,'Bob','bob','zX8kQ6iXfSMz3GalDBV/tiSUSwMEiv+JXcWBZDV9prk=','saltbob3','bob@example.com',0,'ACTIVE',now(),'ko-KR','legacy-token-bob-0001',0);

insert into site_admin (id,admin_id) values (1,1);
insert into project (id,name,overview,vcs,owner,created_date,last_issue_number,last_posting_number,default_reviewer_count,is_using_reviewer_count,project_scope) values (1,'demo','rehearsal project','GIT','alice',now(),0,0,2,1,'PUBLIC');
insert into project_menu_setting (id,project_id,code,issue,pull_request,review,milestone,board) values (1,1,1,1,1,1,1,1);
insert into pull_request (id,title,body,to_project_id,from_project_id,to_branch,from_branch,contributor_id,receiver_id,created,updated,state,is_conflict,is_merging,number) values
(1,'no number open','b',1,1,'refs/heads/main','refs/heads/f1',3,2,now(),now(),1,0,0,NULL),
(2,'rejected one','b',1,1,'refs/heads/main','refs/heads/f2',3,2,now(),now(),3,0,0,2),
(3,'open with reviewer','b',1,1,'refs/heads/main','refs/heads/f3',3,2,now(),now(),1,0,0,3),
(4,'merged one','b',1,1,'refs/heads/main','refs/heads/f4',3,2,now(),now(),6,0,0,4);
insert into pull_request_reviewers (pull_request_id,user_id) values (3,3),(4,3);
