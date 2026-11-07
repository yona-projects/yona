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
yobi.ShortcutKey = (function(htOptions){

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
        htVar.htKeycodeMap = {
            '13':'ENTER', '38':'UP', '40':'DOWN', '37':'LEFT', '39':'RIGHT', '13':'ENTER', '27':'ESC',
            '32':'SPACE', '8':'BACKSPACE', '9':'TAB', '46':'DELETE', '33':'PAGEUP', '34':'PAGEDOWN', '36':'HOME', '35':'END',
            '65':'A', '66':'B', '67':'C', '68':'D', '69':'E', '70':'F', '71':'G', '72':'H', '73':'I', '74':'J', '75':'K', '76':'L',
            '77':'M', '78':'N', '79':'O', '80':'P', '81':'Q', '82':'R', '83':'S', '84':'T', '85':'U', '86':'V', '87':'W', '88':'X',
            '89':'Y', '90':'Z',    '48':'0', '49':'1', '50':'2', '51':'3', '52':'4', '53':'5', '54':'6', '55':'7', '56':'8', '57':'9',
            '219':'[', '221':']', '186':';', '222':'\'', '188':',', '190':'.', '191':'/', '189':'-', '187':'=', '220':'\\', '192':'`',
            '112':'F1', '113':'F2', '114':'F3', '115':'F4', '116':'F5', '117':'F6', '118':'F7', '119':'F8', '120':'F9', '121':'F10', '122':'F11', '123':'F12'
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

    function _runEventHandler(fHandler, weEvt, sKeyInput){
        var sTagName = weEvt.target.tagName.toUpperCase();
        var htInfo = {
            "weEvt"     : weEvt,
            "welTarget" : $(weEvt.target),
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
     * generate key string from keyDown event
     * @param {Wrapped Event} weEvt
     */
    function _getKeyString(weEvt){
        var sMainKey = htVar.htKeycodeMap[weEvt.keyCode];
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
