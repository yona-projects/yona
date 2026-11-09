/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
yona.ShortcutKey = (function(htOptions){

    var htVar = {};
    var htHandlers = {};

    /**
     * initialize
     */
    function _init(htOptions){
        _initVar();
        _attachEvent();
    }

    /**
     * initialize variables
     */
    function _initVar(){
        htVar.rxTrim = /\s+/g;
        htVar.aFormTags = ["INPUT", "TEXTAREA"];
        htVar.aCombinationKeys = ["CTRL", "ALT", "SHIFT"];
        // KeyboardEvent.key 가 이름이 있는 키(이동, 편집 키)일 때의 이름. 글자(A~Z)와 숫자, 기호, F1~F12는 _getMainKey 가 따로 만든다
        htVar.htNamedKeys = {
            'Enter':'ENTER', 'ArrowUp':'UP', 'ArrowDown':'DOWN', 'ArrowLeft':'LEFT', 'ArrowRight':'RIGHT', 'Escape':'ESC',
            ' ':'SPACE', 'Backspace':'BACKSPACE', 'Tab':'TAB', 'Delete':'DELETE', 'PageUp':'PAGEUP', 'PageDown':'PAGEDOWN', 'Home':'HOME', 'End':'END'
        };
        // 기호 키는 Shift를 누르면 key 가 달라지므로(예: / → ?) 물리 키(code)로 이름을 정한다(옛 keyCode 표와 같다)
        htVar.htSymbolCodes = {
            'Minus':'-', 'Equal':'=', 'BracketLeft':'[', 'BracketRight':']', 'Semicolon':';', 'Quote':'\'', 'Comma':',', 'Period':'.',
            'Slash':'/', 'Backslash':'\\', 'Backquote':'`'
        };
    }

    /**
     * add event listener
     */
    function _attachEvent(){
        $(window).on({
            "keydown"     : _onKeyDown,
            "beforeunload": destroy // free memory
        });
    }

    function _detachEvent(){
        $(window).off({
            "keydown"     : _onKeyDown,
            "beforeunload": destroy // free memory
        });
    }

    /**
     * global keyDown event handler
     */
    function _onKeyDown(weEvt){
        var sKeyInput = _getKeyString(weEvt);
        var fHandler = htHandlers[sKeyInput];

        if(typeof fHandler === "function"){
            _runEventHandler(fHandler, weEvt, sKeyInput);
        }
    }

    /**
     * 키를 누른 실제 요소를 돌려준다.
     * Shadow DOM 안의 입력란(`<title-input>`)에서 난 이벤트는 경계에서 대상이 호스트로 바뀌어 `weEvt.target`이 입력란이 아니다.
     * 그러면 입력란에 글자를 치는 것을 단축키로 잘못 알아본다. `composedPath()`의 첫 요소가 실제 대상이다.
     *
     * @param {Wrapped Event} weEvt
     */
    function _getActualTarget(weEvt){
        var oEvt = weEvt.originalEvent;
        var aPath = (oEvt && typeof oEvt.composedPath === "function") ? oEvt.composedPath() : [];
        var elActual = aPath[0];
        return (elActual && elActual.nodeType === 1) ? elActual : weEvt.target;
    }

    function _runEventHandler(fHandler, weEvt, sKeyInput){
        var elTarget = _getActualTarget(weEvt);
        var sTagName = elTarget.tagName.toUpperCase();
        var htInfo = {
            "weEvt"     : weEvt,
            "welTarget" : $(elTarget),
            "sTagName"  : sTagName,
            "sKeyInput" : sKeyInput,
            "bFormInput": (htVar.aFormTags.indexOf(sTagName) > -1)
        };

        try {
            fHandler(htInfo);
        }catch(e){} finally {
            htInfo = null;
        }
    }

    /**
     * attach Shortcut Key Handler
     * @param {String} vKey keyCombiation String e.g. CTRL+ENTER
     * @param {String} fHandler handler function
     * or
     * @param {Hash Table} vKey {"keyCombination:function(){}, "key":function(){}}
     */
    function attachHandler(vKey, fHandler){
        if(typeof vKey === "string"){
            return _setHandler(vKey, fHandler);
        }

        var fHandler, sKey;
        for(sKey in vKey){
            fHandler = vKey[sKey];
            _setHandler(sKey, fHandler);
        }
    }

    function _setHandler(sKey, fHandler){
        sKey = _normalizeKeyString(sKey);
        htHandlers[sKey] = fHandler;
    }

    /**
     * detach Shortcut Key Handler
     * @param {String} sKey
     * @param {String} fHandler
     */
    function detachHandler(sKeyInput){
        var sKey = _normalizeKeyString(sKeyInput);
        delete htHandlers[sKey];
    }

    /**
     * keyDown 이벤트가 가리키는 키의 이름을 돌려준다. 이름이 없는 키(표에 없는 키)는 undefined.
     *
     * 옛 구현은 `keyCode` 표를 썼다. `KeyboardEvent.key`로 바꾸되 결과는 같게 맞췄다.
     * - 한글 등 입력기로 조합하는 중(`isComposing`, `keyCode` 229, `key` "Process")의 키는 단축키가 아니다.
     * - 글자는 `key`가 라틴 글자면 그 글자(대문자), 아니면(러시아어 배열 등) 물리 키(`KeyG` → G)다. `keyCode`도 그랬다.
     * - 숫자와 기호는 물리 키(`Digit1`, `Slash`)다. 숫자 패드(`Numpad1`)는 옛 표에 없었으므로 제외한다.
     *
     * @param {Wrapped Event} weEvt
     * @return {String|undefined}
     */
    function _getMainKey(weEvt){
        var oEvt = weEvt.originalEvent || weEvt;
        var sKey = oEvt.key;
        var sCode = oEvt.code || "";
        var aMatch;

        if(!sKey || oEvt.isComposing === true || oEvt.keyCode === 229 || sKey === "Process"){
            return;
        }
        if(htVar.htNamedKeys.hasOwnProperty(sKey)){
            return htVar.htNamedKeys[sKey];
        }
        if(/^F([1-9]|1[0-2])$/.test(sKey)){
            return sKey;
        }
        if(/^[a-z]$/i.test(sKey)){
            return sKey.toUpperCase();
        }
        if((aMatch = /^Key([A-Z])$/.exec(sCode)) && sKey.length === 1 && /[^\x00-\x7f]/.test(sKey)){
            return aMatch[1]; // 라틴이 아닌 배열의 글자 키
        }
        if((aMatch = /^Digit([0-9])$/.exec(sCode))){
            return aMatch[1];
        }
        if(htVar.htSymbolCodes.hasOwnProperty(sCode)){
            return htVar.htSymbolCodes[sCode];
        }
    }

    /**
     * generate key string from keyDown event
     * @param {Wrapped Event} weEvt
     */
    function _getKeyString(weEvt){
        var sMainKey = _getMainKey(weEvt);
        if(typeof sMainKey === "undefined"){ // ignore event if not on keyMap
            return;
        }

        // generate key combination
        var aKeys = [];
        var sKeyString = "";

        if(weEvt.altKey){
            aKeys.push("ALT");
        }

        if(weEvt.ctrlKey || weEvt.metaKey){
            aKeys.push("CTRL");
        }

        if(weEvt.shiftKey){
            aKeys.push("SHIFT");
        }

        aKeys.push(sMainKey);
        sKeyString = aKeys.join("+").toUpperCase();

        return sKeyString;
    }

    /**
     * normalize Key String
     * @param {String} sKey
     */
    function _normalizeKeyString(sKey){
        sKey = sKey.toUpperCase() || "";
        sKey = sKey.replace(htVar.rxTrim, '');
        sKey = sKey.split("+").sort(function(v){
            return -1 * (htVar.aCombinationKeys.indexOf(v));
        }).join("+");

        return sKey;
    }

    /**
     * Get Key Handlers. for debug.
     * @return {Hash Table}
     */
    function getHandlers(){
        return htHandlers;
    }

    /**
     * set keyMap link
     * @param {Hash Table} htKeyMap
     * @example
     * setKeymapLink({
     *    "N": "http://www.naver.com"
     * });
     */
    function setKeymapLink(htKeyMap){
        var sKey;
        var fHandler = function(htInfo){
            if(!htInfo.bFormInput){
                document.location.href = htKeyMap[htInfo.sKeyInput];
            }
        };

        for(sKey in htKeyMap){
            if(htKeyMap[sKey]){
                attachHandler(sKey, fHandler);
            } else {
                detachHandler(sKey);
            }
        }
    }

    /**
     * destroy this
     */
    function destroy(){
        _detachEvent();
        htHandlers = htVar = null;
    }

    _init(htOptions);

    // public interface
    return {
        "attach": attachHandler,
        "detach": detachHandler,
        "getHandlers": getHandlers,
        "setKeymapLink": setKeymapLink
    };
})();
