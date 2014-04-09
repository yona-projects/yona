(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(){

        var htElement = {};

        /**
         * initialize
         */
        function _init(){
            _initElement();
            _attachEvent();
        }

        /**
         * initialize element variables
         */
        function _initElement(){
            // 프로젝트 설정 관련
            htElement.welForm = $("form#saveSetting");
            htElement.welInputLogo = $("#logoPath");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welInputLogo.change(_onChangeLogoPath);
        }

        /**
         * 프로젝트 로고 변경시 이벤트 핸들러
         */
        function _onChangeLogoPath(){
            var welTarget = $(this);

            if($yobi.isImageFile(welTarget) === false){
                $yobi.showAlert(Messages("project.logo.alert"));
                welTarget.val('');
                return;
            }

            htElement.welForm.submit();
        }

        _init();
    };

})("yobi.organization.Setting");
