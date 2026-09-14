/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 *  https://yona.io
 **/

// P3-69: legacy service/yobi.git.View.js의 _updateState()/_setStateUpdateTimer() 대응.
// PR 뷰(overview 탭)에서 10초 간격으로 GET .../pull/{number}/state를 폴링해 #state 배너와
// Accept 버튼(#pr-accept-button)을 새로고침 없이 갱신한다. legacy와 동일하게 "받아온 HTML이
// 이전과 같으면 DOM을 건드리지 않는다"는 최적화를 재현한다. PR이 CLOSED/MERGED거나
// 병합 진행 중(isMerging)이면 폴링을 시작하지 않거나(shouldPoll=false) 중단한다.
(function(ns){

    "use strict";

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(options){

        var vars = {};
        var elements = {};

        function _init(options){
            _initElement();
            _initVar(options || {});
            _setStateUpdateTimer();
        }

        function _initElement(){
            elements.state = $("#state");
            elements.acceptButton = $("#pr-accept-button");
        }

        function _initVar(options){
            vars.stateUrl = options.stateUrl;
            vars.isStateUpdating = false;
            vars.stateUpdateTimer = null;
            vars.stateUpdateInterval = options.stateUpdateInterval || 10000; // legacy nStateUpdateInterval과 동일한 10초
            vars.stateHTML = "";
            // 최초 페이지 로드 시점의 상태(pr.state/isMerging) 기준 — 이미 CLOSED/MERGED거나
            // 병합 진행 중이면 애초에 폴링을 시작하지 않는다.
            vars.shouldPoll = options.shouldPoll !== false;
        }

        /**
         * legacy _updateState() 대응. 서버가 돌려주는 합성 프래그먼트(#pr-state-poll-body +
         * #pr-accept-button-poll-body)를 파싱해 각각 실제 컨테이너(#state, #pr-accept-button)로
         * 옮겨 넣는다.
         */
        function _updateState(){
            if(vars.isStateUpdating){
                return;
            }
            vars.isStateUpdating = true;

            fetch(vars.stateUrl, { headers: { "X-Requested-With": "XMLHttpRequest" } })
                .then(function(response){
                    if(!response.ok){
                        throw new Error("state fetch failed: " + response.status);
                    }
                    return response.text();
                })
                .then(_onStateFetched)
                .catch(function(){
                    // 네트워크/서버 오류는 무시하고 다음 폴링 주기에 다시 시도한다.
                })
                .then(function(){
                    vars.isStateUpdating = false;
                });
        }

        function _onStateFetched(html){
            // update state only if HTML has changed (legacy와 동일한 불필요 리플로우 방지)
            if(html === vars.stateHTML){
                return;
            }
            vars.stateHTML = html;

            var $parsed = $("<div></div>").html(html);
            var $stateBody = $parsed.find("#pr-state-poll-body");
            var $acceptButtonBody = $parsed.find("#pr-accept-button-poll-body");

            elements.state.html($stateBody.html());
            elements.acceptButton.html($acceptButtonBody.html());

            var prState = $stateBody.attr("data-pr-state");
            var prMerging = $stateBody.attr("data-pr-merging") === "true";
            if(prState === "CLOSED" || prState === "MERGED" || prMerging){
                _unsetStateUpdateTimer();
            }
        }

        function _setStateUpdateTimer(){
            _unsetStateUpdateTimer();

            if(!vars.shouldPoll || !vars.stateUrl){
                return;
            }

            vars.stateUpdateTimer = setInterval(function(){
                if(vars.isStateUpdating !== true){
                    _updateState();
                }
            }, vars.stateUpdateInterval);
        }

        function _unsetStateUpdateTimer(){
            if(vars.stateUpdateTimer != null){
                clearInterval(vars.stateUpdateTimer);
            }
            vars.stateUpdateTimer = null;
        }

        _init(options || {});
    };

})("yona.pullrequest.View");
