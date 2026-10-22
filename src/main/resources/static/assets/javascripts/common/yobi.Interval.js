/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihan Kim
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
yobi = yobi || {};
yobi.Interval = (function(htOptions){

    var htVar = {};
    var htTimers = {};

    /**
     * Initialize yobi.Interval
     */
    function _init(htOptions){
        htVar.bSleep = false;
        htVar.nTimerInput = null;
        htVar.nLastInput  = new Date().getTime();

        _attachEvent();

        // if bCaffeine true
        // timers will not get sleep with input idle
        if(!htOptions.bCaffeine){
            _watchUserInput();
        }
    }

    /**
     * wrapper of window.setInterval
     *
     * @param {Function} fRunner
     * @param {Number} nInterval
     * @return {Number} nTimerId
     */
    function _setInterval(fRunner, nInterval){
        var nTimerId = setInterval(fRunner, nInterval);

        htTimers[nTimerId] = {
            "fRunner"  : fRunner,
            "nInterval": nInterval,
            "bActive"  : true
        };

        return nTimerId;
    }

    /**
     * wrapper of window.clearInterval
     *
     * @param {Number} nTimerId
     */
    function _clearInterval(nTimerId){
        clearInterval(nTimerId);
        delete htTimers[nTimerId];
    }

    /**
     * add listeners to window object
     */
    function _attachEvent(){
        $(window).on({
            "scroll"   : _onUserInput,
            "mousedown": _onUserInput,
            "mousemove": _onUserInput,
            "keypress" : _onUserInput,
            "beforeunload": _destroy
        });
    }

    /**
     * remove listeners to window object
     */
    function _detachEvent(){
        $(window).off({
            "scroll"   : _onUserInput,
            "mousedown": _onUserInput,
            "mousemove": _onUserInput,
            "keypress" : _onUserInput,
        });
    }

    /**
     * activate timers
     */
    function _activateTimers(){
        var htTimer;

        for(var nTimerId in htTimers){
            htTimer = htTimers[nTimerId];

            if(htTimer.bActive === false){
                _setInterval(htTimer.fRunner, htTimer.nInterval);
                delete htTimers[nTimerId];
            }
        }
    }

    /**
     * deactivate timers
     */
    function _deactivateTimers(){
        for(var nTimerId in htTimers){
            htTimers[nTimerId].bActive = false;
            clearInterval(nTimerId);
        }
    }

    /**
     * detect user interaction events
     */
    function _onUserInput(weEvt){
        htVar.nLastInput = weEvt.timeStamp;
    }

    /**
     * start to watch userInput
     * deactivate all timers automatically if no user input in 60sec,
     * and reactivate when any input has detected
     */
    function _watchUserInput(){
        if(htVar.nTimerInput !== null){
            return;
        }

        htVar.nTimerInput = setInterval(function(){
            var nFromLastInput = (new Date().getTime() - htVar.nLastInput);

            if(!htVar.bSleep && nFromLastInput >= 60000){ // get sleep if no input in 60s
                _deactivateTimers();
                htVar.bSleep = true;
            } else if(htVar.bSleep && (nFromLastInput <= 1000)){ // wake up if new input detected
                htVar.bSleep = false;
                _activateTimers();
                return;
            }
        }, 500);
    }

    /**
     * stop to watch userInput
     */
    function _unwatchUserInput(){
        clearInterval(htVar.nTimerInput);
        htVar.nTimerInput = null;
    }

    /**
     * set lastInput to now
     */
    function _updateLastInput(){
        htVar.nLastInput = new Date().getTime();
    }

    /**
     * deactivate all timers beforeunload
     */
    function _destroy(){
        _unwatchUserInput();
        _deactivateTimers();
        _detachEvent();

        htTimers = htVar = null;
    }

    _init(htOptions || {});

    /**
     * pubilc interface
     *
     * set/clear method
     * and timers hashTable for debug
     */
    return {
        "set"   : _setInterval,
        "clear" : _clearInterval,
        "timers": htTimers
    };

})({"bCaffeine": false});
