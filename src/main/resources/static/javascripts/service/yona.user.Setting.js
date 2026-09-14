/**
 * Yona, Project Hosting SW
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
         *
         * 이 모듈은 user/edit.html, user/edit_password.html, user/edit_notifications.html,
         * user/edit_tokens*.html, user/edit_token.html, user/edit_emails.html 등 여러
         * 화면에서 공통으로 $yona.loadModule("user.Setting")로 로드된다 - 화면마다 아래
         * 요소 중 일부만 실제로 존재한다(예: #avatarCropWrap은 user/edit.html 프로필 탭에만
         * 있음). 원본 jQuery 셀렉터는 매치가 없어도 빈 컬렉션(길이 0)을 반환해 이후
         * .find()/.on() 호출이 조용히 no-op이 됐으므로, 네이티브 전환에서는 존재하지
         * 않는 화면에서 null이 되도록 하고 사용처마다 null 가드를 둬 동일하게 no-op을
         * 재현한다.
         */
        function _initElement(){
            htElement.welFormBasic = document.getElementById("frmBasic");

            htElement.welFormAvatar = document.getElementById("frmAvatar");
            htElement.welBtnUploadAvatar = htElement.welFormAvatar ? htElement.welFormAvatar.querySelector(".btnUploadAvatar") : null;
            htElement.welAvatarWrap = htElement.welFormAvatar ? htElement.welFormAvatar.querySelector(".avatar-wrap") : null;
            htElement.welAvatarImage = htElement.welAvatarWrap ? htElement.welAvatarWrap.querySelector("img") : null;
            htElement.welAvatarProgress = htElement.welFormAvatar ? htElement.welFormAvatar.querySelector(".upload-progress") : null;
            htElement.welAvatarProgressBar = htElement.welAvatarProgress ? htElement.welAvatarProgress.querySelector(".bar") : null;

            htElement.welAvatarCropWrap = document.getElementById("avatarCropWrap");
            htElement.welAvatarCropImg = htElement.welAvatarCropWrap ? htElement.welAvatarCropWrap.querySelector(".modal-body > img") : null;
            htElement.welAvatarCropPreviewImg = htElement.welAvatarCropWrap ? htElement.welAvatarCropWrap.querySelector(".avatar-wrap > img") : null;
            htElement.elAvatarCropCanvas = htElement.welAvatarCropWrap ? htElement.welAvatarCropWrap.querySelector("canvas") : null;
            htElement.welBtnSubmitCrop = htElement.welAvatarCropWrap ? htElement.welAvatarCropWrap.querySelector("button.btnSubmitCrop") : null;

            htElement.welFormPswd = document.getElementById("frmPassword");
            htElement.welInputOldPassword  = document.getElementById('oldPassword');
            htElement.welInputPassword  = document.getElementById('password');
            htElement.welInputRetypedPassword = document.getElementById('retypedPassword');

            htElement.welChkNotiSwtich = document.querySelectorAll(".notiUpdate");
        }

        /**
         * attach event
         */
        function _attachEvent(){
            if(htElement.welInputOldPassword){
                htElement.welInputOldPassword.addEventListener("focusout", _onBlurInputPassword);
            }
            if(htElement.welInputPassword){
                htElement.welInputPassword.addEventListener("focusout", _onBlurInputPassword);
            }
            if(htElement.welInputRetypedPassword){
                htElement.welInputRetypedPassword.addEventListener("focusout", _onBlurInputPassword);
            }

            htElement.welChkNotiSwtich.forEach(function(el){
                el.addEventListener("change", _onChangeNotiSwitch);
            });
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

            htVar.nMaxFileSizeInNoCrop = 1024 * 1000 * 1; // 1Mb

            htVar.bUseCropper = yona.Files.getEnv().bXHR2;

            if(htVar.bUseCropper && htElement.welAvatarCropWrap){
                htElement.welBtnSubmitCrop.addEventListener("click", _onClickBtnSubmitCrop);
                htElement.welAvatarCropImg.addEventListener("load", _onAvatarCropImageLoad);
                // #avatarCropWrap은 user/edit.html(프로필 탭)에만 있다 - user.Setting.js가
                // 로드되는 다른 탭(예: 비밀번호 변경)에는 없어 welAvatarCropWrap이 null이 된다
                // (원본 jQuery는 빈 컬렉션에 .on()을 걸어도 조용히 no-op이었다 - 위 null 체크로
                // 동일하게 재현).
                // #avatarCropWrap은 네이티브 <dialog>로 바뀌었으니(data-backdrop="static"이라
                // 배경 클릭으로는 안 닫힘) Bootstrap의 "hidden" 대신 네이티브 "close" 이벤트를 쓴다.
                htElement.welAvatarCropWrap.addEventListener("close", _clearCropper);
                $yona.attachDialogDismiss(htElement.welAvatarCropWrap, true);

                yona.Files.attach({
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

            if(htVar.bUseCropper){
                _showCropper(oRes);
                return;
            }

            if(oRes.size > htVar.nMaxFileSizeInNoCrop){
                _onAvatarUploadError(Messages("user.avatar.fileSizeAlert"));
                yona.Files.deleteFile({"sURL": oRes.url});
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
            var welAvatarId = htElement.welFormAvatar.querySelector("input[name=avatarId]");

            if(!welAvatarId){
                welAvatarId = document.createElement("input");
                welAvatarId.type = "hidden";
                welAvatarId.name = "avatarId";
                htElement.welFormAvatar.appendChild(welAvatarId);
            }

            welAvatarId.value = nAvatarId;
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

            htElement.welAvatarCropImg.setAttribute("src", oRes.url);
            htElement.welAvatarCropPreviewImg.setAttribute("src", oRes.url);
            htElement.welAvatarCropWrap.showModal();
        }

        /**
         * @private
         */
        function _onAvatarCropImageLoad(){
            htVar.oCropper = new Cropper(htElement.welAvatarCropImg, {
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

            htElement.welAvatarCropImg.setAttribute("src", "");
            htElement.welAvatarCropImg.style.width = "auto";
            htElement.welAvatarCropImg.style.height = "auto";
            htElement.welAvatarCropPreviewImg.setAttribute("src", "");
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

            var elImage   = htElement.welAvatarCropImg;
            var nRx = 128 / htData.width;
            var nRy = 128 / htData.height;

            htElement.welAvatarCropPreviewImg.style.width = Math.round(nRx * elImage.naturalWidth) + "px";
            htElement.welAvatarCropPreviewImg.style.height = Math.round(nRy * elImage.naturalHeight) + "px";
            htElement.welAvatarCropPreviewImg.style.marginLeft = "-" + Math.round(nRx * htData.x) + "px";
            htElement.welAvatarCropPreviewImg.style.marginTop = "-" + Math.round(nRy * htData.y) + "px";
        }

        function _onClickBtnSubmitCrop(){
            var oCroppedCanvas = htVar.oCropper.getCroppedCanvas({"width": 128, "height": 128});

            // canvas-to-blob.js
            oCroppedCanvas.toBlob(function(oFile){
                yona.Files.uploadFile(oFile, "jCropUpload");
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
            htElement.welAvatarProgress.style.opacity = 1;
        }

        /**
         * Set avatar image upload progress bar with given percent.
         *
         * @param {Number} nPercent
         */
        function _setAvatarProgressBar(nPercent){
            nPercent = parseInt(nPercent, 10);

            if(nPercent > 0){
                htElement.welAvatarProgress.style.display = "block";
            } else {
                htElement.welAvatarProgress.style.display = "none";
            }

            htElement.welAvatarProgressBar.style.width = nPercent + "%";

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

            if(!htElement.welFormPswd){
                return;
            }

            var welTarget;
            aErrors.forEach(function(htError){
                welTarget = htElement.welFormPswd.querySelector("input[name=" + htError.name + "]");

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
            $yona.showPopoverError(welInput, sMessage, "right");
        }

        function _clearPopovers(){
            if(!htElement.welFormPswd){
                return;
            }
            htElement.welFormPswd.querySelectorAll("input").forEach(function(v){
                $yona.hidePopoverError(v);
            });
        }

        function _onChangeNotiSwitch(){
            var welTarget  = this;
            var bChecked   = welTarget.checked;
            var url        = welTarget.getAttribute("data-href");

            fetch(url, {"method": "post"})
                .then(function(response){
                    if(!response.ok){
                        return Promise.reject(response);
                    }
                    welTarget.checked = bChecked;
                })
                .catch(function(oRes){
                    welTarget.checked = !bChecked;
                    $yona.alert(Messages("error.failedTo",
                        Messages("userinfo.changeNotifications"),
                        oRes.status, oRes.statusText));
                });
        }

        function _showNotificationTab(){
            var elLink = document.querySelector('#notification-projects a[href="' + location.hash + '"]');
            if(elLink){
                $yona.tabShow(elLink);
            }
        }

        _init(htOptions || {});
    };
})("yona.user.Setting");
