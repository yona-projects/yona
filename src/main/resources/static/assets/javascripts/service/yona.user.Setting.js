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
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /** 자르기 모달의 이미지 최대 폭(px). 템플릿의 `max-width`와 같다. */
        var AVATAR_CROP_MAX_WIDTH = 500;
        /** 자른 아바타의 한 변(px). 미리보기 틀과 업로드하는 이미지의 크기이다. */
        var AVATAR_PREVIEW_SIZE = 128;
        /** 선택 상자의 최소 한 변(px, 이미지 표시 크기 기준). */
        var AVATAR_CROP_MIN_SIZE = 32;
        /** Cropper.js 2의 자르기 상자 구성. 비율 1:1, 이미지는 고정(옮기기, 확대 없음), 바깥은 흰 막으로 덮는다. */
        var AVATAR_CROP_TEMPLATE = '<cropper-canvas background>'
            + '<cropper-image></cropper-image>'
            + '<cropper-shade theme-color="rgba(255, 255, 255, 0.6)"></cropper-shade>'
            + '<cropper-handle action="select" plain></cropper-handle>'
            + '<cropper-selection aspect-ratio="1" movable resizable outlined>'
            + '<cropper-grid role="grid" bordered covered></cropper-grid>'
            + '<cropper-handle action="move" theme-color="rgba(255, 255, 255, 0.35)"></cropper-handle>'
            + '<cropper-handle action="n-resize"></cropper-handle>'
            + '<cropper-handle action="e-resize"></cropper-handle>'
            + '<cropper-handle action="s-resize"></cropper-handle>'
            + '<cropper-handle action="w-resize"></cropper-handle>'
            + '<cropper-handle action="ne-resize"></cropper-handle>'
            + '<cropper-handle action="nw-resize"></cropper-handle>'
            + '<cropper-handle action="se-resize"></cropper-handle>'
            + '<cropper-handle action="sw-resize"></cropper-handle>'
            + '</cropper-selection>'
            + '</cropper-canvas>';

        /**
         * initialize
         *
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initElement();
            _attachEvent();

            _initFormValidator();
            _initAvatarUploader();
            _showNotificationTab();
        }

        /**
         * initialize elements
         */
        function _initElement(){
            htElement.welFormBasic = $("#frmBasic");

            htElement.welFormAvatar = $("#frmAvatar");
            htElement.welBtnUploadAvatar = htElement.welFormAvatar.find(".btnUploadAvatar");
            htElement.welAvatarWrap = htElement.welFormAvatar.find(".avatar-wrap");
            htElement.welAvatarImage = htElement.welAvatarWrap.find("img");
            htElement.welAvatarProgress = htElement.welFormAvatar.find(".upload-progress");
            htElement.welAvatarProgressBar = htElement.welAvatarProgress.find(".bar");

            htElement.welAvatarCropWrap = $("#avatarCropWrap");
            htElement.welAvatarCropImg = htElement.welAvatarCropWrap.find(".modal-body > img");
            htElement.welAvatarCropPreviewImg = htElement.welAvatarCropWrap.find(".avatar-wrap > img");
            htElement.welBtnSubmitCrop = htElement.welAvatarCropWrap.find("button.btnSubmitCrop");

            htElement.welFormPswd = $("#frmPassword");
            htElement.welInputOldPassword  = $('#oldPassword');
            htElement.welInputPassword  = $('#password');
            htElement.welInputRetypedPassword = $('#retypedPassword');

            htElement.welChkNotiSwtich = $(".notiUpdate");
        }

        /**
         * attach event
         */
        function _attachEvent(){
            htElement.welInputOldPassword.focusout(_onBlurInputPassword);
            htElement.welInputPassword.focusout(_onBlurInputPassword);
            htElement.welInputRetypedPassword.focusout(_onBlurInputPassword);

            htElement.welChkNotiSwtich.change(_onChangeNotiSwitch);
        }

        /**
         * @private
         */
        function _initAvatarUploader(){
            // 아바타 기본 업로더 설정
            yona.Files.attach({
                "beforeUpload"  : _onAvatarBeforeUpload,
                "successUpload" : _onAvatarUploaded,
                "errorUpload"   : _onAvatarUploadError,
                "uploadProgress": _onAvatarUploading
            });
            yona.Files.getUploader(".avatar-frm");

            htElement.welBtnSubmitCrop.on("click", _onClickBtnSubmitCrop);
            htElement.welAvatarCropImg.on("load", _onAvatarCropImageLoad);
            htElement.welAvatarCropWrap.on("hidden.bs.modal", _clearCropper);

            yona.Files.attach({
               "successUpload": _onAvatarCroppedImageUploaded,
               "errorUpload"  : _onAvatarUploadError
            }, "avatarCropUpload");
        }

        /**
         * @param {Hash Table} htData
         * @param {File} htData.oFile
         * @return {Boolean}
         */
        function _onAvatarBeforeUpload(htData){
            if($yona.isImageFile(htData.oFile) === false){
                _onAvatarUploadError(Messages("user.avatar.onlyImage"));
                return false;
            }
        }

        /**
         * @param {Hash Table} htData
         */
        function _onAvatarUploaded(htData){
            var oRes = htData.oRes;

            if(oRes.mimeType.indexOf("image/") !== 0){
                _onAvatarUploadError(Messages("user.avatar.onlyImage"));
                yona.Files.deleteFile({"sURL": oRes.url});
                return false;
            }

            _setAvatarProgressBar(100);

            _showCropper(oRes);
        }

        /**
         * @param nAvatarId
         * @private
         */
        function _setAvatarIdOnForm(nAvatarId){
            var welAvatarId = htElement.welFormAvatar.find("input[name=avatarId]");

            if(welAvatarId.length === 0){
                welAvatarId = $('<input type="hidden" name="avatarId">');
                htElement.welFormAvatar.append(welAvatarId);
            }

            welAvatarId.val(nAvatarId);
        }

        /**
         * 올린 이미지를 자르기 모달에 띄운다. 자르기 상자는 이미지가 불러와지면(`load`) 만든다.
         *
         * @param {Object} oRes
         */
        function _showCropper(oRes){
            _clearCropper();

            htElement.welAvatarCropImg.attr("src", oRes.url);
            htElement.welAvatarCropPreviewImg.attr("src", oRes.url);
            bootstrap.Modal.getOrCreateInstance(htElement.welAvatarCropWrap[0]).show();
        }

        /**
         * 자르기 상자(Cropper.js 2)를 만든다(`decisions.md` D-101). 동작은 Jcrop 때와 같다: 비율 1:1, 최소 32px, 처음 선택은 왼쪽 위 128x128,
         * 선택이 바뀌면 미리보기를 갱신한다.
         *
         * Cropper.js의 선택 좌표는 캔버스 기준이다. 캔버스를 이미지의 표시 크기(원본 크기, 최대 500px 폭)와 똑같이 맞추면 좌표가
         * 이미지 위의 좌표가 되어 Jcrop 때의 미리보기 계산을 그대로 쓸 수 있다.
         *
         * @private
         */
        function _onAvatarCropImageLoad(){
            var elImage = htElement.welAvatarCropImg.get(0);

            if(!elImage.getAttribute("src") || !elImage.naturalWidth){
                return;
            }

            var nScale = Math.min(1, AVATAR_CROP_MAX_WIDTH / elImage.naturalWidth);
            htVar.nCropWidth = Math.round(elImage.naturalWidth * nScale);
            htVar.nCropHeight = Math.round(elImage.naturalHeight * nScale);

            var oCropper = new Cropper.Cropper(elImage, {
                "container": elImage.parentNode,
                "template" : AVATAR_CROP_TEMPLATE
            });
            htVar.oCropper = oCropper;

            var elCanvas = oCropper.getCropperCanvas();
            elCanvas.style.width = htVar.nCropWidth + "px";
            elCanvas.style.height = htVar.nCropHeight + "px";

            var elSelection = oCropper.getCropperSelection();
            elSelection.addEventListener("change", _onAvatarCropSelectionChange);

            oCropper.getCropperImage().$ready().then(function(){
                if(htVar.oCropper !== oCropper){
                    return; // 준비되는 사이에 모달이 닫혔다
                }

                var nSize = Math.min(AVATAR_PREVIEW_SIZE, htVar.nCropWidth, htVar.nCropHeight);
                elSelection.$change(0, 0, nSize, nSize);
            });
        }

        /**
         * 선택 상자가 바뀌려 할 때이다(바뀌기 전, 취소할 수 있다). 최소 크기보다 작아지면 막고, 아니면 미리보기를 갱신한다.
         *
         * @param {CustomEvent} weEvt `detail`에 바뀔 `x`, `y`, `width`, `height`가 있다
         * @private
         */
        function _onAvatarCropSelectionChange(weEvt){
            var htData = weEvt.detail;

            if(htData.width < AVATAR_CROP_MIN_SIZE || htData.height < AVATAR_CROP_MIN_SIZE){
                weEvt.preventDefault();
                return;
            }

            _onAvatarImageCrop({"x": htData.x, "y": htData.y, "w": htData.width, "h": htData.height});
        }

        /**
         * @private
         */
        function _clearCropper(){
            if(htVar.oCropper){
                htVar.oCropper.destroy();
                htVar.oCropper = null;
            }

            htElement.welAvatarCropImg.attr("src", "");
            htElement.welAvatarCropImg.css({"width":"auto", "height":"auto"});
            htElement.welAvatarCropPreviewImg.attr("src", "");
        }

        /**
         * 미리보기(128x128 틀 안의 이미지)를 선택 부분에 맞춘다.
         *
         * @param {Hash Table} htData 이미지 표시 크기 기준의 `x`, `y`, `w`, `h`
         */
        function _onAvatarImageCrop(htData){
            if(htData.w <= 0){
                return;
            }
            var nRx = AVATAR_PREVIEW_SIZE / htData.w;
            var nRy = AVATAR_PREVIEW_SIZE / htData.h;

            htElement.welAvatarCropPreviewImg.css({
                "width"     : Math.round(nRx * htVar.nCropWidth) + "px",
                "height"    : Math.round(nRy * htVar.nCropHeight) + "px",
                "marginLeft": "-" + Math.round(nRx * htData.x) + "px",
                "marginTop" : "-" + Math.round(nRy * htData.y) + "px"
            });
        }

        /**
         * 선택 부분을 원본 화소로 잘라 128x128 PNG로 올린다(Cropper.js `$toCanvas`).
         */
        function _onClickBtnSubmitCrop(){
            if(!htVar.oCropper){
                return;
            }

            htVar.oCropper.getCropperSelection().$toCanvas({"width": AVATAR_PREVIEW_SIZE, "height": AVATAR_PREVIEW_SIZE}).then(function(elCanvas){
                elCanvas.toBlob(function(oFile){
                    yona.Files.uploadFile(oFile, "avatarCropUpload");
                });
            });
        }

        /**
         * @param {Hash Table} htData
         */
        function _onAvatarCroppedImageUploaded(htData){
            // 설정 폼에 avatarId 설정하고 submit
            _setAvatarIdOnForm(htData.oRes.id);
            htElement.welFormAvatar.submit();
        }

        function _onAvatarUploadError(vParam){
            var sMessage = (vParam.oRes) ? Messages("user.avatar.uploadError") +
                "<br>(" + vParam.oRes.status + " " + vParam.oRes.statusText + ")" : vParam;

            $yona.alert(sMessage);
            _setAvatarProgressBar(0);
        }

        /**
         * @param {Wrapped Event} weEvt
         * @param {Number} nPosition
         * @param {Number}
         */
        function _onAvatarUploading(weEvt, nPosition, nTotal, nPercent){
            _setAvatarProgressBar(nPercent);
            htElement.welAvatarProgress.css("opacity", 1);
        }

        /**
         * Set avatar image upload progress bar with given percent.
         *
         * @param {Number} nPercent
         */
        function _setAvatarProgressBar(nPercent){
            nPercent = parseInt(nPercent, 10);

            if(nPercent > 0){
                htElement.welAvatarProgress.show();
            } else {
                htElement.welAvatarProgress.hide();
            }

            htElement.welAvatarProgressBar.css("width", nPercent + "%");

            // Hide progress bar 1s after full
            if(nPercent >= 100){
                setTimeout(function(){
                    _setAvatarProgressBar(0);
                }, 1000);
            }
        }

        function _onBlurInputPassword(){
            htVar.oValidator._validateForm();
        }

        /**
         * initialize FormValidator
         * @require validate.js
         */
        function _initFormValidator(){
            var aRules = [
                {"name": 'oldPassword',     "rules": 'required'},
                {"name": 'password',        "rules": 'required|min_length[4]'},
                {"name": 'retypedPassword', "rules": 'required|matches[password]'}
            ];

            htVar.oValidator = new FormValidator('frmPassword', aRules, _onFormValidate);

            htVar.oValidator.setMessage('required',   Messages("validation.required"));
            htVar.oValidator.setMessage('min_length', Messages("validation.tooShortPassword"));
            htVar.oValidator.setMessage('matches',    Messages("validation.passwordMismatch"));
        }
        /**
         * on validate form
         *
         * @param {Array} aErrors
         */
        function _onFormValidate(aErrors){
            _clearPopovers();

            // to avoid bootstrap bug
            if (aErrors.length <= 0) {
                return _clearPopovers();
            }

            var welTarget;
            aErrors.forEach(function(htError){
                welTarget = htElement.welFormPswd.find("input[name=" + htError.name + "]");

                if(welTarget){
                    _showPopover(welTarget, htError.message);
                }
            });
        }

        /**
         * Bootstrap toolTip function has some limitation.
         * In this case, toolTip doesn't provide easy way to change title and contents.
         * So, unfortunately I had to change data value in directly.
         *
         * @param {Wrapped Element} welInput
         * @param {String} sMessage
         */
        function _showPopover(welInput, sMessage){
            $yona.showPopover(welInput, {"placement": "right", "content": sMessage});
        }

        function _clearPopovers(){
            $yona.hidePopover(htElement.welFormPswd.find("input"));
        }

        function _onChangeNotiSwitch(){
            var welTarget  = $(this);
            var bChecked   = welTarget.prop("checked");
            var url        = $(this).attr("data-href");

            $.ajax(url, {
                "method" : "post",
                "success": function(data){
                    welTarget.prop("checked", bChecked);
                },
                "error"  : function(oRes){
                    welTarget.prop("checked", !bChecked);
                    $yona.alert(Messages("error.failedTo",
                        Messages("userinfo.changeNotifications"),
                        oRes.status, oRes.statusText));
                }
            })
        }

        function _showNotificationTab(){
            $yona.showTab($('#notification-projects a[href="' + location.hash + '"]'));
        }

        _init(htOptions || {});
    };
})("yona.user.Setting");
