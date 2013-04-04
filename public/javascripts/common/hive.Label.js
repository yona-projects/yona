/**
 * @(#)hive.Label.js 2013.03.13
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
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
		_updateLabels(htOptions.fCallback);		
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

		// ['gray', 'red', 'orange', 'yellow', 'green', 'CornflowerBlue', 'blue', 'purple', 'white']
		htVar.aColors = htVar.aColors || ['#999999','#da5454','#ff9933','#ffcc33','#99ca3c','#22b4b9','#4d68b1','#9966cc','#ffffff'];
		
		// label editor template
		htVar.sTplEditor = htVar.sTplEditor || '<div class="control-group label-editor">\
			<label id="custom-label-label" class="control-label">${labelNew}</label>\
			<div id="custom-label" class="controls">\
				<input id="custom-label-color" type="text" class="input-small" placeholder="${labelCustomColor}">\
				<input id="custom-label-category" type="text" class="input-small" data-provider="typeahead" autocomplete="off" placeholder="${labelCategory}">\
				<input id="custom-label-name" type="text" class="input-small" placeholder="${labelName}">\
				<button id="custom-label-submit" type="button" class="btn btn-inverse">${labelAdd}</button>\
			</div>\
		</div>';
		
		// add label template
		htVar.sTplLabel = htVar.sTplLabel || '<div class="control-group"><label class="control-label" data-category="${category}">${category}</label></div>';
		htVar.sTplControls = htVar.sTplControls || '<div class="controls" data-toggle="buttons-checkbox" data-category="${category}"></div>';
		htVar.sTplBtnLabelId = htVar.sTplBtnLabelId || '<button type="button" class="btn ${labelCSS}" data-labelId="${labelId}">${labelName}</button>';
		htVar.sTplBtnColor = htVar.sTplBtnColor || '<button type="button" class="issue-label n-btn small" style="background-color:${color}">&nbsp;';
	}
	
	/**
	 * initialize element variable
	 */
	function _initElement(){
		htElement.welContainer  = $("fieldset.labels");
		htElement.welSearchForm = $('form#issue-form,form.form-search');
		
		// add label
		htElement.welLabels = $('.labels'); 
		htElement.welLabelEditor = $('.label-editor'); 		
	}
	
	/**
	 * initialize event handler
	 */
	function _attachEvent(){
		htElement.welSearchForm.submit(_onSubmitSearchForm);		
	}
	
	/**
	 * initialize Label Editor
	 */
	function _initLabelEditor(){
		htElement.welContainer.append(_getLabelEditor());
		
		// custom label editor
		htElement.welBtnLabel = $('#custom-label button.issue-label');
		htElement.welBtnCustomLabelSubmit  = $('#custom-label-submit');
		
		htElement.welCustomLabelInput = $('#custom-label input'); // color, name, category
		htElement.welCustomLabelColor = $('#custom-label-color'); 
		htElement.welCustomLabelCategory = $('#custom-label-category');
		htElement.welCustomLabelName =  $('#custom-label-name');
		
		// attach event
		htElement.welBtnLabel.click(_onClickBtnLabel);
		htElement.welBtnCustomLabelSubmit.click(_onClickBtnSubmitCustom);
		
		htElement.welCustomLabelInput.keypress(_onKeypressInputCustom);
		htElement.welCustomLabelInput.keyup(_onKeyupInputCustom);
		htElement.welCustomLabelColor.keyup(_onKeyupInputColorCustom);		
	}	
	
	/**
	 * 검색 버튼 클릭시 라벨 선택도 반영되도록 검색폼에 필드 추가
	 */
	function _onSubmitSearchForm(){
		var aButtons = [];
		var welButtons = $('fieldset.labels div[data-category] button.active[data-labelId]');
		
		welButtons.each(function(nIndex, welBtn){
			aButtons.push('<input type="hidden" name="labelIds" value="'+ welBtn.attr('data-labelId') + '">');
		});
		
		htElement.welSearchForm.append(aButtons);
		welButtons = aButtons = null;
		
		return true;		
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
	 * 새 라벨 색상 버튼 클릭시 이벤트 핸들러
	 * @param {Event} eEvt
	 */
	function _onClickBtnLabel(eEvt){
		var welTarget = $(eEvt.target || eEvt.srcElement || eEvt.originalTarget);

		// Set clicked button active.
		htElement.welBtnLabel.removeClass("active");
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
	 * updateLabels
	 * 라벨 목록을 서버로부터 수신하여 목록 생성 
	 * 분류에 따라 라벨 목록을 만드는 것은 _addLabelIntoCategory 에서 수행
	 * @param {Function} fCallback 완료 후 수행할 콜백 함수
	 */
	function _updateLabels(fCallback){
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
	 * remove label
	 * 라벨 삭제
	 */
	function _removeLabel(id) {
		var label = $('[labelId=' + id + ']');
		
		if (label.siblings().size() > 0) {
			label.remove();
		} else {
			var category = $(label.parents('div').get(0)).attr('category');
			$('[category="' + category + '"]').parent().remove();
			var source = htElement.welCustomLabelCategory.typeahead().data('typeahead').source;
			source.pop(source.indexOf(category));
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
		
		// send request
		$hive.sendForm({
			"sURL"     : htVar.sURLPost, 
			"htData"   : htData,
			"htOptForm": {"enctype": "multipart/form-data"},
			"fOnLoad"  : _onLoadAddCustomLabel
		});
	}
	
	function _onLoadAddCustomLabel(oRes){
		// label.id, label.category, label.name, label.color
		if (!(oRes instanceof Object)) {
			console.log('Failed to add custom label - Server error.');
			return;
		}
		_addLabelIntoCategory(oRes); 
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
		document.styleSheets[0].addRule('.labels button.btn.active[data-labelId="' + oLabel.id + '"]', 'background-color: ' + oLabel.color);
		
		// label Id		
		var welBtnLabelId = $.tmpl(htVar.sTplBtnLabelId, {
			"labelId": oLabel.id,
			"labelName": oLabel.name,
			"labelCSS" : 'active-' + $hive.getContrastColor(oLabel.color)
		});
		
		if(htVar.bEditable){ // Delete link
			welBtnLabelId.append(_getDeleteLink(oLabel.id, oLabel.color));
			htElement.welCustomLabelCategory.typeahead().data("typeahead").source.push(oLabel.category)
		}

		var welCategory = $('fieldset.labels div[category="' + oLabel.category + '"]');
		if (welCategory.length > 0) {
			welCategory.append(welBtnLabelId);
			return welBtnLabelId;
		}
		
		var welLabel = $.tmpl(htVar.sTplLabel, {"category": oLabel.category});
		var welControls = $.tmpl(htVar.sTplControls, {"category": oLabel.category});
		welControls.append(welBtnLabelId); // Edit Button
		welLabel.append(welControls); // Controls

		// add label into category
		if(htElement.welLabelEditor.length > 0) { 
			htElement.welLabelEditor.before(welLabel);
		} else {
			htElement.welLabels.append(welLabel);
		}

		return welBtnLabelId;
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
			$hive.sendForm({
				"sURL"   : htVar.sURLLabels + '/' + sId,
				"htData" : {"_method": "delete"},
				"fOnLoad": function(){
					_removeLabel(sId);					
				}
			});	
		};
		
		var welLinkDelete = $('<a class="icon-trash del-link active-' + $hive.getContrastColor(sColor) + '">');
		welLinkDelete.click(fOnClick);

		return welLinkDelete;
	}
	
	/**
	 * 지정한 라벨을 선택한 상태로 만들어주는 함수
	 */
	function _setActiveLabel(sId, sColor){
		// 색상 지정하고
		$('button.issue-label[labelId="' + sId + '"]').css({
			'color': $hive.getContrastColor(sColor),
			'background-color': sColor
		});

		// 버튼 엘리먼트에 active 클래스 지정
	    $('.labels button.btn[labelId="' + sId + '"]').addClass('active');		
	}
	
	//_init(htOptions);
	
	return {
		"init": _init,
		"setActiveLabel": _setActiveLabel
	};
})();