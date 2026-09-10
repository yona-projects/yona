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

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

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
            htElement.elAvatarCropCanvas = htElement.welAvatarCropWrap.find("canvas").get(0);
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
            yobi.Files.attach({
                "beforeUpload"  : _onAvatarBeforeUpload,
                "successUpload" : _onAvatarUploaded,
                "errorUpload"   : _onAvatarUploadError,
                "uploadProgress": _onAvatarUploading
            });
            yobi.Files.getUploader(".avatar-frm");

            htVar.nMaxFileSizeInNoCrop = 1024 * 1000 * 1; // 1Mb

            htVar.bUseCropper = yobi.Files.getEnv().bXHR2;

            if(htVar.bUseCropper){
                htElement.welBtnSubmitCrop.on("click", _onClickBtnSubmitCrop);
                htElement.welAvatarCropImg.on("load", _onAvatarCropImageLoad);
                htElement.welAvatarCropWrap.on("hidden", _clearCropper);

                yobi.Files.attach({
                   "successUpload": _onAvatarCroppedImageUploaded,
                   "errorUpload"  : _onAvatarUploadError
                }, "jCropUpload");
            }
        }

        /**
         * @param {Hash Table} htData
         * @param {File} htData.oFile
         * @return {Boolean}
         */
        function _onAvatarBeforeUpload(htData){
            if($yobi.isImageFile(htData.oFile) === false){
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
                yobi.Files.deleteFile({"sURL": oRes.url});
                return false;
            }

            _setAvatarProgressBar(100);

            if(htVar.bUseCropper){
                _showCropper(oRes);
                return;
            }

            if(oRes.size > htVar.nMaxFileSizeInNoCrop){
                _onAvatarUploadError(Messages("user.avatar.fileSizeAlert"));
                yobi.Files.deleteFile({"sURL": oRes.url});
                return false;
            }

            _setAvatarIdOnForm(oRes.id);
            htElement.welFormAvatar.submit();
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
         * @param {Object} oRes
         */
        function _showCropper(oRes){
            _clearCropper();

            // Jcrop 시절부터 있던 기존 버그(P3-46 전환 중 발견, 사용자 확인 후 함께 수정):
            // 크롭 결과를 canvas.toBlob()으로 인코딩할 때 원본 mimeType을 넘기지 않아 항상
            // PNG로 고정 인코딩됐다 — 원본 형식을 기억해뒀다가 그대로 써서 원본 형식을 보존한다.
            htVar.sAvatarMimeType = oRes.mimeType;

            htElement.welAvatarCropImg.attr("src", oRes.url);
            htElement.welAvatarCropPreviewImg.attr("src", oRes.url);
            htElement.welAvatarCropWrap.modal("show");
        }

        /**
         * @private
         */
        function _onAvatarCropImageLoad(){
            htVar.oCropper = new Cropper(htElement.welAvatarCropImg.get(0), {
                "aspectRatio"     : 1,
                "viewMode"        : 1, // Jcrop처럼 크롭박스가 이미지 영역을 벗어나지 않도록 제한
                "minCropBoxWidth" : 32,
                "minCropBoxHeight": 32,
                "autoCropArea"    : 1,
                "crop"            : _onAvatarImageCrop,
                "ready"           : _onAvatarCropReady
            });
        }

        /**
         * Jcrop의 setSelect:[0,0,128,128](기본 선택영역)에 대응.
         * Cropper.js는 초기화 시점에 setCropBoxData를 바로 적용할 수 없어 ready 콜백에서 설정한다.
         *
         * @private
         */
        function _onAvatarCropReady(){
            htVar.oCropper.setCropBoxData({"left": 0, "top": 0, "width": 128, "height": 128});
            // setCropBoxData는 crop 이벤트를 발생시키지 않으므로 미리보기를 직접 한 번 갱신한다.
            _onAvatarImageCrop({"detail": htVar.oCropper.getData()});
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
         * Cropper.js의 "crop" 이벤트 핸들러. Jcrop의 onSelect/onChange(둘 다 동일 콜백을 썼음)에 대응.
         *
         * 주의: Cropper.js의 crop 이벤트 detail(x,y,width,height)은 Jcrop의 onSelect/onChange가
         * 주던 "화면에 표시된 이미지" 기준 좌표와 달리 "원본 이미지" 기준 좌표다. 그래서 비율
         * 계산의 분모도 화면 표시 크기(welAvatarCropImg.width()) 대신 원본 크기(naturalWidth)를
         * 써야 동일한 실시간 미리보기 결과가 나온다.
         *
         * @param {jQuery.Event|Object} weEvt weEvt.detail = {x, y, width, height, ...}
         */
        function _onAvatarImageCrop(weEvt){
            var htData = weEvt.detail || weEvt;

            if(htData.width <= 0){
                return;
            }

            var elImage   = htElement.welAvatarCropImg.get(0);
            var nRx = 128 / htData.width;
            var nRy = 128 / htData.height;

            htElement.welAvatarCropPreviewImg.css({
                "width"     : Math.round(nRx * elImage.naturalWidth) + "px",
                "height"    : Math.round(nRy * elImage.naturalHeight) + "px",
                "marginLeft": "-" + Math.round(nRx * htData.x) + "px",
                "marginTop" : "-" + Math.round(nRy * htData.y) + "px"
            });
        }

        function _onClickBtnSubmitCrop(){
            var oCroppedCanvas = htVar.oCropper.getCroppedCanvas({"width": 128, "height": 128});

            // canvas-to-blob.js
            oCroppedCanvas.toBlob(function(oFile){
                yobi.Files.uploadFile(oFile, "jCropUpload");
            }, htVar.sAvatarMimeType);
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

            $yobi.alert(sMessage);
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
            welInput.popover({"trigger": "manual", "placement": "right"});

            var oPopover = welInput.data('popover');
            oPopover.options.placement = 'right';
            oPopover.options.trigger   = 'manual';
            oPopover.options.content   = sMessage;

            welInput.popover('show');
        }

        function _clearPopovers(){
            try {
                htElement.welFormPswd.find("input").each(function(i, v){
                    $(v).popover("destroy");
                });
            } catch(e){} // to avoid bootstrap bug
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
                    $yobi.alert(Messages("error.failedTo",
                        Messages("userinfo.changeNotifications"),
                        oRes.status, oRes.statusText));
                }
            })
        }

        function _showNotificationTab(){
            $('#notification-projects a[href="' + location.hash + '"]').tab("show");
        }

        _init(htOptions || {});
    };
})("yobi.user.Setting");
