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
            htElement.welAvatarWrap = $("#avatarWrap");
            htElement.welAvatarImage = htElement.welAvatarWrap.find("img");
            htElement.welAvatarProgress = htElement.welAvatarWrap.find(".progress");
            htVar.nProgressHeight = htElement.welAvatarWrap.height();

            htElement.welBtnUploadFile = $("#btnUploadFile");
            htElement.welBtnSubmitCrop = $("#btnSubmitCrop");
            htElement.welImgCrop = $("#avatarImgCrop");

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

            // 아바타 업로드 설정
            yobi.Files.attach({
                "beforeUpload"  : _onAvatarBeforeUpload,
                "successUpload" : _onAvatarUploaded,
                "errorUpload"   : _onAvatarUploadError,
                "uploadProgress": _onAvatarUploading
            });
            yobi.Files.getUploader(".avatar-frm");

            // 알림 설정 변경
            htElement.welChkNotiSwtich.change(_onChangeNotiSwitch);
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

            htElement.welAvatarImage.attr("src", oRes.url);

            // 설정 폼에 avatarId 설정
            var welAvatarId = htElement.welFormAvatar.find("input[name=avatarId]");
            if(welAvatarId.length === 0){ // 없으면 새로 설정하고
                welAvatarId = htElement.welFormAvatar.append($("<input>").attr({
                    "type": "hidden",
                    "name": "avatarId",
                    "value": oRes.id
                }));
            } else { // 이미 있으면 값만 수정
                welAvatarId.attr("value", oRes.id);
            }

            _setAvatarProgressBar(100);

            // Crop 후에 업로드 인지, 처음 업로드인지 구분
            if(!htVar.oJcrop){
                _setJcrop(oRes); // jCrop 설정
            } else {
                htElement.welFormAvatar.submit();
            }
        }

        /**
         * jCrop 설정
         *
         * @param {Object} oRes 파일 정보
         */
        function _setJcrop(oRes){
            htElement.welImgCrop.on("load", function(){
                htVar.oJcrop = null;

                htElement.welImgCrop.Jcrop({
                    "aspectRatio": 1,
                    "minSize"  : [128, 128],
                    "bgColor"  : "#fff",
                    "setSelect": [0, 0, 128, 128],
                    "onSelect" : _onAvatarImageCrop,
                    "onChange" : _onAvatarImageCrop,
                    "onRelease": _onAvatarImageCropCancel
                }, function(){
                    htVar.oJcrop = this;
                });
            });
            htElement.welImgCrop.show();
            htElement.welImgCrop.attr("src", oRes.url);

            // 파일 업로드 버튼은 감추고, 크롭 이미지 전송 버튼 활성화

            htElement.welBtnUploadFile.hide();
            htElement.welBtnSubmitCrop.show();
            htElement.welBtnSubmitCrop.click(_sendCroppedImage);
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

            var nWidth = htElement.welImgCrop.width();
            var nHeight = htElement.welImgCrop.height();

            // 미리보기 표시
            htElement.welAvatarImage.css({
                "width"     : Math.round(nRx * nWidth) + "px",
                "height"    : Math.round(nRy * nHeight) + "px",
                "marginLeft": "-" + Math.round(nRx * htData.x) + "px",
                "marginTop" : "-" + Math.round(nRy * htData.y) + "px"
            });

            htVar.htLastCrop = htData;
        }

        /**
         * jCrop 취소시 이벤트 핸들러
         * 완전하게 취소할 수 없고 늘 128x128 이상의 이미지 영역을 갖도록
         */
        function _onAvatarImageCropCancel(){
            if(htVar.oJcrop){
                htVar.oJcrop.setSelect([0, 0, 128, 128]);
            } else {
                htVar.htLastCrop = null;
            }
        }

        /**
         * jCrop 의 결과와 canvas 를 이용해서
         * 잘라낸 이미지를 서버에 전송하는 함수
         * #btnSubmitCrop 버튼을 클릭했을 때 실행된다
         */
        function _sendCroppedImage(){
            var elImage = new Image();
            var sTmpImageURL = htElement.welImgCrop.attr("src");

            // 원본 이미지 크기를 알아내기 위해 새 객체로 불러온다
            // 브라우저 캐시를 사용하므로 네트워크 호출 없음
            elImage.onload = function(){
                // 실제 이미지 크기와 jCrop 영역의 비율 계산
                var htData = htVar.htLastCrop;
                var nWidth = htElement.welImgCrop.width();
                var nRealWidth  = elImage.width;
                var nRw = nRealWidth / nWidth;
                var htCropData = {
                    "x": (htData.x * nRw),
                    "y": (htData.y * nRw),
                    "w": (htData.w * nRw),
                    "h": (htData.h * nRw)
                };

                var htEnv = yobi.Files.getEnv();

                // blob 전송이 가능한 환경이면
                if(htEnv.bXHR2){
                    // 임시 업로드 상태의 현재 파일은 삭제
                    yobi.Files.deleteFile({"sURL": sTmpImageURL});

                    // 캔버스를 이용해 Crop 이미지 데이터로 업로드
                    var elCanvas = document.getElementById("avatarCrop"); // canvas
                    var oContext = elCanvas.getContext("2d");
                    oContext.drawImage(elImage, htCropData.x, htCropData.y, htCropData.w, htCropData.h, 0, 0, 128, 128);

                    // canvas-to-blob.js
                    elCanvas.toBlob(function(oFile){
                        yobi.Files.uploadFile(oFile);
                    }, 'image/jpeg', 100);
                } else {
                    // TODO: 아니면 서버에 Crop 데이터만 전송한다 (for IE)
                    htElement.welFormAvatar.submit();
                }
            };
            elImage.src = sTmpImageURL;
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
                    //htElement.welFormBasic.submit();
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
                return _clearTooltips();
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

        _init(htOptions || {});
    };
})("yobi.user.Setting");
