/**
 * @(#)yobi.user.Setting.js 2013.05.16
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
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
        }

        /**
         * 엘리먼트 변수 초기화
         * initialize elements
         */
        function _initElement(){
            // 기본
            htElement.welFormBasic = $("#frmBasic");

            // 아바타
            htElement.welFormAvatar = $("#frmAvatar");
            htElement.welBtnUploadAvatar = htElement.welFormAvatar.find(".btnUploadAvatar");
            htElement.welAvatarWrap = htElement.welFormAvatar.find(".avatar-wrap");
            htElement.welAvatarImage = htElement.welAvatarWrap.find("img");
            htElement.welAvatarProgress = htElement.welAvatarWrap.find(".progress");
            htVar.nProgressHeight = htElement.welAvatarWrap.height();

            htElement.welAvatarCropWrap = $("#avatarCropWrap");
            htElement.welAvatarCropImg = htElement.welAvatarCropWrap.find(".modal-body > img");
            htElement.welAvatarCropPreviewImg = htElement.welAvatarCropWrap.find(".avatar-wrap > img");
            htElement.elAvatarCropCanvas = htElement.welAvatarCropWrap.find("canvas").get(0);
            htElement.welBtnSubmitCrop = htElement.welAvatarCropWrap.find("button.btnSubmitCrop");

            // 비밀번호 변경
            htElement.welFormPswd = $("#frmPassword");
            htElement.welInputOldPassword  = $('#oldPassword');
            htElement.welInputPassword  = $('#password');
            htElement.welInputRetypedPassword = $('#retypedPassword');

            // 알림 설정
            htElement.welChkNotiSwtich = $(".notiUpdate");
        }

        /**
         * 이벤트 초기화
         * attach event
         */
        function _attachEvent(){
            // 비밀번호 설정
            htElement.welInputOldPassword.focusout(_onBlurInputPassword);
            htElement.welInputPassword.focusout(_onBlurInputPassword);
            htElement.welInputRetypedPassword.focusout(_onBlurInputPassword);

            // 알림 설정 변경
            htElement.welChkNotiSwtich.change(_onChangeNotiSwitch);
        }

        /**
         * 아바타 업로더 설정
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

            // jCrop 을 사용할 수 없는 환경을 위한 최대 파일 크기 제한
            htVar.nMaxFileSizeInNoCrop = 1024 * 1000 * 1; // 1Mb

            // XHR2 를 사용가능한 환경에서만 jCrop 기능을 제공한다
            htVar.bUseJCrop = yobi.Files.getEnv().bXHR2;

            if(htVar.bUseJCrop){
                htElement.welBtnSubmitCrop.on("click", _onClickBtnSubmitCrop);
                htElement.welAvatarCropImg.on("load", _onAvatarCropImageLoad);
                htElement.welAvatarCropWrap.on("hidden", _clearJcrop);

                // jCrop 결과물 업로드 이벤트 설정
                yobi.Files.attach({
                   "successUpload": _onAvatarCroppedImageUploaded,
                   "errorUpload"  : _onAvatarUploadError
                }, "jCropUpload");
            }
        }

        /**
         * 아바타 이미지 업로드 전 타입/확장자 검사
         * false 를 반환하면 업로드 하지 않는다
         *
         * @param {Hash Table} htData
         * @param {File} htData.oFile 업로드 파일
         * @return {Boolean}
         */
        function _onAvatarBeforeUpload(htData){
            if($yobi.isImageFile(htData.oFile) === false){
                _onAvatarUploadError(Messages("user.avatar.onlyImage"));
                return false;
            }
        }

        /**
         * 아바타 이미지 업로드가 완료된 후
         * ajaxForm 의 success 이벤트 핸들러
         *
         * @param {Hash Table} htData 업로드 된 파일의 정보
         */
        function _onAvatarUploaded(htData){
            var oRes = htData.oRes;

            // 업로드 완료한 파일이 이미지가 아니면 오류 처리하고 삭제
            if(oRes.mimeType.indexOf("image/") !== 0){
                _onAvatarUploadError(Messages("user.avatar.onlyImage"));
                yobi.Files.deleteFile({"sURL": oRes.url});
                return false;
            }

            // 업로드 진행 상태 100%
            _setAvatarProgressBar(100);

            // jCrop 을 사용가능한 환경에서만 관련 화면을 표시한다
            if(htVar.bUseJCrop){
                _showJcrop(oRes);
                return;
            }

            // 그렇지 않으면 아바타 설정 폼을 submit 한다
            // Crop 처리하지 않은 파일은 크기를 확인해서
            // 용량이 너무 크면 (> 1MB) 오류 표시하고 파일을 삭제한다
            if(oRes.size > htVar.nMaxFileSizeInNoCrop){
                _onAvatarUploadError(Messages("user.avatar.fileSizeAlert"));
                yobi.Files.deleteFile({"sURL": oRes.url});
                return false;
            }

            // 설정 폼에 avatarId 설정하고 submit
            _setAvatarIdOnForm(oRes.id);
            htElement.welFormAvatar.submit();
        }

        /**
         * 지정한 값 (아바타 이미지 파일 ID) 으로 필드 값을 설정한다
         * 필요한 필드(input)가 폼 내에 존재하지 않으면 생성하여 붙인다
         *
         * @param nAvatarId
         * @private
         */
        function _setAvatarIdOnForm(nAvatarId){
            var welAvatarId = htElement.welFormAvatar.find("input[name=avatarId]");

            // 폼에 해당하는 필드가 없으면 생성하여 붙인다
            if(welAvatarId.length === 0){
                welAvatarId = $('<input type="hidden" name="avatarId">');
                htElement.welFormAvatar.append(welAvatarId);
            }

            welAvatarId.val(nAvatarId);
        }

        /**
         * 지정한 파일 정보를 바탕으로 jCrop UI를 표시한다
         *
         * @param {Object} oRes 파일 정보
         */
        function _showJcrop(oRes){
            _clearJcrop();

            htElement.welAvatarCropImg.attr("src", oRes.url);
            htElement.welAvatarCropPreviewImg.attr("src", oRes.url);
            htElement.welAvatarCropWrap.modal("show");
        }

        /**
         * jCrop 적용할 이미지(welAvatarCropImg)가 로딩 완료되었을 때 이벤트 핸들러
         * 이벤트 핸들러 설정은 _initAvatarUploader 에서 한다
         *
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
         * 기존에 설정된 jCrop 관련 객체, 이미지 정보를 비운다
         * 이미지 변경시를 위해 필요함
         *
         * @private
         */
        function _clearJcrop(){
            htElement.welAvatarCropImg.attr("src", "");
            htElement.welAvatarCropPreviewImg.attr("src", "");

            if(htVar.oJcrop){
                htVar.oJcrop.destroy();
                htVar.oJcrop = null;
            }
        }

        /**
         * jCrop 커스텀 이벤트 핸들러
         * 잘라내기 된 이미지를 미리보기 해주는 역할
         *
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

            // 미리보기 표시
            htElement.welAvatarCropPreviewImg.css({
                "width"     : Math.round(nRx * nWidth) + "px",
                "height"    : Math.round(nRy * nHeight) + "px",
                "marginLeft": "-" + Math.round(nRx * htData.x) + "px",
                "marginTop" : "-" + Math.round(nRy * htData.y) + "px"
            });

            htVar.htLastCrop = htData;
        }

        /**
         * jCrop 취소시 이벤트 핸들러
         * 완전하게 취소(= 전혀 영역을 선택하지 않은 상태)는 할 수 없고
         * 최소한 기본 영역(128x128) 이상의 이미지 영역을 선택한 상태로 한다
         */
        function _onAvatarImageCropCancel(){
            if(htVar.oJcrop){
                htVar.oJcrop.setSelect([0, 0, 128, 128]);
            } else {
                htVar.htLastCrop = null;
            }
        }

        /**
         * jCrop 화면에서의 아바타 변경 버튼(welBtnSubmitCrop) 클릭시 이벤트 핸들러
         * 잘라낸 이미지를 파일(Blob)로 서버에 전송한다
         */
        function _onClickBtnSubmitCrop(){
            // 원본 이미지 크기를 알아내기 위해 새 객체로 불러온다
            var elImage = new Image();

            elImage.onload = function(){
                // 실제 이미지 크기와 jCrop 영역의 비율 계산
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

                // 캔버스에 Crop 이미지 데이터 생성
                var oContext = htElement.elAvatarCropCanvas.getContext("2d");
                oContext.drawImage(elImage, htCropData.x, htCropData.y, htCropData.w, htCropData.h, 0, 0, 128, 128);

                // canvas-to-blob.js
                // 캔버스 이미지 데이터를 Blob 으로 변환하여 업로드
                htElement.elAvatarCropCanvas.toBlob(function(oFile){
                    yobi.Files.uploadFile(oFile, "jCropUpload");
                }, "image/jpeg", 100);
            };

            elImage.src = htElement.welAvatarCropImg.attr("src");
        }

        /**
         * Crop 처리된 아바타 이미지 업로드가 완료된 후 이벤트 핸들러
         *
         * @param {Hash Table} htData 업로드 된 파일의 정보
         */
        function _onAvatarCroppedImageUploaded(htData){
            // 설정 폼에 avatarId 설정하고 submit
            _setAvatarIdOnForm(htData.oRes.id);
            htElement.welFormAvatar.submit();
        }

        /**
         * 아바타 이미지 업로드에 실패한 경우.
         * 일반적으로 서버 연결에 실패했을 때 이 상황이 발생한다.
         * ajaxForm 의 error 이벤트 핸들러.
         */
        function _onAvatarUploadError(sMessage){
            $yobi.alert(sMessage || Messages("user.avatar.uploadError"));
            _setAvatarProgressBar(0);
        }

        /**
         * 아바타 이미지 업로드 진행 상태
         * ajaxForm 의 uploadProgress 이벤트 핸들러
         *
         * @param {Wrapped Event} weEvt
         * @param {Number} nPosition
         * @param {Number}
         */
        function _onAvatarUploading(weEvt, nPosition, nTotal, nPercent){
            _setAvatarProgressBar(nPercent);
            htElement.welAvatarProgress.css("opacity", 1);
        }

        /**
         * 아바타 이미지 업로드 진행 상태 표시 함수
         *
         * @param {Number} nPercent
         */
        function _setAvatarProgressBar(nPercent){
            nPercent = parseInt(nPercent, 10);
            var nHeight = parseInt(htVar.nProgressHeight * (nPercent / 100), 10);
            htElement.welAvatarProgress.height(nHeight);

            // 꽉 차면 보이지 않게 하고 다시 0으로 되돌림
            if(nPercent === 100){
                htElement.welAvatarProgress.css("opacity", 0);

                setTimeout(function(){
                    _setAvatarProgressBar(0);
                }, 1000);
            }
        }

        /**
         * 비밀번호 확인 입력란 벗어날 때 이벤트 핸들러
         * 마지막 입력란이므로 전체 폼 유효성 검사
         */
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

        /**
         * 폼 영역에 있는 jquery.tooltip 모두 제거하는 함수
         */
        function _clearPopovers(){
            try {
                htElement.welFormPswd.find("input").each(function(i, v){
                    $(v).popover("destroy");
                });
            } catch(e){} // to avoid bootstrap bug
        }

        /**
         * 알림 On/Off 스위치 변경
         */
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

        _init(htOptions || {});
    };
})("yobi.user.Setting");
