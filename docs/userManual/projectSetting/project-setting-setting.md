# Setting Project

You can change a project settings if you have the project's administrator permissions.

Project's logo, name, description, share option and member can be configured.

* Go to the project of which you have authorization.
* Click 'Project Setting Button' on top right of a project.
* Click `Setting` tab and change project's logo, name, description and share option.


## Logo

A Project logo is displayed on top of a project page and everywhere projects are listed when setting is done.


## Name

A project name can be changed. If the name is changed, it affects below.

* `URL` related to source code repository will be changed.
* Everywhere the name is shown.


## Description

You can alter a project description. The explanation changed will be shown on project main page, `Project List` and `Group Page`.


## Share Option

You can change share option (public, protected, private). Protected option is shown when a project is group project.

To get detail information see `docs/technical/access-control.md`


## Reviewer

The value means number of minimum reviewers to merge pull requests.

Each pull request should be reviewed by users greater than or equal to the value to be accepted.


## Default Branch

This option is only shown when project repository type is GIT.

The value means a branch HEAD of git repository points to. Also, the value is default branch on `Code` menu and default `to branch` on new pull request page.


## Menu Setting

Using menu setting, you can select menus you want to show.

There are code, issue, pull request, review, milestone and board options you can select.

Although you do not select some options, data about it are not deleted. These are just invisible.
