/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 *  https://yona.io
 **/

// legacy service/yobi.git.View.js의 _updateState()/_setStateUpdateTimer() 대응.
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
            elements.state = document.getElementById("state");
            elements.acceptButton = document.getElementById("pr-accept-button");
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

            var parsed = document.createElement("div");
            parsed.innerHTML = html;
            var stateBody = parsed.querySelector("#pr-state-poll-body");
            var acceptButtonBody = parsed.querySelector("#pr-accept-button-poll-body");

            // 원본 jQuery `.html($x.html())`은 $x가 매치 없이 비어 있으면 `.html()`
            // getter가 undefined를 반환하고, `.html(undefined)` setter는 실제로는
            // 아무 것도 바꾸지 않는 no-op이 된다(비우지 않고 기존 내용 유지) - 그
            // quirk를 그대로 재현하기 위해 대상이 실제로 있을 때만 innerHTML을 갱신한다.
            if(stateBody && elements.state){
                elements.state.innerHTML = stateBody.innerHTML;
            }
            if(acceptButtonBody && elements.acceptButton){
                elements.acceptButton.innerHTML = acceptButtonBody.innerHTML;
            }

            var prState = stateBody ? stateBody.getAttribute("data-pr-state") : undefined;
            var prMerging = stateBody ? stateBody.getAttribute("data-pr-merging") === "true" : false;
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
