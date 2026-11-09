/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Changgun Kim
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
/**
 * yona.ui.Calendar
 *
 * 입력란에 붙는 달력 위젯이다. v1.6의 Pikaday가 하던 일을 vanilla-calendar-pro(3.4.0)로 같은 계약 그대로 한다(S-09, A-424).
 *
 * 계약(v1.6과 같다):
 * - `data-toggle="calendar"` 입력란은 문서가 준비되면 자동으로 초기화되고, `data-*` 속성이 곧 옵션이다(`data-silent="true"` 등).
 * - 값 형식은 `YYYY-MM-DD`이다. 날짜를 고르거나 `setDate()`를 부르면 입력란 값이 바뀌고 입력란에 `change`가 나간다.
 * - 입력란에 값이 있고 `silent`가 아니면 처음에 `setDate(값)`을 한 번 부른다(그래서 처음에도 `change`가 한 번 나간다).
 * - 입력란 바로 다음 형제 `.btn-calendar`를 누르면 달력이 열린다.
 * - 입력란을 누르거나 초점을 두면 열리고, 바깥을 누르면 닫힌다. 위치는 입력란의 왼쪽 아래에 틈 없이 붙고, 오른쪽이나 아래가 화면을 넘치면 뒤집힌다.
 * - 입력란에 직접 쓰고 `change`가 나면 그 날짜로 이동하고 값을 `YYYY-MM-DD`로 바로잡는다(이때 `change`가 한 번 더 나간다).
 * - `setDate("")`는 선택만 풀고 입력란 값은 그대로 둔다(Pikaday의 동작을 유지한 것이다).
 *
 * 월 이름과 요일은 Yona의 언어 설정(`yona.language`, 레이아웃의 스크립트 조각이 현재 요청의 언어로 채운다)을 따르고, 설정이 없거나 브라우저가 모르는 언어면
 * 영어이다(v1.6의 Pikaday는 언어 설정이 없어 항상 영어였다). 한 주의 시작 요일도 언어의 관습을 따른다(ko, ja, en은 일요일, ru, uz는 월요일).
 * v1.6은 항상 일요일부터였다.
 *
 * 항상 보이는 달력(마일스톤, API 토큰)은 `yona.ui.Calendar.inline()`을 쓴다.
 *
 * @requires vanilla-calendar-pro (https://github.com/uvarov-frontend/vanilla-calendar-pro), 전역 `VanillaCalendarPro`
 */

(function(ns){
    var oNS = $yona.createNamespace(ns);

    /**
     * 달력의 언어를 정한다. Yona의 언어 설정(`yona.language`, 예: "ko")을 브라우저가 알면 그대로 쓰고, 없거나 모르는 값이면 영어로 둔다.
     * 월 이름과 요일 이름은 브라우저의 `Intl`이 만든다.
     */
    function _locale(){
        var language = window.yona && window.yona.language;
        if (language && typeof Intl !== "undefined") {
            try {
                if (Intl.DateTimeFormat.supportedLocalesOf([language]).length > 0) { return language; }
            } catch (e) { /* 잘못된 언어 태그는 영어로 둔다 */ }
        }
        return "en-US";
    }

    /**
     * 한 주가 시작되는 요일을 돌려준다(0 일요일, 1 월요일). 브라우저의 `Intl.Locale`이 알려 주는 값을 쓰고, 브라우저가 이를 지원하지 않으면
     * Yona가 지원하는 언어(en, ko, ja, ru, uz) 가운데 월요일에 시작하는 ru와 uz만 월요일로 하고 나머지는 일요일로 둔다.
     */
    function _firstWeekday(locale){
        try {
            var info = new Intl.Locale(locale);
            var week = typeof info.getWeekInfo === "function" ? info.getWeekInfo() : info.weekInfo;
            if (week && week.firstDay) { return week.firstDay % 7; }
        } catch (e) { /* Intl.Locale을 모르는 브라우저는 아래 표를 쓴다 */ }
        return /^(ru|uz)(-|$)/.test(locale) ? 1 : 0;
    }

    /** 형식은 `YYYY-MM-DD` 하나만 쓴다(v1.6에서 다른 형식을 준 곳이 없다). */
    function _pad(n){ return (n < 10 ? "0" : "") + n; }

    function _toString(date){
        return date.getFullYear() + "-" + _pad(date.getMonth() + 1) + "-" + _pad(date.getDate());
    }

    /**
     * 글자나 Date를 Date로 바꾼다. 읽을 수 없거나 없는 날짜(2월 31일 등)이면 null이다.
     * 글자는 `2027-2-3`처럼 한 자리 월과 일, `/`나 `.` 구분도 받는다(moment의 느슨한 해석과 같다).
     */
    function _toDate(value){
        if (value instanceof Date) {
            return isNaN(value.getTime()) ? null : new Date(value.getFullYear(), value.getMonth(), value.getDate());
        }
        var m = /^(\d{4})\D(\d{1,2})\D(\d{1,2})$/.exec(String(value == null ? "" : value).trim());
        if (!m) { return null; }
        var date = new Date(+m[1], +m[2] - 1, +m[3]);
        return date.getMonth() === +m[2] - 1 ? date : null;
    }

    /** 최소, 최대 날짜(Date 또는 글자)를 `YYYY-MM-DD`로 돌려준다. 없으면 undefined. */
    function _limit(value){
        var date = _toDate(value);
        return date ? _toString(date) : undefined;
    }

    /** 최소와 최대 사이로 고른다(Pikaday의 setDate와 같다). */
    function _clamp(date, min, max){
        if (min && date < min) { return min; }
        if (max && date > max) { return max; }
        return date;
    }

    /** 입력란에 `change`를 낸다. `firedBy`로 자기가 낸 것임을 표시해 다시 읽지 않게 한다(Pikaday와 같다). */
    function _fireChange(field, firedBy){
        var evt = document.createEvent("HTMLEvents");
        evt.initEvent("change", true, false);
        evt.firedBy = firedBy;
        field.dispatchEvent(evt);
    }

    /**
     * 달력 하나를 만든다. 팝업과 항상 보이는 달력이 함께 쓰는 선택 상태와 날짜 처리이다.
     *
     * @param {HTMLElement} container 달력을 붙일 요소(팝업이면 입력란, 항상 보이는 달력이면 달력을 담을 요소)
     * @param {Object} options `popup`, `min`, `max`, `initial`, `onPick(date)`
     */
    function _create(container, options){
        var selected = null, vc = null;
        var min = _toDate(options.min), max = _toDate(options.max);

        /**
         * 달력 화면과 선택을 맞춘다.
         * 팝업 달력은 처음 열기 전에 `set()`을 부르면 이후 `show()`가 달력을 만들지 못하므로, 아직 그려지지 않았으면 속성만 바꾸고 열 때 그려지게 한다.
         */
        function _show(date){
            var base = date || new Date();
            var dates = date ? [_toString(date)] : [];
            if (vc.context.mainElement && vc.context.mainElement.hasAttribute("data-vc")) {
                vc.set({ selectedDates: dates, selectedMonth: base.getMonth(), selectedYear: base.getFullYear() });
            } else {
                vc.selectedDates = dates;
                vc.selectedMonth = base.getMonth();
                vc.selectedYear = base.getFullYear();
            }
        }

        function _setSelected(date){
            selected = date;
            _show(date);
        }

        var locale = _locale();
        vc = new VanillaCalendarPro.Calendar(container, {
            inputMode: !!options.popup,
            positionToInput: "auto",
            locale: locale,
            firstWeekday: _firstWeekday(locale),
            enableDateToggle: false,
            selectionDatesMode: "single",
            dateMin: _limit(options.min),
            dateMax: _limit(options.max),
            selectedDates: options.initial ? [_toString(options.initial)] : [],
            selectedMonth: (options.initial || new Date()).getMonth(),
            selectedYear: (options.initial || new Date()).getFullYear(),
            onClickDate: function(self){
                if (options.popup) { return; }
                var picked = _toDate(self.context.selectedDates[0]);
                if (picked) { selected = picked; options.onPick(picked); }
            },
            onChangeToInput: function(self){
                var picked = _toDate(self.context.selectedDates[0]);
                if (picked) { selected = picked; options.onPick(picked); }
                self.hide();
            }
        });
        vc.init();
        selected = options.initial || null;

        return {
            vc: vc,
            getDate: function(){ return selected ? new Date(selected.getTime()) : null; },
            /** 읽을 수 있는 날짜면 선택하고 true, 아니면 아무것도 바꾸지 않고 false이다. 빈 값이면 선택만 풀고 false이다. */
            select: function(value){
                if (!value) { _setSelected(null); return false; }
                var date = _toDate(value);
                if (!date) { return false; }
                _setSelected(_clamp(date, min, max));
                return true;
            },
            show: function(){ vc.show(); }
        };
    }

    /**
     * 입력란에 붙는 팝업 달력이다.
     *
     * @param {Element|jQuery} element 입력란
     * @param {Object} [userOptions] `silent`, `minDate`, `maxDate`, `defaultDate`
     * @return {{getDate: Function, setDate: Function}}
     */
    oNS.container[oNS.name] = function(element, userOptions){

        var targetElement, picker, field;

        function _init(element, userOptions){
            targetElement = $(element);
            field = targetElement.get(0);

            var options = Object.assign({
                "defaultDate": targetElement.val(),
                "setDefaultDate": true,
                "format": "YYYY-MM-DD"
            }, userOptions);

            if (!targetElement.data("calendar")){
                picker = _create(field, {
                    popup: true,
                    min: options.minDate,
                    max: options.maxDate,
                    initial: _toDate(options.defaultDate),
                    onPick: _write
                });
                targetElement.data("calendar", picker);

                // 입력란에 직접 쓴 값을 읽는다(Pikaday의 _onInputChange). 자기가 낸 change는 다시 읽지 않는다.
                field.addEventListener("change", function(evt){
                    if (evt.firedBy === picker) { return; }
                    _setDate(field.value);
                });
            } else {
                picker = targetElement.data("calendar");
            }

            targetElement.next(".btn-calendar").on("click", function(){
                targetElement.data("calendar").show();
            });

            if(targetElement.val().length > 0 && options.silent !== true){
                _setDate(targetElement.val());
            }
        }

        /** 입력란 값을 고치고 change를 낸다. */
        function _write(date){
            field.value = _toString(date);
            _fireChange(field, picker);
        }

        function _getDate(){
            return targetElement.data("calendar").getDate();
        }

        function _setDate(dateStr){
            var current = targetElement.data("calendar");
            if (current.select(dateStr)) {
                _write(current.getDate());
            }
        }

        _init(element, userOptions || {});

        return {
            "getDate": _getDate,
            "setDate": _setDate
        };
    };

    /**
     * 항상 보이는 달력이다(마일스톤 작성, API 토큰 만료일). 날짜를 고르면 [input]에 값을 채운다(`change`는 내지 않는다).
     * [input]에 쓴 값은 `blur`에서 읽어 달력에 반영한다.
     *
     * @param {Element|jQuery} container 달력을 담을 요소
     * @param {Object} options `input`(채울 입력란), `minDate`
     * @return {{getDate: Function, setDate: Function}}
     */
    oNS.container[oNS.name].inline = function(container, options){
        var input = $(options.input);
        var picker = _create($(container).get(0), {
            popup: false,
            min: options.minDate,
            max: options.maxDate,
            initial: _toDate(input.val()),
            onPick: function(date){ input.val(_toString(date)); }
        });

        function _setDate(value){
            if (picker.select(value)) { input.val(_toString(picker.getDate())); }
        }

        input.on("blur", function(){ _setDate(this.value); });

        return {
            "getDate": function(){ return picker.getDate(); },
            "setDate": _setDate
        };
    };

})("yona.ui.Calendar");

$(function(){
    $('[data-toggle="calendar"]').each(function(i, el){
        yona.ui.Calendar(el, $(el).data());
    });
});
