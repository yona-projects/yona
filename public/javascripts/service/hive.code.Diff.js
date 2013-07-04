/**
 * @(#)hive.code.History.js 2013.04.04
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://hive.dev.naver.com/license
 */

(function(ns){

	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){

		var htElement = {};

		/**
		 * initialize
		 */
		function _init(htOptions){
			_initElement(htOptions);
            sDiff = htElement.welDiff.text();
            htElement.welDiff.text("");
            htElement.welDiff.append(_renderDiff(sDiff));
            htElement.welDiff.show();
		}

		/**
		 * initialize element
		 */
		function _initElement(htOptions){
            htElement.welDiff = $('#commit');
		}

        /**
         * welTable에 새 row를 추가한다.
         *
         * @param {Object} welTable
         * @param {String} sClass
         * @param {Number} nLineA
         * @param {Number} nLineB
         * @param {Object|String} vContent
         */
        function _appendLine(welTable, sClass, nLineA, nLineB, vContent) {
            var welTr = $('<tr>').addClass(sClass);

            welTr.append($('<td>').addClass('linenum').text(nLineA));
            welTr.append($('<td>').addClass('linenum').text(nLineB));

            if ((typeof vContent) == 'string') {
                welTr.append($('<td>').append($("<span>").text(vContent)));
            } else {
                welTr.append(vContent);
            }

            welTable.append(welTr);
        }

        /**
         * diff에서 얻은 변경된 라인들을 welTable에 새 row들로 추가한다.
         *
         * 단어 단위 하이라이팅을 적용한다.
         * 삭제된 라인과 추가된 라인이 정확히 1줄씩인 경우임을 가정한다.
         *
         * @param {Object} welTable
         * @param {Object} htDiff
         */
        function _appendChangedLinesWithWordHighlight(welTable, htDiff) {
            var aDiff = JsDiff.diffWords(
                    htDiff.aRemoved[0].substr(1), htDiff.aAdded[0].substr(1));
            var welRemoved = $("<td>");
            var welAdded = $("<td>");

            welRemoved.append($("<span>").text("-"));
            welAdded.append($("<span>").text("+"));

            for (var i = 0; i < aDiff.length; i++) {
                sValue = aDiff[i].value;
                if (aDiff[i].added) {
                    welAdded.append($("<span>").addClass("add").text(sValue));
                } else if (aDiff[i].removed) {
                    welRemoved.append($("<span>").addClass("remove")
                            .text(sValue));
                } else {
                    welAdded.append($("<span>").text(sValue));
                    welRemoved.append($("<span>").text(sValue));
                }
            }

            _appendLine(welTable, "remove", htDiff.nLineA++, "", welRemoved);
            _appendLine(welTable, "add", "", htDiff.nLineB++, welAdded);
        }

        /**
         * diff에서 얻은 변경된 라인들을 welTable에 새 row들로 추가한다.
         *
         * 단어 단위 하이라이팅을 적용하지 않는다.
         *
         * @param {Object} welTable
         * @param {Object} htDiff
         */
        function _appendChangedLinesWithoutWordHighlight(welTable, htDiff) {
            for (var i = 0; i < htDiff.aRemoved.length; i++) {
                _appendLine(welTable, "remove", htDiff.nLineA++, "",
                        htDiff.aRemoved[i]);
            }

            for (var i = 0; i < htDiff.aAdded.length; i++) {
                _appendLine(welTable, "add", "", htDiff.nLineB++,
                        htDiff.aAdded[i]);
            }
        }

        /**
         * diff에서 얻은 변경된 라인들을 welTable에 새 row들로 추가한다.
         *
         * 만약 변경된 라인들이 정확하게 삭제된 라인 1줄, 추가된 라인
         * 1줄이라면 단어 단위 하이라이팅을 적용한다.
         *
         * @param {Object} welTable
         * @param {Object} htDiff
         */
        function _flushChangedLines(welTable, htDiff) {
            if (htDiff.aRemoved.length == 1 && htDiff.aAdded.length == 1) {
                _appendChangedLinesWithWordHighlight(welTable, htDiff);
            } else {
                _appendChangedLinesWithoutWordHighlight(welTable, htDiff);
            }

            htDiff.aRemoved = [];
            htDiff.aAdded = [];
        }

        /**
         * diff에서 얻은 특정 파일의 header를 welTable에 새 row로 추가한다.
         *
         * @param {Object} welTable
         * @param {Object} sHunkHeader
         */
        function _appendFileHeader(welTable, sFileHeader) {
            _appendLine(welTable, "file", "", "", sFileHeader);
        }

        /**
         * diff에서 얻은 특정 hunk의 header를 welTable에 새 row로 추가한다.
         *
         * @param {Object} welTable
         * @param {Object} sHunkHeader
         */
        function _appendHunkHeader(welTable, sHunkHeader) {
            _appendLine(welTable, "range", "...", "...", sHunkHeader);
        }

        /**
         * Diff 렌더링
         *
         * unified diff 형식의 텍스트인 sDiff를 HTML로 렌더링하여 그 결과로
         * 만들어진 HTML 테이블을 반환한다.
         *
         * @param {String} sDiff
         * @return {Object} 렌더링한 결과로 만들어진 HTML 테이블
         */
        function _renderDiff(sDiff) {
            var aLine = sDiff.split('\n');
            var welTable = $('<table>');
            var htDiff = {
                aRemoved: [],
                aAdded: [],
                nLineA: 0,
                nLineB: 0,
            };
            var rxHunkHeader = /@@\s+-(\d+),(\d+)\s+\+(\d+),(\d+)\s+@@/;
            var bAddedOrRemoved;
            var aHunkRange;
            var nLastLineA = 0;
            var nLastLineB = 0;

            bInHunk = false;
            for (var i = 0; i < aLine.length; i++) {
                bAddedOrRemoved = false;

                if (bInHunk) {
                    switch (aLine[i][0]) {
                    case '+':
                        bAddedOrRemoved = true;
                        htDiff.aAdded.push(aLine[i]);
                        break;
                    case '-':
                        bAddedOrRemoved = true;
                        htDiff.aRemoved.push(aLine[i]);
                        break;
                    case ' ':
                        _flushChangedLines(welTable, htDiff);
                        _appendLine(welTable, "", htDiff.nLineA++,
                                htDiff.nLineB++, aLine[i]);
                        break;
                    default:
                        break;
                    }
                } else {
                    switch (aLine[i].substr(0, 2)) {
                    case '++':
                        break;
                    case '--':
                        _flushChangedLines(welTable, htDiff);
                        _appendFileHeader(welTable, aLine[i].substr(5));
                        break;
                    case '@@':
						aMatch = aLine[i].match(rxHunkHeader);
                        aHunkRange = aMatch ? jQuery.map(aMatch, function(sVal) {
							return parseInt(sVal, 10);
						}) : null;
                        if (aHunkRange == null || aHunkRange.length < 4) {
                            if (console instanceof Object) {
                                console.warn("Failed to parse hunk header");
                            }
                        } else {
                            htDiff.nLineA = aHunkRange[1];
                            nLastLineA = htDiff.nLineA + aHunkRange[2] - 1;
                            htDiff.nLineB = aHunkRange[3];
                            nLastLineB = htDiff.nLineB + aHunkRange[4] - 1;
                            _flushChangedLines(welTable, htDiff);
                            _appendHunkHeader(welTable, aLine[i]);
                            bInHunk = true;
                        }
                        break;
                    default:
                        break;
                    }
                }

                if (htDiff.nLineA > nLastLineA || htDiff.nLineB > nLastLineB) {
                    if (console instanceof Object) {
                        console.warn("This hunk has incorrect range.");
                    }
                }

                if (htDiff.nLineA >= nLastLineA
                        && htDiff.nLineB >= nLastLineB) {
                    bInHunk = false;
                }
            }

            return welTable;
        }

		_init(htOptions || {});
	};

})("hive.code.Diff");
