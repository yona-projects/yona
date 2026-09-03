# Setting Project

Legacy's `userManual` had two near-duplicate files for this — `projectSetting/project-setting-setting.md`
and `projectSettings/project-setting-setting.md` (a folder-name typo split them apart), the
second one shorter. Merged into one here, keeping the more detailed content.

You can change a project's settings if you have the project's administrator permissions.

Project logo, name, description, share option, and members can be configured.

* Go to a project you have authorization on.
* Click the `Project Settings` button at the top right of the project page.
* Click the `Setting` tab and change the project's logo, name, description, or share option.

## Logo

A project logo is displayed at the top of the project page, and everywhere projects are listed,
once set.

## Name

A project's name can be changed. Changing it affects:

* The `URL` related to the source code repository.
* Everywhere the name is shown.

## Description

You can change a project's description. The updated description is shown on the project's main
page, `Project List`, and `Group Page`.

## Share Option

You can change the share option (public, protected, private). The protected option is shown
only for group projects.

* `public` — all users can access or watch everything in the project.
* `private` — non-members of the project can't access it.
* `protected` — non-members of the project and of the project's group can't access it.

For more detail, see [technical/access-control.md](../../guide/technical/access-control.md)
(Korean only — legacy never had an English version).

## Reviewer

The value is the minimum number of reviewers needed to merge pull requests.

Each pull request needs at least this many reviewers before it can be accepted.

## Default Branch

This option is only shown when the project's repository type is Git.

The value is the branch the git repository's HEAD points to. It's also the default branch on
the `Code` menu, and the default `to` branch on the new pull request page.

## Menu Setting

Menu setting lets you choose which menus are shown.

Options: code, issue, pull request, review, milestone, board.

Deselecting an option doesn't delete its data — it just becomes invisible.
