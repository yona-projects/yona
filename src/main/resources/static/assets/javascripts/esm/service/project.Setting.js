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

import { register } from "../namespace.js";

const Setting = function(htOptions){

    const htVar = {};
    const htElement = {};

    /**
     * initialize
     */
    function _init(htOptions){
        const htOpt = htOptions || {};
        _initVar(htOpt);
        _initElement(htOpt);
        _attachEvent();
    }

    /**
     * initialize variables
     */
    function _initVar(htOptions){
        htVar.rxPrjName = /^[0-9A-Za-z-_\.가-힣]+$/;
        htVar.aReservedWords = [".", "..", ".git"];
    }

    /**
     * initialize element variables
     */
    function _initElement(htOptions){
        // 프로젝트 설정 관련
        htElement.welForm = $("form#saveSetting");
        htElement.welInputLogo = $("#logoPath");
        htElement.welInputName = $("input#project-name");
        htElement.welBtnSave   = $("#save");
        htElement.welMenuSettingCode = $("#menuSettingCode");
        htElement.welMenuSettingPullRequest = $("#menuSettingPullRequest");
        htElement.welMenuSettingReview = $("#menuSettingReview");
        htElement.welReviewerCountSettingPanel = $("#reviewerCountSettingPanel");
        htElement.welDefaultBranceSettingPanel = $("#defaultBranceSettingPanel");
        htElement.welSubMenuProjectChangeVCS = $("#subMenuProjectChangeVCS");
    }

    /**
     * attach event handlers
     */
    function _attachEvent(){
        htElement.welInputLogo.change(_onChangeLogoPath);
        
        htElement.welBtnSave.click(_onClickBtnSave);
        
        htElement.welMenuSettingCode.on('click', _onClickMenuSettingCode);
        htElement.welMenuSettingPullRequest.on('click', _onClickMenuSettingPullRequest);
        htElement.welMenuSettingReview.on('click', _onClickMenuSettingReview);

    }

    function _onChangeLogoPath(){
        const welTarget = $(this);

        if($yona.isImageFile(welTarget) === false){
            $yona.showAlert(Messages("project.logo.alert"));
            welTarget.val('');
            return;
        }

        htElement.welForm.submit();
    }

    function _onClickBtnSave(){
        const sPrjName = htElement.welInputName.val();
        if(!htVar.rxPrjName.test(sPrjName)){
            $yona.showAlert(Messages("project.name.alert"));
            return false;
        }
        if(htVar.aReservedWords.indexOf(sPrjName) >= 0){
            $yona.showAlert(Messages("project.name.reserved.alert"));
            return false;
        }

        return true;
    }

    /**
     * 코드 메뉴를 바꿀 때 연동 항목을 맞춘다.
     * 끄면 풀 리퀘스트와 리뷰 선택을 기억해 두고 함께 끈 뒤 관련 설정 패널을 숨긴다.
     * 다시 켜면 기억해 둔 선택과 패널을 되돌려, 껐다 켜기만 했는데 두 메뉴가 꺼진 채 저장되지 않게 한다
     * (v1.6은 끌 때만 처리해 껐다 켜면 풀 리퀘스트와 리뷰가 꺼진 채 남았다).
     */
    function _onClickMenuSettingCode() {
        const isChecked = $(this).prop("checked");

        if (!isChecked) {
            htVar.bRememberedPullRequest = htElement.welMenuSettingPullRequest.prop("checked");
            htVar.bRememberedReview = htElement.welMenuSettingReview.prop("checked");

            htElement.welMenuSettingCode.prop("checked", false);
            htElement.welMenuSettingPullRequest.prop("checked", false);
            htElement.welMenuSettingReview.prop("checked", false);

            htElement.welReviewerCountSettingPanel.hide();
            htElement.welDefaultBranceSettingPanel.hide();
            htElement.welSubMenuProjectChangeVCS.hide();
        } else {
            htElement.welMenuSettingPullRequest.prop("checked", htVar.bRememberedPullRequest === true);
            htElement.welMenuSettingReview.prop("checked", htVar.bRememberedReview === true);

            htElement.welReviewerCountSettingPanel.show();
            htElement.welDefaultBranceSettingPanel.show();
            htElement.welSubMenuProjectChangeVCS.show();
        }
    }

    function _onClickMenuSettingPullRequest() {
        const isChecked = $(this).prop("checked");
        
        if(isChecked) {
            htElement.welMenuSettingCode.prop("checked", true);
            htElement.welReviewerCountSettingPanel.show();    
        } else {
            htElement.welReviewerCountSettingPanel.hide();    
        }   
    }

    function _onClickMenuSettingReview() {
        const isChecked = $(this).prop("checked");
        
        if(isChecked) {
            htElement.welMenuSettingCode.prop("checked", true);
        }     
    }

    _init(htOptions);
};

export default Setting;

register("yona.project.Setting", Setting);
