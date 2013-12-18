/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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
 * 기존의 댓글 상자를 코드 댓글 상자로 만들어준다.
 *
 * 코드 주고받기 메뉴의 개요 탭에서도 코드에 댓글을 달 수
 * 있도록 하기 위해 yobi.Code.Diff.js의 일부를 뽑아내어
 * 구현하였다. 그러나 view에 의존성이 매우 크기 때문에
 * yobi.Code.Diff.js와 yobi.git.View.js이외에서 사용하려면 많은
 * 수정이 필요할 것이다.
 */

yobi = yobi || {};

yobi.CodeCommentBox = (function() {
    var htElement = {};

    function _init(htOptions) {
        var welCloseButton = $('.close-comment-box');
        var welOpenButton = $('.open-comment-box');

        var fOnClickAddButton = function(weEvt) {
            _show($(weEvt.target).closest("tr"),
                htOptions.fCallbackAfterShowCommentBox);
            $(weEvt.target).siblings(".close-comment-box").show();
            $(weEvt.target).hide();
        };

        var fOnClickCloseButton = function(weEvt) {
            _hide(htOptions.fCallbackAfterHideCommentBox);
            $(weEvt.target).siblings(".open-comment-box").show();
            $(weEvt.target).hide();
        };

        welCloseButton.click(fOnClickCloseButton).hide();
        welOpenButton.click(fOnClickAddButton);

        var welHidden = $('<input>').attr('type', 'hidden');
        htElement.welDiff = htOptions.welDiff || $('#commit');
        htElement.welEmptyCommentForm = $('#comment-form')
            .append(welHidden.clone().attr('name', 'path'))
            .append(welHidden.clone().attr('name', 'line'))
            .append(welHidden.clone().attr('name', 'side'))
            .append(welHidden.clone().attr('name', 'commitA'))
            .append(welHidden.clone().attr('name', 'commitB'))
            .append(welHidden.clone().attr('name', 'commitId'));
    }

    /**
     * welTr 밑에 댓글 상자를 보여준다.
     *
     * when: 특정 줄의, (댓글 상자가 안 나타난 상태에서의) 댓글 아이콘이나,
     * 댓글창 열기 버튼을 눌렀을 때
     *
     * @param {Object} welTr
     */
    function _show(welTr, fCallback) {
        var welTd = $('<td colspan="3">');
        welTd.addClass('diff-comment-box');
        var welCommentTr;
        var nLine = parseInt(welTr.data('line'));
        var sType = welTr.data('type');
        var sCommitId;
        var sPath;
        var sCommitA = welTr.closest('.diff-container').data('commitA');
        var sCommitB = welTr.closest('.diff-container').data('commitB');

        if (isNaN(nLine)) {
            nLine = parseInt(welTr.prev().data('line'));
            sType = welTr.prev().data('type');
        }

        if (isNaN(nLine)) {
            return;
        }

        if (sType == 'remove') {
            sPath = welTr.closest('table').data('path-a');
            sCommitId = sCommitA;
        } else {
            sPath = welTr.closest('table').data('path-b');
            sCommitId = sCommitB;
        }

        if (htElement.welCommentTr) {
            htElement.welCommentTr.remove();
        }

        htElement.welCommentTr = $("<tr>")
            .append(welTd.append(htElement.welEmptyCommentForm.width(htElement.welDiff.width())));

        welCommentTr = htElement.welCommentTr;
        welCommentTr.find('[name=path]').attr('value', sPath);
        welCommentTr.find('[name=line]').attr('value', nLine);
        sType = (sType == 'remove') ? 'A' : 'B';
        welCommentTr.find('[name=side]').attr('value', sType);

        welCommentTr.find('[name=commitA]').attr('value', sCommitA);
        welCommentTr.find('[name=commitB]').attr('value', sCommitB);

        welTr.after(htElement.welCommentTr);

        if (fCallback !== undefined) {
            fCallback();
        }
    }

    /**
     * 댓글 상자를 숨긴다.
     *
     * when: 특정 줄의, (댓글 상자가 나타난 상태에서의) 댓글 아이콘이나,
     * 댓글창 닫기 버튼을 눌렀을 때
     */
    function _hide(fCallback) {
        htElement.welCommentTr.remove();
        htElement.welEmptyCommentForm.find('[name=path]').removeAttr('value');
        htElement.welEmptyCommentForm.find('[name=line]').removeAttr('value');
        htElement.welEmptyCommentForm.find('[name=side]').removeAttr('value');
        htElement.welEmptyCommentForm.find('[name=commitA]').removeAttr('value');
        htElement.welEmptyCommentForm.find('[name=commitB]').removeAttr('value');
        htElement.welEmptyCommentForm.find('[name=commitId]').removeAttr('value');

        if (fCallback !== undefined) {
            fCallback(htElement.welEmptyCommentForm);
        }
    }

    // public interface
    return {
        "init"  : _init,
        "show" : _show,
        "hide" : _hide
    };
})();
