/**
 * @(#)hive.Label.js 2013.03.13
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

/**
 * hive.Label
 * 라벨 목록을 가져다 표현해주는 역할 
 * (개별 삭제 링크 처리 포함)
 */
hive.Label = (function(htOptions){

	var htVar = {};
	var htElement = {};
	
	/**
	 * initialize
	 */
	function _init(htOptions){
		htOptions = htOptions || {"bEditable": false};
		
		_initVar(htOptions);
		_initElement(htOptions);

		// initialize label editor on bEditable == true
		if(htVar.bEditable){
			_initLabelEditor();
		}
		
		_attachEvent();
		_getLabels(htOptions.fOnLoad);		
	}
	
	/**
	 * initialize variable except element
	 *	htVar.sURLLabels = htOptions.sURLLabels;
	 *	htVar.sURLPost = htOptions.sURLPost;
	 *	htVar.bEditable = htOptions.bEditable;
	 */
	function _initVar(htOptions){
		// copy htOptions to htVar
		for(key in htOptions){
			htVar[key] = htOptions[key];
		}
		
		htVar.sURLLabel = htVar.sURLLabels.substr(0, htVar.sURLLabels.length-1);
		
		htVar.sTplLabel = htOptions.sTplLabel || '<div class="control-group"><label class="control-label" data-category="${category}">${category}</label></div>';
		htVar.sTplControls = htOptions.sTplControls || '<div class="controls label-group" data-category="${category}"></div>';		
		htVar.sTplBtnLabelId = htOptions.sTplBtnLabelId || '<button type="button" class="issue-label ${labelCSS}" data-labelId="${labelId}">${labelName}</button>';		
	}
	
	/**
	 * initialize element variable
	 */
	function _initElement(){
		htElement.welContainer  = $("fieldset.labels");
		htElement.welForm = $('form#issue-form,form.form-search,form#search');
		
		// add label
		htElement.welLabels = $('.labels'); 
		htElement.welLabelEditor = $('.label-editor'); 		
	}
	
	/**
	 * initialize event handler
	 */
	function _attachEvent(){
		htElement.welForm.submit(_onSubmitForm);		
	}
	
	/**
	 * 폼 전송시 라벨 선택도 반영되도록 자동으로 필드 추가
	 */
	function _onSubmitForm(){
		var aValues = [];
		var welButtons = $('fieldset.labels div[data-category] button.active[data-labelId]');
		
		welButtons.each(function(nIndex, elBtn){
			aValues.push('<input type="hidden" name="labelIds" value="'+ $(elBtn).attr('data-labelId') + '">');
		});
		
		htElement.welForm.append(aValues);
		welButtons = aButtons = null;

		return true;
	}

	/**
	 * initialize Label Editor
	 */
	function _initLabelEditor(){
		hive.LabelEditor.appendTo(htElement.welContainer, {
			"sURLPost" : htVar.sURLPost,
			"fOnCreate": _onCreateNewLabel
		});
	}
	
	/**
	 * 라벨 에디터가 새 라벨 생성후 호출하는 함수
	 * @param {Object} oLabel
	 */
	function _onCreateNewLabel(oLabel){
		_addLabelIntoCategory(oLabel);
	}
	
	/**
	 * getLabels
	 * 라벨 목록을 서버로부터 수신하여 목록 생성 
	 * 분류에 따라 라벨 목록을 만드는 것은 _addLabelIntoCategory 에서 수행
	 * @param {Function} fCallback 완료 후 수행할 콜백 함수
	 */
	function _getLabels(fCallback){
		var fOnLoad = function(oRes) {
			if(!(oRes instanceof Object)){
				console.log('Failed to update - Server error');
				return;
			}

			// add label into category after sort
			var aLabels = oRes.sort(function(a, b) {
				return (a.category == b.category) ? (a.name > b.name) : (a.category > b.category);
			});
			$(aLabels).each(function(nIndex, oLabel){
				_addLabelIntoCategory(oLabel);
			});

			// run callback function
			if (typeof fCallback == "function") {
				fCallback();
			}
			
			aLabels = null;
		};

		// send request
		$hive.sendForm({
			"sURL"     : htVar.sURLLabels,
			"fOnLoad"  : fOnLoad,
			"htOptForm": {"method":"get"},
		});
	}

	/**
	 * add label into category
	 * 새 라벨을 지정한 분류에 추가. 
	 * 서버 통신용 함수는 아니고 화면에 표시하기 위한 목적. 
	 * @param {Number} oLabel.id 라벨 id
	 * @param {String} oLabel.category 라벨 분류
	 * @param {String} oLabel.name 라벨 이름
	 * @param {String} oLabel.color 라벨 색상
	 * @returns {Wrapped Element} 추가된 라벨 버튼 엘리먼트
	 */
	function _addLabelIntoCategory(oLabel) {
		// set Label Color
		_setLabelColor(oLabel);
		
		// label Id		
		var welBtnLabelId = $.tmpl(htVar.sTplBtnLabelId, {
			"labelId": oLabel.id,
			"labelName": oLabel.name,
			"labelCSS" : 'active-' + $hive.getContrastColor(oLabel.color)
		});
		
		// 편집모드: 삭제 링크 추가
		if(htVar.bEditable){ 
			welBtnLabelId.append(_getDeleteLink(oLabel.id, oLabel.color));
		}
		welBtnLabelId.click(_onClickLabel);
		
		// 이미 같은 카테고리가 있으면 거기에 넣고
		var welCategory = $('fieldset.labels div[data-category="' + oLabel.category + '"]');
		if (welCategory.length > 0) {
			welCategory.append(welBtnLabelId);
			return welBtnLabelId;
		}
		
		// 없으면 새 카테고리 줄을 추가한다
		var welLabel = $.tmpl(htVar.sTplLabel, {"category": oLabel.category});
		var welControls = $.tmpl(htVar.sTplControls, {"category": oLabel.category});
		welControls.append(welBtnLabelId); // Edit Button
		welLabel.append(welControls); // Controls

		// add label into category
		if(htElement.welLabelEditor.length > 0) { 
			htElement.welLabelEditor.before(welLabel);
		} else {
			htElement.welLabels.prepend(welLabel);
		}

		return welBtnLabelId;
	}

	/**
	 * 라벨 엘리먼트가 활성화 되었을때 (= active 클래스가 주어졌을때)
	 * 적용할 배경색/글자색 CSS Rule 추가하는 함수
	 * @param {Object} oLabel 
	 */
	function _setLabelColor(oLabel){
		var sCSSTarget = '.labels .issue-label.active[data-labelId="' + oLabel.id + '"]';
		document.styleSheets[0].addRule(sCSSTarget, 'background-color: ' + oLabel.color);
		document.styleSheets[0].addRule(sCSSTarget, 'color: ' + $hive.getContrastColor(oLabel.color));
	}

	/**
	 * 라벨 엘리먼트를 클릭했을때 이벤트 핸들러
	 * active 클래스를 토글한다
	 */
	function _onClickLabel(e){
        var welTarget = $(e.target || e.srcElement || e.originalTarget);
        welTarget.toggleClass("active");
        return false;
	}
	
	/**
	 * remove label
	 * 라벨 삭제
	 */
	function _removeLabel(id) {
		var label = $('[data-labelId=' + id + ']');
		
		if (label.siblings().size() > 0) {
			label.remove();
			return;
		}
		
		var category = $(label.parents('div').get(0)).attr('data-category');
		$('[data-category="' + category + '"]').parent().remove();
		
		// 라벨 에디터의 분류 자동완성에서도 제거
		if(htVar.bEditable){
			hive.LabelEditor.removeCategory(category);
		}
	}

	/**
	 * add delete link
	 * 라벨 삭제 링크 추가 함수
	 * @param {String} sId
	 * @param {String} sColor
	 * @return {Wrapped Element}
	 */
	function _getDeleteLink(sId, sColor){
		var fOnClick = function(){
			if(confirm(Messages("label.confirm.delete")) === false){
				return false;
			}
			
			$hive.sendForm({
				"sURL"   : htVar.sURLLabel + '/' + sId + '/delete',
				"htData" : {"_method": "delete"},
				"fOnLoad": function(){
					_removeLabel(sId);					
				}
			});	
		};
		
		//$('<a class="icon-trash del-link active-' + $hive.getContrastColor(sColor) + '">');
		var welLinkDelete = $('<a class="del-link">&times;</a>');
		welLinkDelete.click(fOnClick);

		return welLinkDelete;
	}
	
	/**
	 * 지정한 라벨을 선택한 상태로 만들어주는 함수
	 * @param {String} sId
	 * @param {String} sColor deprecated
	 */
	function _setActiveLabel(sId, sColor){
		// 색상 지정: addLabelIntoCategory 단계에서 
		// 이미 .active 상태의 색상이 지정되어 있음

		// 해당되는 라벨 엘리먼트에 active 클래스 지정
	    $('.labels button.issue-label[data-labelId="' + sId + '"]').addClass('active');		
	}
	
	//_init(htOptions);
	
	return {
		"init": _init,
		"setActiveLabel": _setActiveLabel
	};
})();

/**
 * hive.LabelEditor
 * 새 라벨 추가를 위한 에디터 인터페이스
 */
hive.LabelEditor = (function(welContainer, htOptions){
	
	var htVar = {};
	var htElement = {};
	
	/**
	 * initialize
	 * @param {Wrapped Element} welContainer Container Element to append label editor
	 * @param {Hash Table} htOptions
	 */
	function _init(welContainer, htOptions){
		_initVar(htOptions);
		_initElement(welContainer);
		_attachEvent();
	}
	
	/**
	 * initialize variables
	 */
	function _initVar(htOptions){
		htVar.sURLPost = htOptions.sURLPost;
		htVar.fOnCreate = htOptions.fOnCreate || function(){};

		htVar.aColors = htOptions.aColors || ['#999999','#da5454','#ff9933','#ffcc33','#99ca3c','#22b4b9','#4d68b1','#9966cc','#ffffff'];
		htVar.sTplEditor = htOptions.sTplEditor || '<div class="control-group label-editor">\
		<label id="custom-label-label" class="control-label">${labelNew}</label>\
		<div id="custom-label" class="controls">\
			<input id="custom-label-color" type="text" class="input-small" placeholder="${labelCustomColor}">\
			<input id="custom-label-category" type="text" class="input-small" data-provider="typeahead" autocomplete="off" placeholder="${labelCategory}">\
			<input id="custom-label-name" type="text" class="input-small" placeholder="${labelName}">\
			<button id="custom-label-submit" type="button" class="btn-transparent n-btn med black" style="vertical-align:top;">${labelAdd}</button>\
		</div>\
		</div>';
		htVar.sTplBtnColor = htOptions.sTplBtnColor || '<button type="button" class="issue-label n-btn small" style="background-color:${color}">&nbsp;';		
	}
	
	/**
	 * initialize elements
	 */
	function _initElement(welContainer){
		htElement.welContainer = $(welContainer);
		htElement.welEditor = _getLabelEditor();
		htElement.welContainer.append(htElement.welEditor);
		
		htElement.waBtnCustomColor = $("#custom-label button.issue-label");
		htElement.welBtnCustomLabelSubmit  = $('#custom-label-submit');
		
		htElement.welCustomLabelName =  $('#custom-label-name');
		htElement.welCustomLabelInput = $('#custom-label input'); // color, name, category
		htElement.welCustomLabelColor = $('#custom-label-color'); 
		htElement.welCustomLabelCategory = $('#custom-label-category');
		htElement.welCustomLabelCategory.typeahead();
	}
	
	/**
	 * attach events
	 */
	function _attachEvent(){
		htElement.waBtnCustomColor.click(_onClickBtnCustomColor);
		htElement.welBtnCustomLabelSubmit.click(_onClickBtnSubmitCustom);
		
		htElement.welCustomLabelInput.keypress(_onKeypressInputCustom);
		htElement.welCustomLabelInput.keyup(_onKeyupInputCustom);
		htElement.welCustomLabelColor.keyup(_onKeyupInputColorCustom);		
	}	
	
	/**
	 * Get label Editor
	 * 새 라벨 편집기 영역 엘리먼트를 생성해서 반환하는 함수
	 * @returns {Wrapped Element} 
	 */
	function _getLabelEditor(){
		// label editor HTML
		var welEditor = $.tmpl(htVar.sTplEditor, {
			"labelAdd"		: Messages("label.add"),
			"labelNew"		: Messages("label.new"),
			"labelName"		: Messages("label.name"),
			"labelCategory"	: Messages('label.category'),
			"labelCustomColor": Messages("label.customColor")
		});
		
		// generate color buttons
		var welControls = welEditor.find(".controls");
		if(welControls && htVar.aColors.length > 0){
			var aColorBtns = [];
			htVar.aColors.forEach(function(sColor){
				aColorBtns.push($.tmpl(htVar.sTplBtnColor, {"color": sColor}));
			});
			welControls.prepend(aColorBtns);
			welControls = aColorBtns = null;
		}

		return welEditor;
	}
		
	/**
	 * 새 라벨 추가 버튼 클릭시 이벤트 핸들러
	 */
	function _onClickBtnSubmitCustom(){
		_addCustomLabel();
	}
	
	function _onKeypressInputCustom(e){
		return !(e.keyCode === 13);
	}
	
	function _onKeyupInputCustom(e){
		if(e.keyCode === 13){
			_addCustomLabel();
		}
	}
	
	/**
	 * add custom label
	 * 새 라벨 추가
	 */
	function _addCustomLabel(){
		var htData = {
			"name"    : htElement.welCustomLabelName.val(),
			"color"   : htElement.welCustomLabelColor.val(),
			"category": htElement.welCustomLabelCategory.val()
		};
		
		// 하나라도 입력안된 것이 있으면 서버 요청 하지 않음
		if(htData.name.length === 0 || htData.color.length === 0 || htData.category.length === 0){
			return false;
		}
		
		// send request
		$hive.sendForm({
			"sURL"     : htVar.sURLPost, 
			"htData"   : htData,
			"htOptForm": {"enctype": "multipart/form-data"},
			"fOnLoad"  : function(oRes){
				// label.id, label.category, label.name, label.color
				if (!(oRes instanceof Object)) {
					console.log('Failed to add custom label - Server error.');
					return;
				}
				htElement.welCustomLabelCategory.data("typeahead").source.push(oRes.category);
				htVar.fOnCreate(oRes);
			}
		});
	}
	
	/**
	 * 새 라벨 색상 버튼 클릭시 이벤트 핸들러
	 * @param {Event} eEvt
	 */
	function _onClickBtnCustomColor(eEvt){
		var welTarget = $(eEvt.target || eEvt.srcElement || eEvt.originalTarget);

		// Set clicked button active.
		htElement.waBtnCustomColor.removeClass("active");
		welTarget.addClass("active");

		// Get the selected color.
		var sColor = welTarget.css('background-color');

		// Fill the color input area with the hexadecimal value of
		// the selected color.
		htElement.welCustomLabelColor.val(new RGBColor(sColor).toHex());

		_updateSelectedColor(sColor);

		// Focus to the category input area.
		htElement.welCustomLabelCategory.focus();		
	}
	
	/**
	 * 새 라벨 색상 코드 입력 <input>이 업데이트 될 때
	 * 이름 입력 <input> 영역 배경색/글씨색 업데이트 하도록
	 */
	function _onKeyupInputColorCustom(){
		var sColor = htElement.welCustomLabelColor.val();
		var oColor = new RGBColor(sColor);
		
		if (oColor.ok) {
			_updateSelectedColor(sColor);
		}
		
		oColor = null;
	}
	
	/**
	 * updateSelectedLabel Color
	 * 지정한 색으로 새 라벨 이름 영역의 배경색을 설정하고
	 * 글자색을 배경색에 맞추어 업데이트 해주는 함수 
	 * @param {String} sBgColor 배경색
	 */
	function _updateSelectedColor(sBgColor){
		// Change the name input area's color to the selected color.
		var sFgColor = $hive.getContrastColor(sBgColor);
		htElement.welCustomLabelName.css({
			"color": sFgColor,
			"background-color": sBgColor
		})

		// Change also place holder's
		// TODO: 이 부분도 나중에 정리할 것. #custom-label-name 고정되어 있음
		var aSelectors = ['#custom-label-name:-moz-placeholder', 
		                 '#custom-label-name:-ms-input-placeholder', 
		                 '#custom-label-name::-webkit-input-placeholder'];
		
		var elStyle = document.styleSheets[0];
		var sStyleColor = 'color:' + sFgColor + ' !important';
		var sStyleOpacity = 'opacity: 0.8';
		
		aSelectors.forEach(function(sSelector){
			try {
				elStyle.addRule(sSelector, sStyleColor);
				elStyle.addRule(sSelector, sStyleOpacity);
			} catch (e){ }		
		});
		
		aSelectors = elStyle = null;
	}
	
	function _removeCategoryTypeahead(sCategory){
		var source = htElement.welCustomLabelCategory.typeahead().data('typeahead').source;
		source.pop(source.indexOf(sCategory));
	}
		
	return {
		"appendTo": _init,
		"removeCategory": _removeCategoryTypeahead
	}
})();
