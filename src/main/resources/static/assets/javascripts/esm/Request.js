/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 단추나 링크를 눌러 서버에 요청(POST, PUT, DELETE)을 보내고 화면을 갱신한다.
 * Yobi 시절의 jQuery 플러그인 `jquery.requestAs.js`를 표준 기능(`fetch`, `CustomEvent`)으로 바꾼 것이다(S-07).
 *
 * <h4>선언형</h4>
 * 요소에 방식과 주소를 적어 두면 문서가 준비될 때 요소마다 클릭과 Enter 처리기를 단다. 나중에 들어온 요소는 `bind(루트)`로 건다.
 * <pre>
 * &lt;button data-request-method="delete" data-request-uri="/issue/3/delete"&gt;삭제&lt;/button&gt;
 * </pre>
 * 주소(`data-request-uri`)가 없으면 `href`를 쓴다. `GET`을 건 `&lt;a&gt;`는 보통 링크이므로 아무것도 달지 않는다.
 *
 * <h4>직접 부르기</h4>
 * <pre>
 * yona.Request.send("delete", url).then(function(result){ if(!result.ok){ ... } });
 * </pre>
 *
 * <h4>서버 계약</h4>
 * 성공(2xx)이면 상태가 204이고 `Location` 머리글이 있을 때 그곳으로 이동하고, 아니면 화면을 새로 고친다.
 * 실패는 사용자에게 알리지 않는다(v1.6 그대로). 알리려면 `request:error`를 듣거나 `send`의 결과를 본다.
 *
 * <h4>이벤트</h4>
 * 요소를 넘기면 그 요소에 표준 `CustomEvent`를 보낸다(버블링하지 않는다).
 * <ul>
 *   <li>`request:before` 요청 전. `preventDefault()`하면 요청을 보내지 않는다.</li>
 *   <li>`request:load` 성공. `detail`은 `{status, response}`. `preventDefault()`하면 이동과 새로 고침을 하지 않는다.</li>
 *   <li>`request:error` 실패. `detail`은 `{status, response}`(네트워크 오류는 `status` 0, `response` null).</li>
 * </ul>
 *
 * 토큰(CSRF)은 `yona.Csrf.js`가 `fetch`를 가로채 싣는다. 서버가 아약스 요청을 알아보는 `X-Requested-With`는 여기서 단다.
 */

import { register } from "./namespace.js";

const Request = (function(){

    /** 이동과 새로 고침. 시험에서 바꿔 끼울 수 있다 */
    const htNavigation = {
        "to"    : function(sUrl){ document.location.href = sUrl; },
        "reload": function(){ document.location.reload(); }
    };

    /** 이미 처리기를 단 요소 */
    const oBound = new WeakSet();

    /**
     * 요소에 이벤트를 보낸다.
     *
     * @param {HTMLElement|null} elTarget
     * @param {String} sType
     * @param {Object} htDetail
     * @return {Boolean} 취소되지 않았으면 true
     */
    function _dispatch(elTarget, sType, htDetail){
        if(!elTarget){
            return true;
        }
        return elTarget.dispatchEvent(new CustomEvent(sType, {"detail": htDetail, "cancelable": true}));
    }

    /**
     * 서버에 요청을 보낸다.
     *
     * @param {String} sMethod post, put, delete 등(대소문자 무관)
     * @param {String} sUrl
     * @param {HTMLElement} [elSource] 이벤트를 받을 요소
     * @return {Promise<{ok: Boolean, status: Number}>} 실패해도 거부하지 않고 결과로 알린다. 요청을 막았으면 `{ok: false, status: -1}`
     */
    function send(sMethod, sUrl, elSource){
        if(_dispatch(elSource, "request:before", {"method": sMethod, "url": sUrl}) === false){
            return Promise.resolve({"ok": false, "status": -1});
        }

        return fetch(sUrl, {
            "method" : String(sMethod).toUpperCase(),
            "headers": {"X-Requested-With": "XMLHttpRequest"},
            "cache"  : "no-store"
        }).then((oResponse) =>{
            if(!oResponse.ok){
                _dispatch(elSource, "request:error", {"status": oResponse.status, "response": oResponse});
                return {"ok": false, "status": oResponse.status};
            }
            if(_dispatch(elSource, "request:load", {"status": oResponse.status, "response": oResponse}) !== false){
                const sLocation = oResponse.headers.get("Location");
                if(oResponse.status === 204 && sLocation){
                    yona.Request.navigation.to(sLocation);
                } else {
                    yona.Request.navigation.reload();
                }
            }
            return {"ok": true, "status": oResponse.status};
        }, () =>{
            _dispatch(elSource, "request:error", {"status": 0, "response": null});
            return {"ok": false, "status": 0};
        });
    }

    /**
     * 눌렀을 때 요청을 보낸다. 방식과 주소는 누르는 때 요소에서 읽는다.
     */
    function _onActivate(weEvt){
        const elTarget = weEvt.currentTarget;
        if(weEvt.type === "keydown" && !$yona.isEnterKey(weEvt)){
            return;
        }
        weEvt.preventDefault();
        weEvt.stopPropagation();
        send(elTarget.dataset.requestMethod, elTarget.dataset.requestUri || elTarget.getAttribute("href"), elTarget);
    }

    /**
     * [elRoot] 안의 `data-request-method` 요소(루트 자신 포함)마다 처리기를 단다. 이미 단 요소는 건너뛴다.
     *
     * @param {Document|HTMLElement} elRoot
     */
    function bind(elRoot){
        const aElements = Array.prototype.slice.call(elRoot.querySelectorAll("[data-request-method]"));
        if(elRoot.matches && elRoot.matches("[data-request-method]")){
            aElements.unshift(elRoot);
        }
        aElements.forEach((elTarget) =>{
            if(oBound.has(elTarget)){
                return;
            }
            // GET 링크는 보통 링크이다(C003)
            if(elTarget.dataset.requestMethod.toLowerCase() === "get" && elTarget.tagName.toLowerCase() === "a"){
                return;
            }
            oBound.add(elTarget);
            elTarget.style.cursor = "pointer";
            elTarget.addEventListener("click", _onActivate);
            elTarget.addEventListener("keydown", _onActivate);
        });
    }

    if(document.readyState === "loading"){
        document.addEventListener("DOMContentLoaded", () =>{ bind(document); });
    } else {
        bind(document);
    }

    return {
        "send"      : send,
        "bind"      : bind,
        "navigation": htNavigation
    };
})();

// 옛 전역 이름으로도 부를 수 있게 등록한다
register("yona.Request", Request);
