(function e(t,n,r){function s(o,u){if(!n[o]){if(!t[o]){var a=typeof require=="function"&&require;if(!u&&a)return a(o,!0);if(i)return i(o,!0);var f=new Error("Cannot find module '"+o+"'");throw f.code="MODULE_NOT_FOUND",f}var l=n[o]={exports:{}};t[o][0].call(l.exports,function(e){var n=t[o][1][e];return s(n?n:e)},l,l.exports,e,t,n,r)}return n[o].exports}var i=typeof require=="function"&&require;for(var o=0;o<r.length;o++)s(r[o]);return s})({1:[function(require,module,exports){
'use strict';

var angular = require('angular');


angular.module('yona.migration', []);

({"migration-config":require("./migration-config.js"),"migration-controller":require("./migration-controller.js"),"migration-directive":require("./migration-directive.js"),"migration-service":require("./migration-service.js")});

},{"./migration-config.js":2,"./migration-controller.js":3,"./migration-directive.js":4,"./migration-service.js":5,"angular":"angular"}],2:[function(require,module,exports){
'use strict';

var angular = require('angular');

angular.module("yona.migration")
    .constant("CONFIG", {
        "YONA_SERVER": "",
        "GITHUB_API_BASE_URL": "https://api.github.com",
        "IMPORT_API_ACCEPT_HEADER": "application/vnd.github.golden-comet-preview+json",
        "CLIENT_ID": "e7f9ad76a3a4ba19b2a5",
        "LOGGING_PROJECT_FULLNAME": "yona-projects/yona-migration-log"
    })
    .value("USER", {
        TOKEN: ""
    })
    .value("WORKER", {
        TOKEN: ""
    })
    .config(['$httpProvider', function($httpProvider) {
        $httpProvider.defaults.useXDomain = true;
        delete $httpProvider.defaults.headers.common['X-Requested-With'];

        //initialize get if not there
        if (!$httpProvider.defaults.headers.get) {
            $httpProvider.defaults.headers.get = {};
        }

        //disable IE ajax request caching
        $httpProvider.defaults.headers.get['If-Modified-Since'] = 'Mon, 26 Jul 1997 05:00:00 GMT';
    }]);

},{"angular":"angular"}],3:[function(require,module,exports){
'use strict';

MigrationController.$inject = ["$log", "$timeout", "$scope", "migrationService", "USER", "WORKER", "CONFIG"];
var angular = require('angular');
var NProgress = require('NProgress');

angular.module("yona.migration")
    .controller("MigrationController", MigrationController);

function MigrationController($log, $timeout, $scope, migrationService, USER, WORKER, CONFIG) {
    /* jshint validthis: true */
    var vm = this;
    vm.yobiUser = "yobi user loginId";  // set by ng-init at the view page
    vm.project = {};
    vm.destinationProjects = [];
    vm.importResult = {};
    vm.source = {};             // source project
    vm.destination = {          // destination project
        owner: "",
        projectName: "",
        full_name: "",
        ownerType: "",
        assignees: {}
    };
    vm.hardCodedDestination = {
        owner: ''  // if owner set, it will be forced destination project for test
    };
    vm.projectLabels = {};
    vm.rawIssueLabelMap = {};
    vm.issueLabelMap = {};
    vm.systemMessages = [];
    vm.selectedSourceName = "";
    vm.selectedDestinationName = "";
    vm.CONFIG = CONFIG;
    vm.showWorkerWarning = false;   // not ready for worker at destination
    vm.showUserProjectWarning = false;   // show destination project isn't organization project
    vm.delegateAttachmentsMigrationCalled = {};

    var prevOwnerName = "";
    var loadingBar = {};
    var filterTextTimeout;

    vm.redirect = function () {
        window.location = "https://github.com/login/oauth/authorize?client_id=" + CONFIG.CLIENT_ID
            + "&scope=user,repo,admin:org";
    };
    vm.getSourceProject = getProject;
    vm.getSourceProjects = getSourceProjects;
    vm.getDestinationProjects = getDestinationProjects;
    vm.importIssues = importIssues;
    vm.importMilestones = importMilestones;
    vm.setDestination = setDestination;
    vm.isNewOwner = isNewOwner;
    vm.importPosts = importPosts;
    vm.userExistAtDestinationProject = userExistAtDestinationProject;
    vm.selectSourceProject = selectSourceProject;
    vm.selectDestinationProject = selectDestinationProject;
    vm.setUserToken = setUserToken;
    vm.getCurrentGithubUser = getCurrentGithubUser;
    vm.addAssigneeToLocal = addAssigneeToLocal;
    vm.getAssigneeFromLocal = getAssigneeFromLocal;
    vm.delegateAttachmentsMigration = delegateAttachmentsMigration;

    // for testing
    vm._test_ = {};
    vm._test_.flatLabelMap = flatLabelMap;

    var testNotiMsg = "'테스트 기간 중에는 Destination을 test-org 그룹 하위의 프로젝트로만 지정 가능합니다!'";
    activate();

    ///////////////////////////

    function delegateAttachmentsMigration(force){
        if(!isDestinationSelected()){
            return;
        }
        var calledCheckKey = vm.source.full_name + "/" + vm.destination.full_name;
        if(!vm.delegateAttachmentsMigrationCalled[calledCheckKey]){
            vm.delegateAttachmentsMigrationCalled[calledCheckKey] = true;
            migrationService.delegateAttachmentsMigration(vm.source, vm.destination).then(function(response){
                $log.log(response);
            });
        }
        if(force === true){
            migrationService.delegateAttachmentsMigration(vm.source, vm.destination).then(function(response){
                $log.log(response);
            });
        }
    }

    function addAssigneeToLocal(sourceUser, destUser){
        if(storageAvailable('localStorage')){
            localStorage.setItem(sourceUser, destUser);
        }
    }

    function removeAssigneeFromLocal(sourceUser){
        if(storageAvailable('localStorage')){
            localStorage.removeItem(sourceUser);
        }
    }

    function getAssigneeFromLocal(sourceUser){
        var sourceUserFromLocalStorage = "";
        if(vm.destination.assignees && vm.destination.assignees[sourceUser]
                && vm.destination.assignees[sourceUser].confirmed){
            return vm.destination.assignees[sourceUser];
        }

        if(storageAvailable('localStorage')){
            sourceUserFromLocalStorage = localStorage.getItem(sourceUser);
            if(sourceUserFromLocalStorage){
                $log.log("loaded from localstorage:", sourceUserFromLocalStorage);
                if(!vm.destination.assignees[sourceUser]){
                    vm.destination.assignees[sourceUser] = {};
                }
                vm.destination.assignees[sourceUser].login = sourceUserFromLocalStorage;
                if(isDestinationSelected()){
                    userExistAtDestinationProject(vm.destination, sourceUser, 0);
                }
            }
        }
    }

    function storageAvailable(type) {
        try {
            var storage = window[type],
                x = '__storage_test__';
            storage.setItem(x, x);
            storage.removeItem(x);
            return true;
        }
        catch(e) {
            return false;
        }
    }

    function getCurrentGithubUser(){
        migrationService.getCurrentGithubUser().then(function(response){
            vm.currentGithubUser = response.data;
        });
    }

    function haveMilestonesAlready(){
        migrationService.haveMilestonesAlready(vm.destination).then(function(response){
            vm.destination.milestones = response.data;
        });
    }

    function haveIssuesAlready(){
        migrationService.haveIssuesAlready(vm.destination).then(function(response){
            vm.destination.issues = response.data;
        });
    }

    function havePostsAlready(){
        migrationService.havePostsAlready(vm.destination).then(function(response){
            vm.destination.posts = response.data;
        });
    }

    function setUserToken(token){
        USER.TOKEN = token;
        if(!token || token.trim().length < 2){
            vm.redirect();
        }
    }

    function systemMessage(text){
        vm.systemMessages.push(text);
        setTimeout(function(){
            var systemMsgDisplay = $("#system-msg");
            systemMsgDisplay.scrollTop(systemMsgDisplay[0].scrollHeight);
        }, 500);
    }

    function isMigrationWorkerAdmin(destination){
        vm.showWorkerWarning = false;   // not ready for worker at destination
        vm.showUserProjectWarning = false;   // show destination project isn't organization project
        vm.showNotAdminWarning = false;
        if(destination.ownerType === "User"){
            vm.showUserProjectWarning = true;
            return;
        } else {
            vm.showUserProjectWarning = false;
        }
        migrationService.getOrgMembership(destination, vm.currentGithubUser.login).then(
            function(response) {
                if(destination.ownerType === 'Organization'
                    && response.data.role !== 'admin'){
                    WORKER.TOKEN = USER.TOKEN;
                    vm.showWorkerWarning = true;
                } else {
                    vm.showWorkerWarning = false;
                }
            });
        migrationService.getOrgMembership(destination, vm.currentGithubUser.login).then(
            function(response) {
                if(destination.ownerType === 'Organization'
                    && response.data.role !== 'admin'){
                    vm.showNotAdminWarning = true;
                } else {
                    vm.showNotAdminWarning = false;
                }
            });
    }

    function userExistAtDestinationProject(destination, sourceUser, delay){
        // never set destination assignee user
        if(!destination.assignees[sourceUser] || !destination.assignees[sourceUser].login) {
            return;
        }

        var destUser = destination.assignees[sourceUser].login;

        if(delay === 0){ // check immediately
            checkUserExisted();
            return;
        } else { // check with delay
            if (filterTextTimeout) {
                $timeout.cancel(filterTextTimeout);
            }
            var defaultDelayMillis = 300;
            filterTextTimeout = $timeout(checkUserExisted, delay > defaultDelayMillis? delay : defaultDelayMillis);
        }

        ///////////////////
        function checkUserExisted(){
            migrationService.userExistAtDestinationProject(destination.owner, destination.projectName, destUser).then(
                function(response) {
                    if(response.status === 204){
                        if(!vm.destination.assignees){
                            vm.destination.assignees = {};
                        }
                        vm.destination.assignees[sourceUser].confirmed = true;
                        vm.destination.assignees[sourceUser].login = destUser;
                        //WARN: it is not good add logic here, but commit a evil for convenience
                        addAssigneeToLocal(sourceUser, destUser)
                    } else {
                        vm.destination.assignees[sourceUser].confirmed = false;
                        //WARN: it is not good add logic here, but commit a evil for convenience
                        removeAssigneeFromLocal(sourceUser);
                    }
                }
            )
        }
    }

    function getSourceProjects(){
        loadingBar.start();
        migrationService.getSourceProjects().then(function(results){
            vm.sourceProjects = results.data;
            loadingBar.end();
        });
    }

    function checkHardcodedDestinationForTest(){
        if(vm.hardCodedDestination.owner &&
                vm.destination.owner !== vm.hardCodedDestination.owner){
            vm.importBtnDisabled = false;
            alert(testNotiMsg);
            throw "Error - wrong destination project selected"
        }
    }

    function importPosts() {
        checkHardcodedDestinationForTest();
        if(!lastConfirm("게시글", vm.destination)) return;

        vm.importBtnDisabled = true;
        var source = vm.source;
        var destination = vm.destination;

        setExpectedResultCount(source.postCount);

        assertIsProjectSelected('Source', source);
        assertIsProjectSelected('Destination', destination);

        loadingBar.start();
        systemMessage("서버로부터 게시글을 읽어들입니다...");
        migrationService.importPosts(source, destination).then(function (result) {
            vm.importResult = result;
            vm.destination.issues = result;
            endLoadingBar(1);
            systemMessage("읽어 들인 게시글을 Github으로 보내는중입니다.");
            systemMessage("(실제 최종 데이터가 보이려면 작업 이후에도 시간이 좀 더 걸릴 수 있습니다)");
            systemMessage("진행바가 끝까지 진행된 이후에는 브라우저를 닫으셔도 이슈 이전작업에 영향을 주지 않습니다");
            delegateAttachmentsMigration();
        });
        migrationService.logToIssue(vm.source, vm.destination, "게시글", vm.yobiUser, vm.source.postCount);
    }

    function importIssues(source){
        if(!vm.source.hasOwnProperty('projectName')){
            systemMessage("source 프로젝트를 선택하세요!");
            return;
        }

        checkHardcodedDestinationForTest();

        if(!lastConfirm('이슈', vm.destination)) return;
        vm.importBtnDisabled = true;
        importProjectLabels(source);
    }

    function isNewOwner(repo){
        var ownerName = repo.owner.login || repo.owner;
        if(!prevOwnerName || prevOwnerName != ownerName) {
            prevOwnerName = ownerName;
            return true;
        } else {
            return false;
        }
    }

    function flatLabelMap() {
        vm.issueLabelMap = {};
        for (var key in vm.rawIssueLabelMap) {
            if (vm.rawIssueLabelMap.hasOwnProperty(key)) {
                vm.rawIssueLabelMap[key].map(function (id) {
                    if (!vm.issueLabelMap[key]) {
                        vm.issueLabelMap[key] = [];
                    }
                    vm.issueLabelMap[key].push(
                        vm.projectLabels[id].name + " - " + vm.projectLabels[id].categoryName);
                });
            }
        }
        return vm.issueLabelMap;
    }

    function importIssueLabelMap(source) {
        migrationService.importIssueLabelMap(source.owner, source.projectName).then(function (data) {
            vm.rawIssueLabelMap = data;
            systemMessage(Object.keys(vm.rawIssueLabelMap).length + "개의 이슈에 라벨이 붙어 있습니다.");
            systemMessage("작업중입니다. 잠시만 기다려 주세요..");
            flatLabelMap(vm.rawIssueLabelMap);
            executeIssueImports();
        });
    }

    function importProjectLabels(source) {
        loadingBar.start();
        migrationService.importProjectLabels(source.owner, source.projectName)
            .then(function (data) {
                vm.projectLabels = data.labels;
                systemMessage(Object.keys(vm.projectLabels).length + "개의 라벨이 존재합니다.");
                systemMessage("프로젝트의 issue별 라벨을 정리합니다...");
                importIssueLabelMap(source);
            });
    }

    function selectSourceProject(fullName){
        vm.selectedSourceName = fullName;
    }
    function selectDestinationProject(fullName){
        vm.selectedDestinationName = fullName;
    }

    function checkIfDestinationHaveDataAlready(){
        haveMilestonesAlready(vm.destination);
        havePostsAlready(vm.destination);
        haveIssuesAlready(vm.destination);
    }

    function isDestinationSelected(){
        return vm.destination && vm.destination.owner && vm.destination.projectName
        && vm.destination.ownerType;
    }

    function setDestination(repo) {
        vm.destination = {
            owner: repo.owner.login,
            projectName: repo.name,
            full_name: repo.owner.login + "/" +  repo.name,
            ownerType: repo.owner.type,
            assignees: vm.destination.assignees || {},
            withWikiCommit: true
        };

        // set worker token
        if(vm.destination.ownerType === "User"){
            WORKER.TOKEN = USER.TOKEN;
        } else {
            WORKER.TOKEN = "";
        }

        isMigrationWorkerAdmin(vm.destination);
        checkIfDestinationHaveDataAlready();
        checkIfDestinationAssigneesExist();

        function checkIfDestinationAssigneesExist(){
            if(vm.source.assignees){
                vm.source.assignees.forEach(function(sourceUser){
                    userExistAtDestinationProject(vm.destination, sourceUser.login, 0);
                });
            }
        }
    }


    function getDestinationProjects() {
        systemMessage("Github 프로젝트 목록을 읽어들입니다...");
        loadingBar.start();
        migrationService.getDestinationProjects().then(function (response) {
            vm.destinationProjects = response;
            loadingBar.end();
            systemMessage("OK!");
        });
    }

    function setExpectedResultCount(expectedCount){
        vm.importResult.count = 0;
        vm.importResult.errorData = [];
        vm.expectedImportCount = expectedCount;
    }

    function executeIssueImports() {
        var source = vm.source;
        var destination = vm.destination;
        var issueLabelMap = vm.issueLabelMap;

        setExpectedResultCount(source.issueCount);

        assertIsProjectSelected('Source', source);
        assertIsProjectSelected('Destination', destination);

        systemMessage("서버로부터 이슈를 읽어들입니다...");
        migrationService.importIssues(source, destination, issueLabelMap).then(function (result) {
            vm.importResult = result;
            vm.destination.issues = result;
            endLoadingBar(1);
            systemMessage("읽어 들인 이슈를 Github으로 보내는중입니다.");
            systemMessage("(실제 최종 데이터가 보이려면 작업 이후에도 시간이 좀 더 걸릴 수 있습니다)");
            systemMessage("진행바가 끝까지 진행된 이후에는 브라우저를 닫으셔도 이슈 이전작업에 영향을 주지 않습니다");
        });
        migrationService.logToIssue(vm.source, vm.destination, "이슈", vm.yobiUser, vm.source.issueCount);
    }

    function endLoadingBar(counter){
        $timeout(function() {
            if(vm.importResult.count === vm.expectedImportCount) {
                loadingBar.end();
            } else {
                if(counter < 20){
                    endLoadingBar(counter++);
                } else {
                    loadingBar.end();
                    throw "Timeout!";
                }
            }
        }, 500);
    }
    function lastConfirm(type, target){
        return confirm("\n" +target.owner + "/" + target.projectName + " 프로젝트로 \n\n" + type + " 마이그레이션을 시작하시겠습니까?\n\n마이그레이션이 종료되기 전에 브라우저 창을 닫지 말아주세요.");
    }

    function assertIsProjectSelected(name, project) {
        if (!project || !project.owner || !project.projectName) {
            $log.error(project);
            systemMessage(name + " 프로젝트가 지정되지 않았습니다");
            vm.importBtnDisabled = false;
            throw name + " isn't ready!"
        }
    }

    function importMilestones() {
        checkHardcodedDestinationForTest();

        if(!lastConfirm('마일스톤', vm.destination)) return;

        vm.importBtnDisabled = true;

        setExpectedResultCount(vm.source.milestoneCount);

        assertIsProjectSelected('Source', vm.source);
        assertIsProjectSelected('Destination', vm.destination);

        systemMessage("마일스톤을 복사합니다..");

        migrationService.importMilestones(vm.source, vm.destination).then(function (result) {
            vm.importResult = result;
            vm.destination.milestones = vm.importResult;
            endLoadingBar(1);
        });
        migrationService.logToIssue(vm.source, vm.destination, "마일스톤", vm.yobiUser, vm.source.milestoneCount);
    }

    function getProject(owner, projectName) {
        loadingBar.start();
        migrationService.getSourceProject(owner, projectName).then(function (data) {
            loadingBar.end();
            vm.source = data;
            return vm.source;
        });
    }

    function noop(){}

    function leavingPageAlert(){
        window.onload = function() {
            window.addEventListener("beforeunload", function (e) {
                if (!USER.TOKEN) {
                    return undefined;
                }

                var confirmationMessage = '페이지를 떠나시겠습니까? 마이그레이션이 진행중인 상태가 아닌지 확인해 주세요!';

                (e || window.event).returnValue = confirmationMessage; //Gecko + IE
                return confirmationMessage; //Gecko + Webkit, Safari, Chrome etc.
            });
        };
    }

    function activate() {
        if(NProgress){
            loadingBar =  {
                start: NProgress.start || noop,
                end: function(){
                    NProgress.done? NProgress.done(): noop();
                    vm.importBtnDisabled = false;
                    if(vm.destination.projectName){
                        clearInterval(vm.interval);
                        vm.interval = setInterval(checkIfDestinationHaveDataAlready, 2000);
                    }
                }
            };
        }

        // for test
        if(navigator.userAgent.indexOf("PhantomJS") === -1) {
            leavingPageAlert();
        }

        getSourceProjects();
        // ToDo: health check
    }
}

module.exports = MigrationController;

},{"NProgress":6,"angular":"angular"}],4:[function(require,module,exports){
'use strict';

var angular = require('angular');

angular.module("yona.migration")
    .directive("importWarning", importWarning);

function importWarning() {
    var warningTemplate = '<div class="alert-icon text-align-left" ng-if="data.length > 0">' +
        '<div><i class="yobicon-alert"></i><span class="alert-icon-text" ng-if="data.length > 0"> 대상 프로젝트에 <strong>{{type}}</strong> 데이터가 존재합니다.</span></div>' +
        '<div class="text-align-left description" ng-if="data.length >0">import 진행시 데이터가 추가됩니다. 기존 데이터를 건드리지는 않으나 중복데이터가 입력될 가능성이 있으니 미리 확인해 주세요</div>' +
        '</div>';
    return ({
        template: warningTemplate,
        restrict: 'E',
        scope: {
            data: "=",
            type: "@"
        }
    });
}

module.exports = importWarning;

},{"angular":"angular"}],5:[function(require,module,exports){
'use strict';

migrationService.$inject = ["$http", "$log", "CONFIG", "USER", "WORKER"];
var angular = require('angular');

angular.module("yona.migration")
    .service("migrationService", migrationService);

function migrationService($http, $log, CONFIG, USER, WORKER) {
    var milestoneMapper = {};
    var labelOfPost = "게시글";
    var importResult = {
        count: 0,
        errorData: []
    };
    var migrationServiceApi = {
        getSourceProject: getSourceProject,
        getIssues: getIssues,
        getDestinationProjects: getDestinationProjects,
        importMilestones: importMilestones,
        importIssues: importIssues,
        importProjectLabels: importProjectLabels,
        importIssueLabelMap: importIssueLabelMap,
        importPosts: importPosts,
        userExistAtDestinationProject: userExistAtDestinationProject,
        getSourceProjects: getSourceProjects,
        haveMilestonesAlready: haveMilestonesAlready,
        haveIssuesAlready: haveIssuesAlready,
        havePostsAlready: havePostsAlready,
        getOrgMembership: getOrgMembership,
        getCurrentGithubUser: getCurrentGithubUser,
        delegateAttachmentsMigration: delegateAttachmentsMigration,
        logToIssue: logToIssue
    };

    // for testing
    migrationServiceApi._test_ = {};
    migrationServiceApi._test_.preprocessIssueData = preprocessIssueData;

    return  migrationServiceApi;

    //////////////////////////////

    function logToIssue(source, destination, type, yobiUser, count){
        var body = "source project total \n--- \n"
            + "\nmilestone: " + source.milestoneCount
            + "\nissue: " + source.issueCount
            + "\nposting: " + source.postCount
            + "\n\nsee: https://github.com/" + destination.full_name + "/issues"
            + "\n\n====\n\n"
            + " by [" + yobiUser + "](http://github.com/" + yobiUser + ")";
        var sourceFullName = source.owner +"/" + source.projectName;
        var labels = [sourceFullName + " --> " + destination.full_name, destination.owner];
        if(destination.withWikiCommit){
            labels.push("attachments");
        }
        return $http({
            method: 'POST',
            url: CONFIG.GITHUB_API_BASE_URL + "/repos/" + CONFIG.LOGGING_PROJECT_FULLNAME + "/issues",
            headers: {
                "Authorization": "token " + WORKER.TOKEN
            },
            data: {
                "title": sourceFullName + " --> " + destination.full_name + " " + type + "(" + count + ")",
                "body": body,
                "labels": labels
            }
        }).then(function success(response){
            return response;
        }, function error(response){
            return response;
        });
    }

    function delegateAttachmentsMigration(source, destination){
        console.log("Not Yet Support! :: delegateAttachmentsMigration");
    }

    function getCurrentGithubUser(){
        return $http({
            method: 'GET',
            url: CONFIG.GITHUB_API_BASE_URL + "/user",
            headers: {
                "Authorization": "token " + USER.TOKEN
            }
        }).then(success, error);
    }

    function isDestinationReady(destination) {
        return destination && destination.owner && destination.projectName;
    }

    function havePostsAlready(destination){
        if(!isDestinationReady(destination)){
            return;
        }

        return $http({
            method: 'GET',
            url: CONFIG.GITHUB_API_BASE_URL + "/repos/" + destination.owner + "/"
                + destination.projectName + "/issues?state=all&labels=" + labelOfPost,
            headers: {
                "Authorization": "token " + WORKER.TOKEN
            }
        }).then(function success(response){
            return response;
        }, function error(response){
            return response;
        });
    }

    function haveIssuesAlready(destination){
        if(!isDestinationReady(destination)){
            return;
        }

        return $http({
            method: 'GET',
            url: CONFIG.GITHUB_API_BASE_URL + "/repos/" + destination.owner + "/"
                + destination.projectName + "/issues?state=all",
            headers: {
                "Authorization": "token " + WORKER.TOKEN
            }
        }).then(function success(response){
            return response;
        }, function error(response){
            return response;
        });
    }

    function haveMilestonesAlready(destination){
        if(!isDestinationReady(destination)){
            return;
        }

        return $http({
            method: 'GET',
            url: CONFIG.GITHUB_API_BASE_URL + "/repos/" + destination.owner + "/"
                + destination.projectName + "/milestones?state=all",
            headers: {
                "Authorization": "token " + WORKER.TOKEN
            }
        }).then(function success(response){
            return response;
        }, function error(response){
            return response;
        });
    }

    function getOrgMembership(destination, login){
        return $http({
            method: 'GET',
            url: CONFIG.GITHUB_API_BASE_URL + '/orgs/' + destination.owner + '/memberships/' + login,
            headers: {
                "Authorization": "token " + WORKER.TOKEN
            }
        }).then(success, errorWithSilence);
    }

    function userExistAtDestinationProject(owner, projectName, loginid){
        return $http({
            method: 'GET',
            url: CONFIG.GITHUB_API_BASE_URL + '/repos/' + owner + '/' + projectName
                + '/collaborators/' + loginid,
            headers: {
                "Authorization": "token " + WORKER.TOKEN
            }
        }).then(success, errorWithSilence);
    }

    function importProjectLabels(owner, projectName) {
        return $http({
            method: 'GET',
            url: CONFIG.YONA_SERVER + '/migration/' + owner + '/projects/' + projectName
            + '/labels'
        }).then(successWithData, error);
    }

    function importIssueLabelMap(owner, projectName) {
        var issueLabelMap = {};
        return $http({
            method: 'GET',
            url: CONFIG.YONA_SERVER + '/migration/' + owner + '/projects/' + projectName
            + '/issuelabel'
        }).then(function successCallback(response) {
            response.data.issueLabelPairs.map(function (obj) {
                if (!issueLabelMap[obj.issueId]) {
                    issueLabelMap[obj.issueId] = [];
                }
                issueLabelMap[obj.issueId].push(obj.issueLabelId);
            });
            return issueLabelMap;
        }, error);
    }

    function getSourceProject(owner, projectName) {
        return $http({
            method: 'GET',
            url: CONFIG.YONA_SERVER + '/migration/' + owner + '/projects/' + projectName
        }).then(successWithData, error);
    }

    function getSourceProjects(){
        return $http({
            method: 'GET',
            url: CONFIG.YONA_SERVER + '/migration/projects'
        }).then(success, error);
    }

    function getDestinationProjects() {
        var BASE_URL = CONFIG.GITHUB_API_BASE_URL;
        var page = 1;
        var PER_PAGE = 100;
        var projects = [];
        var MAX_PAGE_FOR_GATHERING = 100;

        return gatheringLists();

        /////////////////////////////////////
        function gatheringLists() {
            return $http({
                method: 'GET',
                url: BASE_URL + '/user/repos?per_page=' + PER_PAGE + '&page=' + page,
                headers: {
                    "Authorization": "token " + USER.TOKEN
                }
            }).then(successThenPaging, error);
        }

        function successThenPaging(response) {
            projects = projects.concat(response.data);

            if (hasNextPage(response.headers('Link'))) {
                page++;
                if (page <= MAX_PAGE_FOR_GATHERING) {
                    return gatheringLists();
                } else {
                    // throw error at condition of too may pages
                    throw "Too many pages!! Limit: Max " + MAX_PAGE_FOR_GATHERING + " page"
                    + " and " + PER_PAGE + " items per page";
                }
            }
            return projects;

            function hasNextPage(linkHeader) {
                return linkHeader && linkHeader.indexOf('; rel="next",') > -1
            }
        }

        function error(response) {
            $log.error("Errors while loading destination projects", response);
            throw response.status + " : " + response.data;
        }
    }

    function getIssues(owner, projectName) {
        return $http({
            method: 'GET',
            url: CONFIG.YONA_SERVER + '/migration/' + owner + '/projects/' + projectName + '/issues'
        }).then(successWithData, error);
    }

    function importMilestones(source, destination) {
        assertIsProjectSelected('Source', source);
        assertIsProjectSelected('Destination', destination);

        importResult.expected = source.milestoneCount;

        var sourceProjectUrl = "/migration/" + source.owner + "/projects/" + source.projectName + "/milestones";
        if(destination.withWikiCommit && destination.withWikiCommit === true){
            sourceProjectUrl = sourceProjectUrl + "?withWikiCommit=" + destination.withWikiCommit;
        }

        return $http({
            method: 'GET',
            url: sourceProjectUrl
        }).then(function success(response) {
            response.data.milestones.forEach(function (data) {
                $http({
                    method: 'POST',
                    url: CONFIG.GITHUB_API_BASE_URL + '/repos/' + destination.owner + '/'
                        + destination.projectName + '/milestones',
                    headers: {
                        "Authorization": "token " + WORKER.TOKEN
                    },
                    data: JSON.stringify(data.milestone)
                }).then(function success(response) {
                    if (response.status !== 201) {
                        importResult.errorData.push(response);
                    }
                    milestoneMapping(data, response);
                    importResult.count++;
                }, function error(response) {
                    var orgData = JSON.parse(response.config.data);
                    importResult.errorData.push(orgData);
                    importResult.errorData.push(response.data.errors[0]);
                    $log.error("milestone import error", response);
                });
            });
            return importResult;
        }, function err(response) {
            $log.error(response);
        });

        function milestoneMapping(data, response) {
            if (!milestoneMapper[destination.projectName]) {
                milestoneMapper[destination.projectName] = {};
            }
            milestoneMapper[destination.projectName][data.milestone.id] = response.data.number;
        }
    }

    function assertIsProjectSelected(name, project) {
        if (!project || !project.owner || !project.projectName) {
            $log.error(project);
            throw name + " isn't ready!"
        }
    }

    function importPosts(source, destination) {
        assertIsProjectSelected('Source', source);
        assertIsProjectSelected('Destination', destination);
        var sourceProjectUrl = "/migration/" + source.owner + "/projects/" + source.projectName + "/posts";
        if(destination.withWikiCommit && destination.withWikiCommit === true){
            sourceProjectUrl = sourceProjectUrl + "?withWikiCommit=" + destination.withWikiCommit;
        }

        return $http({
            method: 'GET',
            url: sourceProjectUrl
        }).then(function success(response) {
            var counter = 0;  // for request limit per min
            response.data.issues.forEach(function (data) {
                preprocessPostData(data);

                setTimeout(function(){
                    $http({
                        method: 'POST',
                        url: CONFIG.GITHUB_API_BASE_URL + '/repos/' + destination.owner + '/'
                        + destination.projectName + '/import/issues',
                        headers: {
                            "Authorization": "token " + WORKER.TOKEN,
                            "Accept": "application/vnd.github.golden-comet-preview+json"
                        },
                        data: JSON.stringify(data)
                    }).then(function success(response) {
                        importResult.count++;
                        if (response.status !== 202) {
                            importResult.errorData.push(response);
                        }
                    }, function error(response) {
                        importResult.errorData.push(response);
                        $log.error("a", response);
                    });
                }, counter * 500);
                counter++;
            });
            return importResult;
        }, error);

        function preprocessPostData(data) {
            // treat no contents issue
            if ("body" in data.issue && !data.issue.body) {
                data.issue.body = "-- no contents --";
            }

            // hardcoded label
            data.issue.labels = [labelOfPost];
        }
    }

    function importIssues(source, destination, issueLabelMap) {
        assertIsProjectSelected('Source', source);
        assertIsProjectSelected('Destination', destination);

        var BASE_API = CONFIG.GITHUB_API_BASE_URL;
        var fullRepoName = destination.owner + '/' + destination.projectName;

        var sourceProjectUrl = "/migration/" + source.owner + "/projects/" + source.projectName + "/issues";
        if(destination.withWikiCommit && destination.withWikiCommit === true){
            sourceProjectUrl = sourceProjectUrl + "?withWikiCommit=" + destination.withWikiCommit;
        }

        return $http({
            method: 'GET',
            url: sourceProjectUrl
        }).then(function success(response) {
            var counter = 0;  // for request limit per min

            response.data.issues.forEach(function (data) {
                var issueData = preprocessIssueData(data, destination, issueLabelMap);

                setTimeout(function(){
                    $http({
                        method: 'POST',
                        url: BASE_API + '/repos/' + fullRepoName + '/import/issues',
                        headers: {
                            "Authorization": "token " + WORKER.TOKEN,
                            "Accept": "application/vnd.github.golden-comet-preview+json"
                        },
                        data: JSON.stringify(issueData)
                    }).then(function success(response) {
                        importResult.count++;
                        if (response.status !== 202) {
                            importResult.errorData.push(response);
                        }
                    }, function error(response) {
                        importResult.errorData.push(response);
                        $log.error("a", response);
                    });
                }, counter * 500);
                counter++;
            });
            return importResult;
        }, function error(response) {
            $log.error("b", response);
        });
    }

    function preprocessIssueData(data, destination, issueLabelMap) {
        var projectName = destination.projectName;
        // assignee mapping
        if(destination.assignees && destination.assignees[data.issue.assignee]){
            data.issue.assignee = destination.assignees[data.issue.assignee].login;
        } else {
            delete data.issue.assignee;
        }

        // issue label mapping
        data.issue.labels = issueLabelMap[data.issue.id];
        delete data.issue.id;

        // treat no contents issue
        if ("body" in data.issue && !data.issue.body) {
            var NO_CONTENTS = "-- no contents --";
            $log.log(NO_CONTENTS);
            data.issue.body = NO_CONTENTS;
        }

        // milestone mapping
        if ("milestoneId" in data.issue) {
            if(milestoneMapper[projectName]){
                data.issue.milestone = milestoneMapper[projectName][data.issue.milestoneId];
            } else {
                delete data.issue.milestone;
            }
            delete data.issue.milestoneId;
        }
        return data;
    }

    function success(response){
        $log.log(response);
        return response;
    }

    function successWithData(response){
        $log.log(response);
        return response.data;
    }

    function error(response){
        $log.error(response);
        return response;
    }

    function errorWithSilence(response){
        return response;
    }
}

module.exports = migrationService;

},{"angular":"angular"}],6:[function(require,module,exports){
/* NProgress, (c) 2013, 2014 Rico Sta. Cruz - http://ricostacruz.com/nprogress
 * @license MIT */

;(function(root, factory) {

  if (typeof define === 'function' && define.amd) {
    define(factory);
  } else if (typeof exports === 'object') {
    module.exports = factory();
  } else {
    root.NProgress = factory();
  }

})(this, function() {
  var NProgress = {};

  NProgress.version = '0.2.0';

  var Settings = NProgress.settings = {
    minimum: 0.08,
    easing: 'ease',
    positionUsing: '',
    speed: 200,
    trickle: true,
    trickleRate: 0.02,
    trickleSpeed: 800,
    showSpinner: true,
    barSelector: '[role="bar"]',
    spinnerSelector: '[role="spinner"]',
    parent: 'body',
    template: '<div class="bar" role="bar"><div class="peg"></div></div><div class="spinner" role="spinner"><div class="spinner-icon"></div></div>'
  };

  /**
   * Updates configuration.
   *
   *     NProgress.configure({
   *       minimum: 0.1
   *     });
   */
  NProgress.configure = function(options) {
    var key, value;
    for (key in options) {
      value = options[key];
      if (value !== undefined && options.hasOwnProperty(key)) Settings[key] = value;
    }

    return this;
  };

  /**
   * Last number.
   */

  NProgress.status = null;

  /**
   * Sets the progress bar status, where `n` is a number from `0.0` to `1.0`.
   *
   *     NProgress.set(0.4);
   *     NProgress.set(1.0);
   */

  NProgress.set = function(n) {
    var started = NProgress.isStarted();

    n = clamp(n, Settings.minimum, 1);
    NProgress.status = (n === 1 ? null : n);

    var progress = NProgress.render(!started),
        bar      = progress.querySelector(Settings.barSelector),
        speed    = Settings.speed,
        ease     = Settings.easing;

    progress.offsetWidth; /* Repaint */

    queue(function(next) {
      // Set positionUsing if it hasn't already been set
      if (Settings.positionUsing === '') Settings.positionUsing = NProgress.getPositioningCSS();

      // Add transition
      css(bar, barPositionCSS(n, speed, ease));

      if (n === 1) {
        // Fade out
        css(progress, { 
          transition: 'none', 
          opacity: 1 
        });
        progress.offsetWidth; /* Repaint */

        setTimeout(function() {
          css(progress, { 
            transition: 'all ' + speed + 'ms linear', 
            opacity: 0 
          });
          setTimeout(function() {
            NProgress.remove();
            next();
          }, speed);
        }, speed);
      } else {
        setTimeout(next, speed);
      }
    });

    return this;
  };

  NProgress.isStarted = function() {
    return typeof NProgress.status === 'number';
  };

  /**
   * Shows the progress bar.
   * This is the same as setting the status to 0%, except that it doesn't go backwards.
   *
   *     NProgress.start();
   *
   */
  NProgress.start = function() {
    if (!NProgress.status) NProgress.set(0);

    var work = function() {
      setTimeout(function() {
        if (!NProgress.status) return;
        NProgress.trickle();
        work();
      }, Settings.trickleSpeed);
    };

    if (Settings.trickle) work();

    return this;
  };

  /**
   * Hides the progress bar.
   * This is the *sort of* the same as setting the status to 100%, with the
   * difference being `done()` makes some placebo effect of some realistic motion.
   *
   *     NProgress.done();
   *
   * If `true` is passed, it will show the progress bar even if its hidden.
   *
   *     NProgress.done(true);
   */

  NProgress.done = function(force) {
    if (!force && !NProgress.status) return this;

    return NProgress.inc(0.3 + 0.5 * Math.random()).set(1);
  };

  /**
   * Increments by a random amount.
   */

  NProgress.inc = function(amount) {
    var n = NProgress.status;

    if (!n) {
      return NProgress.start();
    } else {
      if (typeof amount !== 'number') {
        amount = (1 - n) * clamp(Math.random() * n, 0.1, 0.95);
      }

      n = clamp(n + amount, 0, 0.994);
      return NProgress.set(n);
    }
  };

  NProgress.trickle = function() {
    return NProgress.inc(Math.random() * Settings.trickleRate);
  };

  /**
   * Waits for all supplied jQuery promises and
   * increases the progress as the promises resolve.
   *
   * @param $promise jQUery Promise
   */
  (function() {
    var initial = 0, current = 0;

    NProgress.promise = function($promise) {
      if (!$promise || $promise.state() === "resolved") {
        return this;
      }

      if (current === 0) {
        NProgress.start();
      }

      initial++;
      current++;

      $promise.always(function() {
        current--;
        if (current === 0) {
            initial = 0;
            NProgress.done();
        } else {
            NProgress.set((initial - current) / initial);
        }
      });

      return this;
    };

  })();

  /**
   * (Internal) renders the progress bar markup based on the `template`
   * setting.
   */

  NProgress.render = function(fromStart) {
    if (NProgress.isRendered()) return document.getElementById('nprogress');

    addClass(document.documentElement, 'nprogress-busy');
    
    var progress = document.createElement('div');
    progress.id = 'nprogress';
    progress.innerHTML = Settings.template;

    var bar      = progress.querySelector(Settings.barSelector),
        perc     = fromStart ? '-100' : toBarPerc(NProgress.status || 0),
        parent   = document.querySelector(Settings.parent),
        spinner;
    
    css(bar, {
      transition: 'all 0 linear',
      transform: 'translate3d(' + perc + '%,0,0)'
    });

    if (!Settings.showSpinner) {
      spinner = progress.querySelector(Settings.spinnerSelector);
      spinner && removeElement(spinner);
    }

    if (parent != document.body) {
      addClass(parent, 'nprogress-custom-parent');
    }

    parent.appendChild(progress);
    return progress;
  };

  /**
   * Removes the element. Opposite of render().
   */

  NProgress.remove = function() {
    removeClass(document.documentElement, 'nprogress-busy');
    removeClass(document.querySelector(Settings.parent), 'nprogress-custom-parent');
    var progress = document.getElementById('nprogress');
    progress && removeElement(progress);
  };

  /**
   * Checks if the progress bar is rendered.
   */

  NProgress.isRendered = function() {
    return !!document.getElementById('nprogress');
  };

  /**
   * Determine which positioning CSS rule to use.
   */

  NProgress.getPositioningCSS = function() {
    // Sniff on document.body.style
    var bodyStyle = document.body.style;

    // Sniff prefixes
    var vendorPrefix = ('WebkitTransform' in bodyStyle) ? 'Webkit' :
                       ('MozTransform' in bodyStyle) ? 'Moz' :
                       ('msTransform' in bodyStyle) ? 'ms' :
                       ('OTransform' in bodyStyle) ? 'O' : '';

    if (vendorPrefix + 'Perspective' in bodyStyle) {
      // Modern browsers with 3D support, e.g. Webkit, IE10
      return 'translate3d';
    } else if (vendorPrefix + 'Transform' in bodyStyle) {
      // Browsers without 3D support, e.g. IE9
      return 'translate';
    } else {
      // Browsers without translate() support, e.g. IE7-8
      return 'margin';
    }
  };

  /**
   * Helpers
   */

  function clamp(n, min, max) {
    if (n < min) return min;
    if (n > max) return max;
    return n;
  }

  /**
   * (Internal) converts a percentage (`0..1`) to a bar translateX
   * percentage (`-100%..0%`).
   */

  function toBarPerc(n) {
    return (-1 + n) * 100;
  }


  /**
   * (Internal) returns the correct CSS for changing the bar's
   * position given an n percentage, and speed and ease from Settings
   */

  function barPositionCSS(n, speed, ease) {
    var barCSS;

    if (Settings.positionUsing === 'translate3d') {
      barCSS = { transform: 'translate3d('+toBarPerc(n)+'%,0,0)' };
    } else if (Settings.positionUsing === 'translate') {
      barCSS = { transform: 'translate('+toBarPerc(n)+'%,0)' };
    } else {
      barCSS = { 'margin-left': toBarPerc(n)+'%' };
    }

    barCSS.transition = 'all '+speed+'ms '+ease;

    return barCSS;
  }

  /**
   * (Internal) Queues a function to be executed.
   */

  var queue = (function() {
    var pending = [];
    
    function next() {
      var fn = pending.shift();
      if (fn) {
        fn(next);
      }
    }

    return function(fn) {
      pending.push(fn);
      if (pending.length == 1) next();
    };
  })();

  /**
   * (Internal) Applies css properties to an element, similar to the jQuery 
   * css method.
   *
   * While this helper does assist with vendor prefixed property names, it 
   * does not perform any manipulation of values prior to setting styles.
   */

  var css = (function() {
    var cssPrefixes = [ 'Webkit', 'O', 'Moz', 'ms' ],
        cssProps    = {};

    function camelCase(string) {
      return string.replace(/^-ms-/, 'ms-').replace(/-([\da-z])/gi, function(match, letter) {
        return letter.toUpperCase();
      });
    }

    function getVendorProp(name) {
      var style = document.body.style;
      if (name in style) return name;

      var i = cssPrefixes.length,
          capName = name.charAt(0).toUpperCase() + name.slice(1),
          vendorName;
      while (i--) {
        vendorName = cssPrefixes[i] + capName;
        if (vendorName in style) return vendorName;
      }

      return name;
    }

    function getStyleProp(name) {
      name = camelCase(name);
      return cssProps[name] || (cssProps[name] = getVendorProp(name));
    }

    function applyCss(element, prop, value) {
      prop = getStyleProp(prop);
      element.style[prop] = value;
    }

    return function(element, properties) {
      var args = arguments,
          prop, 
          value;

      if (args.length == 2) {
        for (prop in properties) {
          value = properties[prop];
          if (value !== undefined && properties.hasOwnProperty(prop)) applyCss(element, prop, value);
        }
      } else {
        applyCss(element, args[1], args[2]);
      }
    }
  })();

  /**
   * (Internal) Determines if an element or space separated list of class names contains a class name.
   */

  function hasClass(element, name) {
    var list = typeof element == 'string' ? element : classList(element);
    return list.indexOf(' ' + name + ' ') >= 0;
  }

  /**
   * (Internal) Adds a class to an element.
   */

  function addClass(element, name) {
    var oldList = classList(element),
        newList = oldList + name;

    if (hasClass(oldList, name)) return; 

    // Trim the opening space.
    element.className = newList.substring(1);
  }

  /**
   * (Internal) Removes a class from an element.
   */

  function removeClass(element, name) {
    var oldList = classList(element),
        newList;

    if (!hasClass(element, name)) return;

    // Replace the class name.
    newList = oldList.replace(' ' + name + ' ', ' ');

    // Trim the opening and closing spaces.
    element.className = newList.substring(1, newList.length - 1);
  }

  /**
   * (Internal) Gets a space separated list of the class names on the element. 
   * The list is wrapped with a single space on each end to facilitate finding 
   * matches within the list.
   */

  function classList(element) {
    return (' ' + (element.className || '') + ' ').replace(/\s+/gi, ' ');
  }

  /**
   * (Internal) Removes an element from the DOM.
   */

  function removeElement(element) {
    element && element.parentNode && element.parentNode.removeChild(element);
  }

  return NProgress;
});


},{}]},{},[1])


//# sourceMappingURL=yona.Migration.js.map
