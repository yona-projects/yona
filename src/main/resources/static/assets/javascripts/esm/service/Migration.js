/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/

/**
 * Yona의 프로젝트를 GitHub로 옮기는 이전(Migration) 화면이다. v1.6은 AngularJS 1.5.8 앱(컨트롤러, 서비스, 디렉티브, 설정을 browserify로 묶은 파일)이었고,
 * 같은 동작을 Vue 3 앱 한 개(옵션 API, 번들러 없이 전역 빌드, 화면 안에서 컴파일하는 템플릿)로 다시 썼다(D-120).
 *
 * 구성: 설정(`CONFIG`)과 토큰(`USER`, `WORKER`), `$http`를 대신하는 `http()`와 GitHub, Yona 서버를 부르는 서비스 함수,
 * 화면 상태와 동작을 담은 Vue 앱(`MigrationApp`), 기존 이슈나 마일스톤이 있다는 경고 컴포넌트(`import-warning`).
 * 서버가 내려 주는 값(토큰, 로그인 아이디, 이메일, OAuth state)은 `#migration-app`의 `data-*` 속성으로 받는다.
 */

// ES 모듈로 불러온다(<script type="module">). 모듈은 지연 실행되어 head의 클래식 스크립트(jQuery, vue.global.prod.js, yona.Common.js)가 먼저 준비된다.
const CONFIG = {
    "YONA_SERVER": "",
    "GITHUB_API_BASE_URL": "https://api.github.com",
    "IMPORT_API_ACCEPT_HEADER": "application/vnd.github.golden-comet-preview+json",
    "CLIENT_ID": "e7f9ad76a3a4ba19b2a5",
    "LOGGING_PROJECT_FULLNAME": "yona-projects/yona-migration-log"
};
const USER = {TOKEN: ""};
const WORKER = {TOKEN: ""};

////////////////////////////// $http 대체

/**
 * AngularJS `$http`와 같은 응답 모양을 돌려주는 `fetch` 래퍼이다. 2xx는 이행하고 그 밖의 상태와 연결 실패(status -1)는
 * 응답 객체로 거부한다. 응답 본문은 JSON처럼 보이거나 `application/json`이면 객체로 읽는다. GET에는 v1.6이 IE의 요청 캐시를 막으려고 싣던
 * `If-Modified-Since`를 그대로 싣고, 보내는 값이 있으면 JSON으로 보낸다.
 */
function http(config) {
    const method = (config.method || "GET").toUpperCase();
    const headers = {"Accept": "application/json, text/plain, */*"};
    const init = {"method": method};

    if (method === "GET") {
        headers["If-Modified-Since"] = "Mon, 26 Jul 1997 05:00:00 GMT";
    }
    if (config.data !== undefined) {
        init.body = typeof config.data === "string" ? config.data : JSON.stringify(config.data);
        headers["Content-Type"] = "application/json;charset=utf-8";
    }
    Object.assign(headers, config.headers);
    init.headers = headers;

    function build(response, text) {
        let data = text;
        const contentType = response.headers.get("Content-Type") || "";
        const trimmed = typeof text === "string" ? text.trim() : "";
        if (trimmed && (contentType.indexOf("application/json") === 0 || /^\[|^\{(?!\{)/.test(trimmed))) {
            try {
                data = JSON.parse(trimmed);
            } catch (e) {
                data = text;
            }
        }
        return {
            "data": data,
            "status": response.status,
            "headers": function (name) { return response.headers.get(name); },
            "config": config
        };
    }

    return fetch(config.url, init).then((response) => {
        return response.text().then((text) => {
            const result = build(response, text);
            if (response.status >= 200 && response.status < 300) {
                return result;
            }
            throw result;
        });
    }, () => {
        throw {"data": null, "status": -1, "headers": function () { return null; }, "config": config};
    });
}

////////////////////////////// 서비스

const milestoneMapper = {};
const labelOfPost = "게시글";
// 화면과 서비스가 같이 쓰는 가져오기 결과이다. 서비스가 `count`를 올릴 때 화면이 바뀌도록 반응형으로 만든다.
const importResult = Vue.reactive({"count": 0, "errorData": []});

function success(response) {
    console.log(response);
    return response;
}

function successWithData(response) {
    console.log(response);
    return response.data;
}

// 화면(Vue 앱)이 마운트되면 시스템 메시지 영역에 글을 더하는 함수로 바꾼다. 그 전의 실패는 콘솔에만 남는다.
let reportFailure = function () {};

/**
 * 요청 실패를 콘솔에 남기고, 서버가 상태 코드로 거부한 경우(403, 404 등)에는 화면의 시스템 메시지 영역(`#system-msg`)에도 상태와 주소를 보인다.
 * v1.6은 콘솔에만 기록해 화면에는 아무 안내가 없었다(migration-F8).
 *
 * @param {{status?: number, config?: {url?: string}}} response 거부된 응답 객체
 * @returns {*} 받은 응답을 그대로 돌려준다
 */
function error(response) {
    console.error(response);
    if (response && response.status > 0) {
        reportFailure(response.status + " " + ((response.config && response.config.url) || ""));
    }
    return response;
}

function errorWithSilence(response) {
    return response;
}

function isDestinationReady(destination) {
    return destination && destination.owner && destination.projectName;
}

function assertIsProjectSelected(name, project) {
    if (!project || !project.owner || !project.projectName) {
        console.error(project);
        throw name + " isn't ready!";
    }
}

const githubApi = CONFIG.GITHUB_API_BASE_URL;

function authorization(token) {
    return {"Authorization": "token " + token};
}

const migrationService = {
    logToIssue: function (source, destination, type, yobiUser, count) {
        const body = "source project total \n--- \n"
            + "\nmilestone: " + source.milestoneCount
            + "\nissue: " + source.issueCount
            + "\nposting: " + source.postCount
            + "\n\nsee: https://github.com/" + destination.full_name + "/issues"
            + "\n\n====\n\n"
            + " by [" + yobiUser + "](http://github.com/" + yobiUser + ")";
        const sourceFullName = source.owner + "/" + source.projectName;
        const labels = [sourceFullName + " --> " + destination.full_name, destination.owner];
        if (destination.withWikiCommit) {
            labels.push("attachments");
        }
        return http({
            "method": "POST",
            "url": githubApi + "/repos/" + CONFIG.LOGGING_PROJECT_FULLNAME + "/issues",
            "headers": authorization(WORKER.TOKEN),
            "data": {
                "title": sourceFullName + " --> " + destination.full_name + " " + type + "(" + count + ")",
                "body": body,
                "labels": labels
            }
        }).then((response) => { return response; }, (response) => { return response; });
    },

    delegateAttachmentsMigration: function () {
        console.log("Not Yet Support! :: delegateAttachmentsMigration");
    },

    getCurrentGithubUser: function () {
        return http({"method": "GET", "url": githubApi + "/user", "headers": authorization(USER.TOKEN)}).then(success, error);
    },

    havePostsAlready: function (destination) {
        if (!isDestinationReady(destination)) {
            return;
        }
        return http({
            "method": "GET",
            "url": githubApi + "/repos/" + destination.owner + "/" + destination.projectName + "/issues?state=all&labels=" + labelOfPost,
            "headers": authorization(WORKER.TOKEN)
        }).then((response) => { return response; }, (response) => { return response; });
    },

    haveIssuesAlready: function (destination) {
        if (!isDestinationReady(destination)) {
            return;
        }
        return http({
            "method": "GET",
            "url": githubApi + "/repos/" + destination.owner + "/" + destination.projectName + "/issues?state=all",
            "headers": authorization(WORKER.TOKEN)
        }).then((response) => { return response; }, (response) => { return response; });
    },

    haveMilestonesAlready: function (destination) {
        if (!isDestinationReady(destination)) {
            return;
        }
        return http({
            "method": "GET",
            "url": githubApi + "/repos/" + destination.owner + "/" + destination.projectName + "/milestones?state=all",
            "headers": authorization(WORKER.TOKEN)
        }).then((response) => { return response; }, (response) => { return response; });
    },

    getOrgMembership: function (destination, login) {
        return http({
            "method": "GET",
            "url": githubApi + "/orgs/" + destination.owner + "/memberships/" + login,
            "headers": authorization(WORKER.TOKEN)
        }).then(success, errorWithSilence);
    },

    userExistAtDestinationProject: function (owner, projectName, loginid) {
        return http({
            "method": "GET",
            "url": githubApi + "/repos/" + owner + "/" + projectName + "/collaborators/" + loginid,
            "headers": authorization(WORKER.TOKEN)
        }).then(success, errorWithSilence);
    },

    importProjectLabels: function (owner, projectName) {
        return http({"method": "GET", "url": CONFIG.YONA_SERVER + "/migration/" + owner + "/projects/" + projectName + "/labels"}).then(successWithData, error);
    },

    importIssueLabelMap: function (owner, projectName) {
        const issueLabelMap = {};
        return http({"method": "GET", "url": CONFIG.YONA_SERVER + "/migration/" + owner + "/projects/" + projectName + "/issuelabel"}).then((response) => {
            response.data.issueLabelPairs.forEach((obj) => {
                if (!issueLabelMap[obj.issueId]) {
                    issueLabelMap[obj.issueId] = [];
                }
                issueLabelMap[obj.issueId].push(obj.issueLabelId);
            });
            return issueLabelMap;
        }, error);
    },

    getSourceProject: function (owner, projectName) {
        return http({"method": "GET", "url": CONFIG.YONA_SERVER + "/migration/" + owner + "/projects/" + projectName}).then(successWithData, error);
    },

    getSourceProjects: function () {
        return http({"method": "GET", "url": CONFIG.YONA_SERVER + "/migration/projects"}).then(success, error);
    },

    /** 내 GitHub 저장소 목록을 100개씩 100쪽까지 모은다. 다음 쪽 여부는 응답 헤더 `Link`의 `rel="next",`로 판단한다. */
    getDestinationProjects: function () {
        let page = 1;
        const PER_PAGE = 100;
        let projects = [];
        const MAX_PAGE_FOR_GATHERING = 100;

        function hasNextPage(linkHeader) {
            return linkHeader && linkHeader.indexOf('; rel="next",') > -1;
        }

        function successThenPaging(response) {
            projects = projects.concat(response.data);
            if (hasNextPage(response.headers("Link"))) {
                page++;
                if (page <= MAX_PAGE_FOR_GATHERING) {
                    return gatheringLists();
                }
                throw "Too many pages!! Limit: Max " + MAX_PAGE_FOR_GATHERING + " page and " + PER_PAGE + " items per page";
            }
            return projects;
        }

        function failure(response) {
            console.error("Errors while loading destination projects", response);
            throw response.status + " : " + response.data;
        }

        function gatheringLists() {
            return http({
                "method": "GET",
                "url": githubApi + "/user/repos?per_page=" + PER_PAGE + "&page=" + page,
                "headers": authorization(USER.TOKEN)
            }).then(successThenPaging, failure);
        }

        return gatheringLists();
    },

    importMilestones: function (source, destination) {
        assertIsProjectSelected("Source", source);
        assertIsProjectSelected("Destination", destination);

        importResult.expected = source.milestoneCount;

        let sourceProjectUrl = "/migration/" + source.owner + "/projects/" + source.projectName + "/milestones";
        if (destination.withWikiCommit && destination.withWikiCommit === true) {
            sourceProjectUrl = sourceProjectUrl + "?withWikiCommit=" + destination.withWikiCommit;
        }

        function milestoneMapping(data, response) {
            if (!milestoneMapper[destination.projectName]) {
                milestoneMapper[destination.projectName] = {};
            }
            milestoneMapper[destination.projectName][data.milestone.id] = response.data.number;
        }

        return http({"method": "GET", "url": sourceProjectUrl}).then((response) => {
            response.data.milestones.forEach((data) => {
                http({
                    "method": "POST",
                    "url": githubApi + "/repos/" + destination.owner + "/" + destination.projectName + "/milestones",
                    "headers": authorization(WORKER.TOKEN),
                    "data": JSON.stringify(data.milestone)
                }).then((created) => {
                    if (created.status !== 201) {
                        importResult.errorData.push(created);
                    }
                    milestoneMapping(data, created);
                    importResult.count++;
                }, (failed) => {
                    const orgData = JSON.parse(failed.config.data);
                    importResult.errorData.push(orgData);
                    importResult.errorData.push(failed.data.errors[0]);
                    console.error("milestone import error", failed);
                });
            });
            return importResult;
        }, (response) => {
            console.error(response);
        });
    },

    importPosts: function (source, destination) {
        assertIsProjectSelected("Source", source);
        assertIsProjectSelected("Destination", destination);
        let sourceProjectUrl = "/migration/" + source.owner + "/projects/" + source.projectName + "/posts";
        if (destination.withWikiCommit && destination.withWikiCommit === true) {
            sourceProjectUrl = sourceProjectUrl + "?withWikiCommit=" + destination.withWikiCommit;
        }

        function preprocessPostData(data) {
            // 내용이 없는 글
            if ("body" in data.issue && !data.issue.body) {
                data.issue.body = "-- no contents --";
            }
            // 게시글 라벨은 고정이다
            data.issue.labels = [labelOfPost];
        }

        return http({"method": "GET", "url": sourceProjectUrl}).then((response) => {
            let counter = 0;  // 분당 요청 수 제한을 피하려고 0.5초씩 띄운다
            response.data.issues.forEach((data) => {
                preprocessPostData(data);
                setTimeout(() => {
                    http({
                        "method": "POST",
                        "url": githubApi + "/repos/" + destination.owner + "/" + destination.projectName + "/import/issues",
                        "headers": {"Authorization": "token " + WORKER.TOKEN, "Accept": CONFIG.IMPORT_API_ACCEPT_HEADER},
                        "data": JSON.stringify(data)
                    }).then((imported) => {
                        importResult.count++;
                        if (imported.status !== 202) {
                            importResult.errorData.push(imported);
                        }
                    }, (failed) => {
                        importResult.errorData.push(failed);
                        console.error("a", failed);
                    });
                }, counter * 500);
                counter++;
            });
            return importResult;
        }, error);
    },

    importIssues: function (source, destination, issueLabelMap) {
        assertIsProjectSelected("Source", source);
        assertIsProjectSelected("Destination", destination);

        const fullRepoName = destination.owner + "/" + destination.projectName;
        let sourceProjectUrl = "/migration/" + source.owner + "/projects/" + source.projectName + "/issues";
        if (destination.withWikiCommit && destination.withWikiCommit === true) {
            sourceProjectUrl = sourceProjectUrl + "?withWikiCommit=" + destination.withWikiCommit;
        }

        return http({"method": "GET", "url": sourceProjectUrl}).then((response) => {
            let counter = 0;  // 분당 요청 수 제한을 피하려고 0.5초씩 띄운다
            response.data.issues.forEach((data) => {
                const issueData = preprocessIssueData(data, destination, issueLabelMap);
                setTimeout(() => {
                    http({
                        "method": "POST",
                        "url": githubApi + "/repos/" + fullRepoName + "/import/issues",
                        "headers": {"Authorization": "token " + WORKER.TOKEN, "Accept": CONFIG.IMPORT_API_ACCEPT_HEADER},
                        "data": JSON.stringify(issueData)
                    }).then((imported) => {
                        importResult.count++;
                        if (imported.status !== 202) {
                            importResult.errorData.push(imported);
                        }
                    }, (failed) => {
                        importResult.errorData.push(failed);
                        console.error("a", failed);
                    });
                }, counter * 500);
                counter++;
            });
            return importResult;
        }, (response) => {
            console.error("b", response);
        });
    }
};

function preprocessIssueData(data, destination, issueLabelMap) {
    const projectName = destination.projectName;
    // 담당자 매핑
    if (destination.assignees && destination.assignees[data.issue.assignee]) {
        data.issue.assignee = destination.assignees[data.issue.assignee].login;
    } else {
        delete data.issue.assignee;
    }

    // 이슈 라벨 매핑
    data.issue.labels = issueLabelMap[data.issue.id];
    delete data.issue.id;

    // 내용이 없는 이슈
    if ("body" in data.issue && !data.issue.body) {
        const NO_CONTENTS = "-- no contents --";
        console.log(NO_CONTENTS);
        data.issue.body = NO_CONTENTS;
    }

    // 마일스톤 매핑
    if ("milestoneId" in data.issue) {
        if (milestoneMapper[projectName]) {
            data.issue.milestone = milestoneMapper[projectName][data.issue.milestoneId];
        } else {
            delete data.issue.milestone;
        }
        delete data.issue.milestoneId;
    }
    return data;
}

////////////////////////////// 화면

function noop() {}

function storageAvailable(type) {
    try {
        const storage = window[type];
        const x = "__storage_test__";
        storage.setItem(x, x);
        storage.removeItem(x);
        return true;
    } catch (e) {
        return false;
    }
}

/** 이슈나 마일스톤이 이미 있다는 경고. v1.6의 `import-warning` 디렉티브이다. */
const ImportWarning = {
    "props": ["data", "type"],
    "computed": {
        "exists": function () { return this.data && this.data.length > 0; },
        "warnExists": function () { return Messages("migration.js.warnExists"); },
        "warnAdds": function () { return Messages("migration.js.warnAdds"); }
    },
    "template": '<div class="alert-icon text-align-left" v-if="exists">'
        + '<div><i class="yobicon-alert"></i><span class="alert-icon-text">{{ warnExists }}</span></div>'
        + '<div class="text-align-left description">{{ warnAdds }}</div>'
        + '</div>'
};

const MigrationApp = {
    "components": {"import-warning": ImportWarning},

    "data": function () {
        return {
            "CONFIG": CONFIG,
            "token": "",
            "oauthState": "",
            "yobiUser": "yobi user loginId",
            "yobiUserEmail": "",
            "project": {},
            "sourceProjects": undefined,
            "destinationProjects": [],
            "importResult": {},
            "source": {},
            "destination": {"owner": "", "projectName": "", "full_name": "", "ownerType": "", "assignees": {}},
            "hardCodedDestination": {"owner": ""},  // owner가 있으면 시험용으로 이 프로젝트만 대상이 된다
            "projectLabels": {},
            "rawIssueLabelMap": {},
            "issueLabelMap": {},
            "systemMessages": [],
            "selectedSourceName": "",
            "selectedDestinationName": "",
            "showWorkerWarning": false,    // 대상에 작업 계정이 준비되지 않음
            "showUserProjectWarning": false,   // 대상이 조직 프로젝트가 아님
            "showNotAdminWarning": false,
            "showMoreDesc": false,
            "importBtnDisabled": false,
            "expectedImportCount": undefined,
            "currentGithubUser": undefined,
            "delegateAttachmentsMigrationCalled": {},
            "sourceFilter": "",
            "destinationFilter": ""
        };
    },

    "computed": {
        "sourceCount": function () { return this.sourceProjects ? this.sourceProjects.length : ""; },
        "destinationCount": function () { return this.destinationProjects ? this.destinationProjects.length : ""; },

        /** 검색어(대소문자 구분 없이 `full_name`에 들어 있는 글자)로 거른 원본 프로젝트. 소유자가 바뀌는 첫 항목에 `newOwner`를 표시한다. */
        "sourceItems": function () {
            return this.itemsOf(this.sourceProjects, this.sourceFilter, (repo) => { return repo.owner; });
        },
        "destinationItems": function () {
            return this.itemsOf(this.destinationProjects, this.destinationFilter, (repo) => { return repo.owner.login || repo.owner; });
        },

        "progressClass": function () {
            return this.importResult.count / this.expectedImportCount * 100 < 100 ? "bg-danger" : "bg-success";
        },
        "progressWidth": function () {
            return (this.importResult.count / this.expectedImportCount * 100 || 0) + "%";
        }
    },

    "created": function () {
        const root = document.getElementById("migration-app");
        this.oauthState = root.getAttribute("data-oauth-state") || "";
        this.token = root.getAttribute("data-token") || "";
        this.yobiUser = root.getAttribute("data-login-id");
        this.yobiUserEmail = root.getAttribute("data-email");
        this.testNotiMsg = Messages("migration.js.destinationOnly");
        this.activate();
        this.setUserToken(this.token);
    },

    "mounted": function () {
        // 토큰이 있을 때만 화면 본문이 있고, 그때 GitHub 저장소 목록과 내 계정을 읽는다
        if (this.token) {
            this.getDestinationProjects();
            this.getCurrentGithubUser();
        }
    },

    "watch": {
        /** 원본 프로젝트가 바뀌면 담당자 입력칸을 이전에 입력해 둔 값(localStorage)으로 채운다(v1.6의 행마다 `ng-init`). */
        "source": function (source) {
            const vm = this;
            (source.assignees || []).forEach((assignee) => { vm.getAssigneeFromLocal(assignee.login); });
        }
    },

    "methods": {
        "len": function (list) { return list ? list.length : 0; },

        "itemsOf": function (list, filter, ownerOf) {
            const term = String(filter || "").toLowerCase();
            let previous = null;
            return (list || []).filter((repo) => {
                return String(repo.full_name).toLowerCase().indexOf(term) > -1;
            }).map((repo) => {
                const owner = ownerOf(repo);
                const item = {"repo": repo, "newOwner": previous === null || previous !== owner};
                previous = owner;
                return item;
            });
        },

        "redirect": function () {
            window.location = "https://github.com/login/oauth/authorize?client_id=" + CONFIG.CLIENT_ID
                + "&scope=user,repo,admin:org"
                // 요청 위조를 막으려고 서버가 내려 준 state를 싣는다(R-58-2)
                + "&state=" + encodeURIComponent(this.oauthState);
        },

        "setUserToken": function (token) {
            USER.TOKEN = token;
            if (!token || token.trim().length < 2) {
                this.redirect();
            }
        },

        "activate": function () {
            const vm = this;
            this.loadingBar = {
                "start": (window.NProgress && NProgress.start) ? function () { NProgress.start(); } : noop,
                "end": function () {
                    if (window.NProgress && NProgress.done) {
                        NProgress.done();
                    }
                    vm.importBtnDisabled = false;
                    if (vm.destination.projectName) {
                        clearInterval(vm.interval);
                        vm.interval = setInterval(() => { vm.checkIfDestinationHaveDataAlready(); }, 2000);
                    }
                }
            };

            // 나가기 전에 확인한다(토큰이 있을 때만)
            window.onload = function () {
                window.addEventListener("beforeunload", (e) => {
                    if (!USER.TOKEN) {
                        return undefined;
                    }
                    const confirmationMessage = Messages("migration.js.leavePage");
                    (e || window.event).returnValue = confirmationMessage;
                    return confirmationMessage;
                });
            };

            reportFailure = (text) => this.systemMessage(text);
            this.getSourceProjects();
        },

        "systemMessage": function (text) {
            this.systemMessages.push(text);
            setTimeout(() => {
                const display = document.getElementById("system-msg");
                if (display) {
                    display.scrollTop = display.scrollHeight;
                }
            }, 500);
        },

        "getSourceProjects": function () {
            const vm = this;
            this.loadingBar.start();
            migrationService.getSourceProjects().then((results) => {
                vm.sourceProjects = results.data;
                vm.loadingBar.end();
            });
        },

        "getDestinationProjects": function () {
            const vm = this;
            this.systemMessage(Messages("migration.js.readingGithub"));
            this.loadingBar.start();
            migrationService.getDestinationProjects().then((response) => {
                vm.destinationProjects = response;
                vm.loadingBar.end();
                vm.systemMessage("OK!");
            });
        },

        "getCurrentGithubUser": function () {
            const vm = this;
            migrationService.getCurrentGithubUser().then((response) => {
                vm.currentGithubUser = response.data;
            });
        },

        "getSourceProject": function (owner, projectName) {
            const vm = this;
            this.loadingBar.start();
            migrationService.getSourceProject(owner, projectName).then((data) => {
                vm.loadingBar.end();
                vm.source = data;
            });
        },

        "selectSourceProject": function (fullName) {
            this.selectedSourceName = fullName;
        },

        "selectDestinationProject": function (fullName) {
            this.selectedDestinationName = fullName;
        },

        "onClickSource": function (repo) {
            this.getSourceProject(repo.owner, repo.projectName);
            this.selectSourceProject(repo.full_name);
        },

        "onClickDestination": function (repo) {
            this.setDestination(repo);
            this.selectDestinationProject(repo.full_name);
        },

        "isDestinationSelected": function () {
            return this.destination && this.destination.owner && this.destination.projectName && this.destination.ownerType;
        },

        "setDestination": function (repo) {
            const vm = this;
            this.destination = {
                "owner": repo.owner.login,
                "projectName": repo.name,
                "full_name": repo.owner.login + "/" + repo.name,
                "ownerType": repo.owner.type,
                "assignees": this.destination.assignees || {},
                "withWikiCommit": true
            };

            // 작업 토큰. v1.6은 조직 대상이면 별도 작업 계정의 토큰을 쓰려 했으나 그 토큰을 설정하는 곳이 없어 빈 토큰(`token `)으로 GitHub에
            // 요청을 보냈다. 관리자인 사용자가 조직 저장소로 옮겨도 인증이 없어 거절되었으므로(migration-F3) 늘 사용자 토큰을 쓴다.
            WORKER.TOKEN = USER.TOKEN;

            this.isMigrationWorkerAdmin(this.destination);
            this.checkIfDestinationHaveDataAlready();
            if (this.source.assignees) {
                this.source.assignees.forEach((sourceUser) => {
                    vm.userExistAtDestinationProject(vm.destination, sourceUser.login, 0);
                });
            }
        },

        "isMigrationWorkerAdmin": function (destination) {
            const vm = this;
            this.showWorkerWarning = false;
            this.showUserProjectWarning = false;
            this.showNotAdminWarning = false;
            if (destination.ownerType === "User") {
                this.showUserProjectWarning = true;
                return;
            }
            this.showUserProjectWarning = false;
            migrationService.getOrgMembership(destination, this.currentGithubUser.login).then((response) => {
                if (destination.ownerType === "Organization" && response.data.role !== "admin") {
                    WORKER.TOKEN = USER.TOKEN;
                    vm.showWorkerWarning = true;
                } else {
                    vm.showWorkerWarning = false;
                }
            });
            migrationService.getOrgMembership(destination, this.currentGithubUser.login).then((response) => {
                vm.showNotAdminWarning = destination.ownerType === "Organization" && response.data.role !== "admin";
            });
        },

        "checkIfDestinationHaveDataAlready": function () {
            const vm = this;
            const milestones = migrationService.haveMilestonesAlready(this.destination);
            const posts = migrationService.havePostsAlready(this.destination);
            const issues = migrationService.haveIssuesAlready(this.destination);
            if (milestones) { milestones.then((response) => { vm.destination.milestones = response.data; }); }
            if (posts) { posts.then((response) => { vm.destination.posts = response.data; }); }
            if (issues) { issues.then((response) => { vm.destination.issues = response.data; }); }
        },

        /** 담당자 입력칸의 값(`destination.assignees[원본 로그인].login`). 없으면 빈 글자. */
        "assigneeLogin": function (sourceUser) {
            const assignee = this.destination.assignees && this.destination.assignees[sourceUser];
            return assignee && assignee.login !== undefined ? assignee.login : "";
        },

        "setAssigneeLogin": function (sourceUser, value) {
            if (!this.destination.assignees) {
                this.destination.assignees = {};
            }
            if (!this.destination.assignees[sourceUser]) {
                this.destination.assignees[sourceUser] = {};
            }
            this.destination.assignees[sourceUser].login = value;
        },

        "assigneeConfirmed": function (sourceUser) {
            const assignee = this.destination.assignees && this.destination.assignees[sourceUser];
            return assignee ? assignee.confirmed : undefined;
        },

        "addAssigneeToLocal": function (sourceUser, destUser) {
            if (storageAvailable("localStorage")) {
                localStorage.setItem(sourceUser, destUser);
            }
        },

        "removeAssigneeFromLocal": function (sourceUser) {
            if (storageAvailable("localStorage")) {
                localStorage.removeItem(sourceUser);
            }
        },

        "getAssigneeFromLocal": function (sourceUser) {
            if (this.destination.assignees && this.destination.assignees[sourceUser] && this.destination.assignees[sourceUser].confirmed) {
                return this.destination.assignees[sourceUser];
            }

            if (storageAvailable("localStorage")) {
                const fromLocal = localStorage.getItem(sourceUser);
                if (fromLocal) {
                    console.log("loaded from localstorage:", fromLocal);
                    if (!this.destination.assignees[sourceUser]) {
                        this.destination.assignees[sourceUser] = {};
                    }
                    this.destination.assignees[sourceUser].login = fromLocal;
                    if (this.isDestinationSelected()) {
                        this.userExistAtDestinationProject(this.destination, sourceUser, 0);
                    }
                }
            }
        },

        /** 담당자로 적은 GitHub 계정이 대상 저장소의 협업자인지 확인한다. `delay`가 0이면 바로, 아니면 입력이 멈춘 뒤 300ms(이상) 지나서 확인한다. */
        "userExistAtDestinationProject": function (destination, sourceUser, delay) {
            const vm = this;
            // 담당자를 정하지 않았으면 확인하지 않는다
            if (!destination.assignees[sourceUser] || !destination.assignees[sourceUser].login) {
                return;
            }
            const destUser = destination.assignees[sourceUser].login;

            function checkUserExisted() {
                migrationService.userExistAtDestinationProject(destination.owner, destination.projectName, destUser).then((response) => {
                    if (response.status === 204) {
                        if (!vm.destination.assignees) {
                            vm.destination.assignees = {};
                        }
                        vm.destination.assignees[sourceUser].confirmed = true;
                        vm.destination.assignees[sourceUser].login = destUser;
                        vm.addAssigneeToLocal(sourceUser, destUser);
                    } else {
                        vm.destination.assignees[sourceUser].confirmed = false;
                        vm.removeAssigneeFromLocal(sourceUser);
                    }
                });
            }

            if (delay === 0) {
                checkUserExisted();
                return;
            }
            if (this.filterTextTimeout) {
                clearTimeout(this.filterTextTimeout);
            }
            const defaultDelayMillis = 300;
            this.filterTextTimeout = setTimeout(checkUserExisted, delay > defaultDelayMillis ? delay : defaultDelayMillis);
        },

        "delegateAttachmentsMigration": function (force) {
            if (!this.isDestinationSelected()) {
                return;
            }
            const calledCheckKey = this.source.full_name + "/" + this.destination.full_name;
            if (!this.delegateAttachmentsMigrationCalled[calledCheckKey]) {
                this.delegateAttachmentsMigrationCalled[calledCheckKey] = true;
                // v1.6은 값을 돌려주지 않는 서비스 함수에 `.then`을 걸어 TypeError가 났다(C101). 서비스는 아직 지원하지 않으므로 결과를 기다리지 않는다
                migrationService.delegateAttachmentsMigration(this.source, this.destination);
            }
            if (force === true) {
                migrationService.delegateAttachmentsMigration(this.source, this.destination);
            }
        },

        "checkHardcodedDestinationForTest": function () {
            if (this.hardCodedDestination.owner && this.destination.owner !== this.hardCodedDestination.owner) {
                this.importBtnDisabled = false;
                alert(this.testNotiMsg);
                throw "Error - wrong destination project selected";
            }
        },

        "lastConfirm": function (type, target) {
            return confirm(Messages("migration.js.confirmStart", target.owner, target.projectName, type));
        },

        "assertIsProjectSelected": function (name, project) {
            if (!project || !project.owner || !project.projectName) {
                console.error(project);
                this.systemMessage(Messages("migration.js.projectNotSet", name));
                this.importBtnDisabled = false;
                throw name + " isn't ready!";
            }
        },

        "setExpectedResultCount": function (expectedCount) {
            this.importResult.count = 0;
            this.importResult.errorData = [];
            this.expectedImportCount = expectedCount;
        },

        "endLoadingBar": function (counter) {
            const vm = this;
            setTimeout(() => {
                if (vm.importResult.count === vm.expectedImportCount) {
                    vm.loadingBar.end();
                } else if (counter < 20) {
                    // v1.6은 후위 증가라 counter가 늘지 않아 시간 초과에 닿지 못했다(C102)
                    vm.endLoadingBar(counter + 1);
                } else {
                    vm.loadingBar.end();
                    // AngularJS의 `$timeout`은 안에서 난 예외를 기록하고 넘어갔다. 시간 초과도 기록만 한다
                    console.error("Timeout!");
                }
            }, 500);
        },

        "importMilestones": function () {
            const vm = this;
            this.checkHardcodedDestinationForTest();
            if (!this.lastConfirm(Messages("migration.milestone"), this.destination)) { return; }

            this.importBtnDisabled = true;
            this.setExpectedResultCount(this.source.milestoneCount);
            this.assertIsProjectSelected("Source", this.source);
            this.assertIsProjectSelected("Destination", this.destination);
            this.systemMessage(Messages("migration.js.copyMilestones"));

            migrationService.importMilestones(this.source, this.destination).then((result) => {
                vm.importResult = result;
                vm.destination.milestones = vm.importResult;
                vm.endLoadingBar(1);
            });
            migrationService.logToIssue(this.source, this.destination, "마일스톤", this.yobiUser, this.source.milestoneCount);
        },

        "importIssues": function (source) {
            if (!this.source.hasOwnProperty("projectName")) {
                this.systemMessage(Messages("migration.js.selectSourceFirst"));
                return;
            }
            this.checkHardcodedDestinationForTest();
            if (!this.lastConfirm(Messages("migration.issue"), this.destination)) { return; }
            this.importBtnDisabled = true;
            this.importProjectLabels(source);
        },

        "importProjectLabels": function (source) {
            const vm = this;
            this.loadingBar.start();
            migrationService.importProjectLabels(source.owner, source.projectName).then((data) => {
                vm.projectLabels = data.labels;
                vm.systemMessage(Messages("migration.js.labelCount", Object.keys(vm.projectLabels).length));
                vm.systemMessage(Messages("migration.js.arrangingLabels"));
                vm.importIssueLabelMap(source);
            });
        },

        "importIssueLabelMap": function (source) {
            const vm = this;
            migrationService.importIssueLabelMap(source.owner, source.projectName).then((data) => {
                vm.rawIssueLabelMap = data;
                vm.systemMessage(Messages("migration.js.labeledIssues", Object.keys(vm.rawIssueLabelMap).length));
                vm.systemMessage(Messages("migration.js.working"));
                vm.flatLabelMap();
                vm.executeIssueImports();
            });
        },

        /** 이슈 번호별 라벨 번호 목록을 "라벨 이름 - 분류 이름" 목록으로 바꾼다. */
        "flatLabelMap": function () {
            const vm = this;
            this.issueLabelMap = {};
            Object.keys(this.rawIssueLabelMap).forEach((key) => {
                vm.rawIssueLabelMap[key].forEach((id) => {
                    if (!vm.issueLabelMap[key]) {
                        vm.issueLabelMap[key] = [];
                    }
                    vm.issueLabelMap[key].push(vm.projectLabels[id].name + " - " + vm.projectLabels[id].categoryName);
                });
            });
            return this.issueLabelMap;
        },

        "executeIssueImports": function () {
            const vm = this;
            const source = this.source;
            const destination = this.destination;
            const issueLabelMap = this.issueLabelMap;

            this.setExpectedResultCount(source.issueCount);
            this.assertIsProjectSelected("Source", source);
            this.assertIsProjectSelected("Destination", destination);

            this.systemMessage(Messages("migration.js.readingIssues"));
            migrationService.importIssues(source, destination, issueLabelMap).then((result) => {
                vm.importResult = result;
                vm.destination.issues = result;
                vm.endLoadingBar(1);
                vm.systemMessage(Messages("migration.js.sendingIssues"));
                vm.systemMessage(Messages("migration.js.delayNotice"));
                vm.systemMessage(Messages("migration.js.closeBrowserOk"));
            });
            migrationService.logToIssue(this.source, this.destination, "이슈", this.yobiUser, this.source.issueCount);
        },

        "importPosts": function () {
            const vm = this;
            this.checkHardcodedDestinationForTest();
            if (!this.lastConfirm(Messages("migration.post"), this.destination)) { return; }

            this.importBtnDisabled = true;
            const source = this.source;
            const destination = this.destination;

            this.setExpectedResultCount(source.postCount);
            this.assertIsProjectSelected("Source", source);
            this.assertIsProjectSelected("Destination", destination);

            this.loadingBar.start();
            this.systemMessage(Messages("migration.js.readingPosts"));
            migrationService.importPosts(source, destination).then((result) => {
                vm.importResult = result;
                vm.destination.issues = result;
                vm.endLoadingBar(1);
                vm.systemMessage(Messages("migration.js.sendingPosts"));
                vm.systemMessage(Messages("migration.js.delayNotice"));
                vm.systemMessage(Messages("migration.js.closeBrowserOk"));
                vm.delegateAttachmentsMigration();
            });
            migrationService.logToIssue(this.source, this.destination, "게시글", this.yobiUser, this.source.postCount);
        },

        /** 게시글 이전 단추: 안내 문구를 토글한 뒤 이전을 시작한다. */
        "onImportPosts": function () {
            this.showMoreDesc = !this.showMoreDesc;
            this.importPosts();
        }
    }
};

$(() => {
    Vue.createApp(MigrationApp).mount("#migration-app");
});
