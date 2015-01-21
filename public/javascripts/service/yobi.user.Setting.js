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

            htVar.bUseJCrop = yobi.Files.getEnv().bXHR2;

            if(htVar.bUseJCrop){
                htElement.welBtnSubmitCrop.on("click", _onClickBtnSubmitCrop);
                htElement.welAvatarCropImg.on("load", _onAvatarCropImageLoad);
                htElement.welAvatarCropWrap.on("hidden", _clearJcrop);

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

            if(htVar.bUseJCrop){
                _showJcrop(oRes);
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
        function _showJcrop(oRes){
            _clearJcrop();

            htElement.welAvatarCropImg.attr("src", oRes.url);
            htElement.welAvatarCropPreviewImg.attr("src", oRes.url);
            htElement.welAvatarCropWrap.modal("show");
        }

        /**
         * @private
         */
        function _onAvatarCropImageLoad(){
            htElement.welAvatarCropImg.Jcrop({
                "aspectRatio": 1,
                "minSize"  : [32, 32],
                "bgColor"  : "#fff",
                "setSelect": [0, 0, 128, 128],
                "onSelect" : _onAvatarImageCrop,
                "onChange" : _onAvatarImageCrop,
                "onRelease": _onAvatarImageCropCancel
            }, function(){
                htVar.oJcrop = this; // "this" means jCrop object
            });
        }

        /**
         * @private
         */
        function _clearJcrop(){
            if(htVar.oJcrop){
                htVar.oJcrop.destroy();
                htVar.oJcrop = null;
            }

            htElement.welAvatarCropImg.attr("src", "");
            htElement.welAvatarCropImg.css({"width":"auto", "height":"auto"});
            htElement.welAvatarCropPreviewImg.attr("src", "");
        }

        /**
         * @param {Hash Table} htData
         */
        function _onAvatarImageCrop(htData){
            if(htData.w <= 0){
                return;
            }
            var nRx = 128 / htData.w;
            var nRy = 128 / htData.h;

            var nWidth = htElement.welAvatarCropImg.width();
            var nHeight = htElement.welAvatarCropImg.height();

            htElement.welAvatarCropPreviewImg.css({
                "width"     : Math.round(nRx * nWidth) + "px",
                "height"    : Math.round(nRy * nHeight) + "px",
                "marginLeft": "-" + Math.round(nRx * htData.x) + "px",
                "marginTop" : "-" + Math.round(nRy * htData.y) + "px"
            });

            htVar.htLastCrop = htData;
        }

        function _onAvatarImageCropCancel(){
            if(htVar.oJcrop){
                htVar.oJcrop.setSelect([0, 0, 128, 128]);
            } else {
                htVar.htLastCrop = null;
            }
        }

        function _onClickBtnSubmitCrop(){
            var elImage = new Image();

            elImage.onload = function(){
                var htData = htVar.htLastCrop;
                var nWidth = htElement.welAvatarCropImg.width();
                var nRealWidth  = elImage.width;
                var nRw = nRealWidth / nWidth;
                var htCropData = {
                    "x": (htData.x * nRw),
                    "y": (htData.y * nRw),
                    "w": (htData.w * nRw),
                    "h": (htData.h * nRw)
                };

                var oContext = htElement.elAvatarCropCanvas.getContext("2d");
                oContext.drawImage(elImage, htCropData.x, htCropData.y, htCropData.w, htCropData.h, 0, 0, 128, 128);

                // canvas-to-blob.js
                htElement.elAvatarCropCanvas.toBlob(function(oFile){
                    yobi.Files.uploadFile(oFile, "jCropUpload");
                }, "image/jpeg", 100);
            };

            elImage.src = htElement.welAvatarCropImg.attr("src");
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
