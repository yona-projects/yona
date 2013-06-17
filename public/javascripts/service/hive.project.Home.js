/**
 * @(#)hive.project.Home.js 2013.04.25
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){
		
		var htVar = {};
		var htElement = {};
	
		function _init(htOptions){
			var htOpt = htOptions || {};
			_initVar(htOpt);
			_initElement(htOptions);
			_attachEvent();			
            _initLabels();
            
            _resizeProjectInfo();
		}

        function _initVar(htOptions){
            htVar.sURLProjectLabels = htOptions.sURLProjectLabels;
            htVar.sURLLabels = htOptions.sURLLabels;
            htVar.sURLLabelCategories = htOptions.sURLLabelCategories;
            htVar.nProjectId = htOptions.nProjectId;
		}

		/**
		 * initialize element
		 */
		function _initElement(htOptions) {
            var welBtnPlus = $('#plus-button-template').tmpl();

			htElement.welRepoURL = $("#repositoryURL");

            // project label
            htElement.welLabelBoard = htOptions.welLabelBoard;
            htElement.welLabelEditorToggle = htOptions.welLabelEditorToggle;

            htElement.welInputCategory = $('#label-input-template').tmpl();
            htElement.welSubmitCategory = $('#label-submit-template').tmpl();
            htElement.welInputCategory.attr('placeholder', Messages('label.addNewCategory'));
            htElement.welInputCategory.keypress(_onKeyPressNewCategory);
            htElement.welSubmitCategory.click(_onClickNewCategory);

            htElement.welInputLabel = $('#label-input-template').tmpl();
            htElement.welSubmitLabel = $('#label-submit-template').tmpl();
            htElement.welInputLabel.keypress(_onKeyPressNewLabel);
            htElement.welInputLabel.attr('placeholder', Messages('label.addNewLabel'));
            htElement.welSubmitLabel.click(_submitLabel);

            htElement.welInputLabelBox = $('<p>')
                .append(htElement.welInputLabel)
                .append(htElement.welSubmitLabel);

            htElement.welInputCategoryBox = $('<p>')
                .append(htElement.welInputCategory)
                .append(htElement.welSubmitCategory);

            htElement.welBtnPlusLabel = welBtnPlus.clone();
            htElement.welBtnPlusCategory = welBtnPlus.clone();

            htElement.aLabel = [];
            htElement.htCategory = {};
            htElement.aBtnPlusLabel = [];

            htElement.welNewCategory = $('<li>')
                .append(htElement.welBtnPlusCategory);

            htElement.welLabelBoard.append(htElement.welNewCategory);
            
            htElement.welHome = $(".project-home");
            htElement.welHomeLogo = htElement.welHome.find(".logo");
            htElement.welHomeInfo = htElement.welHome.find(".project-info");
            htElement.welHomeMember = htElement.welHome.find(".member-info");            
		}
		
		/**
		 * attach event handler
		 */
		function _attachEvent(){
			htElement.welRepoURL.click(_onClickRepoURL);
            htElement.welLabelEditorToggle.on('click', function() {
                if ($(this).hasClass('active')) {
                    // Now inactive
                    $(this).removeClass('active');
                    $(this).text(Messages("button.edit"));
                    _hideLabelEditor();
                } else {
                    // Now active
                    $(this).addClass('active');
                    $(this).text(Messages("button.done"));
                    _showLabelEditor();
                }
            });

            new hive.ui.Typeahead(htElement.welInputCategory, {
                "sActionURL": htVar.sURLLabelCategories,
                "htData": {
                    "project_id": htVar.nProjectId,
                    "limit": 8
                }
            });

            htElement.welBtnPlusCategory.click(_onClickPlusCategory);

            $(window).bind("resize", _resizeProjectInfo);
		}
		
		function _resizeProjectInfo(){
		    htElement.welHomeInfo.width(htElement.welHome.width() - (htElement.welHomeLogo.width() + htElement.welHomeMember.width() + 21));
		}

		function _onClickRepoURL(){
			htElement.welRepoURL.select();
		}

        /**
        * When any key is pressed on input box in any Category line.
        *
        * @param {Object} oEvent
        */
        function _onKeyPressNewLabel(oEvent) {
            if (oEvent.keyCode == 13) {
                _submitLabel();
                return false;
            }
        }

        /**
         * When any key is pressed on input box in New Category line.
         *
         * @param {Object} oEvent
         */
        function _onKeyPressNewCategory(oEvent) {
            if (oEvent.keyCode == 13) {
                _onClickNewCategory();
                return false;
            }
        }

        /**
         * Read data to create a label from input box.
         */
        function _labelFromInput() {
            return {
                "category": htElement.welInputLabel.data('category'),
                "name": htElement.welInputLabel.val()
            };
        }

        /**
        * Submit new tag to add that.
        */
        function _submitLabel() {
            var htLabel = _labelFromInput();

            if (htLabel == null) {
                return;
            }

            htElement.welInputLabel.val("");

            $hive.sendForm({
                "sURL"   : htVar.sURLProjectLabels,
                "htData" : htLabel,
                "fOnLoad": _appendLabels
            });
        }

        var aRequired = ["Language", "License"];

        function isRequired(sCategory) {
            return aRequired.indexOf(sCategory) < 0;
        }

        /**
        * Get list of tags from the server and show them in #tags div.
        */
        function _initLabels() {
            $hive.sendForm({
                "sURL"     : htVar.sURLProjectLabels,
                "htOptForm": {"method":"get"},
                "fOnLoad"  : function(data) {
                        _appendLabels(data);

                        for (var i = 0; i < aRequired.length; i++) {
                            var sCategory = aRequired[i];
                            if (!htElement.htCategory.hasOwnProperty(sCategory)) {
                                __addCategory(sCategory);
                            }
                        }

                        _hideLabelEditor();
                    }
            });
        }

        /**
        * Make a tag element by given instance id and name.)
        *
        * @param {String} sInstanceId
        * @param {String} sName
        */
        function _createLabel(sInstanceId, sName) {
            // If someone clicks a delete button, remove the tag which contains
            // the button, and also hide its category in .project-info div if
            // the category becomes to have no tag.
            var fOnLoadAfterDeleteLabel = function() {
                var welCategory = welLabel.parent().parent();
                var sCategory = welCategory.data('category');
                welLabel.remove();
                if (welCategory.children('.label-list').children().length == 0
                    && isRequired(sCategory)
                    && htElement.welInputLabel.data('category') != sCategory) {
                    delete htElement.htCategory[sCategory];
                    welCategory.remove();
                }
            };

            var fOnClickDelete = function() {
                $hive.sendForm({
                    "sURL"   : htVar.sURLProjectLabels + '/' + sInstanceId,
                    "htData" : {"_method":"DELETE"},
                    "fOnLoad": fOnLoadAfterDeleteLabel
                });
            };

            var welDeleteButton = $('#label-delete-button-template')
                .tmpl()
                .click(fOnClickDelete);

            var welLabel = $('#label-template')
                .tmpl({'name': sName})
                .append(welDeleteButton);

            welLabel.setRemovability = function(bFlag) {
                if (bFlag === true) {
                    welLabel.addClass('label');
                    welDeleteButton.show();
                } else {
                    welLabel.removeClass('label');
                    welDeleteButton.hide();
                }
            }

            htElement.aLabel.push(welLabel);

            return welLabel;
        }

        /**
        * Append the given tags on #tags div to show them.
        *
        * @param {Object} htLabels
        */
        function _appendLabels(htLabels) {
            for(var sInstanceId in htLabels) {
                var waCategory, welCategory;
                var htLabel = htLabels[sInstanceId];

                waCategory = htElement.welLabelBoard
                    .children("[data-category=" + htLabel.category + "]");

                if (waCategory.length > 0) {
                    waCategory
                        .children(".label-list")
                        .append(_createLabel(sInstanceId, htLabel.name));
                } else {
                    __addCategory(htLabel.category)
                        .children(".label-list")
                        .append(_createLabel(sInstanceId, htLabel.name));
                }
            }
        }

        /**
         * Create a category consists with category name, labels belong
         * to this and plus button to add a label.
         *
         * @param {String} sCategory
         * @return {Object} The created category
         */
        function _createCategory(sCategory) {
            var welBtnPlusLabel = htElement.welBtnPlusLabel
                .clone()
                .data('category', sCategory)
                .click(_onClickPlusLabel);

            var welCategory = $('#category-template')
                .tmpl({'category': sCategory})
                .append(welBtnPlusLabel);

            welCategory.welBtnPlusLabel = welBtnPlusLabel;
            htElement.aBtnPlusLabel.push(welBtnPlusLabel);

            return welCategory;
        }

        function __addCategory(sCategory) {
            welCategory = _createCategory(sCategory);
            htElement.htCategory[sCategory] = welCategory;
            htElement.welNewCategory.before(welCategory);

            return welCategory;
        }

        /**
         * Add a category just before `htElement.welNewCategory`.
         */
        function _onClickNewCategory() {
            var sCategory = htElement.welInputCategory.val();
            var welCategory = htElement.htCategory[sCategory];

            if (!welCategory) {
                welCategory = __addCategory(sCategory);
            }

            htElement.welInputCategory.val("");
            welCategory.welBtnPlusLabel.trigger('click');
            if (!htElement.welInputCategory.is(":focus")) {
                htElement.welInputCategory.trigger('focus');
            }
        }

        /**
         * When a plus button in the end of the Label Board is clicked..
         */
        function _onClickPlusCategory() {
            htElement.welInputLabelBox.hide();
            htElement.welInputCategoryBox.show();
            $(this).before(htElement.welInputCategoryBox);
            jQuery.map(htElement.aBtnPlusLabel, function(btn) { btn.show(); });
            htElement.welBtnPlusCategory.hide();

            if (!htElement.welInputCategory.is(":focus")) {
                htElement.welInputCategory.trigger('focus');
            }
        }

        /**
         * When a plus button in each category is clicked..
         */
        function _onClickPlusLabel() {
            var sCategory, welCategory, nLabel;

            for (sCategory in htElement.htCategory) {
                if ($(this).data('category') == sCategory) {
                    continue;
                }

                welCategory = htElement.htCategory[sCategory];

                nLabel = welCategory.children('.label-list').children().length;

                if (nLabel == 0 && isRequired(sCategory)) {
                    delete htElement.htCategory[sCategory];
                    welCategory.remove();
                }
            }

            htElement.welInputLabel.data('category', $(this).data('category'));

            new hive.ui.Typeahead(htElement.welInputLabel, {
                "sActionURL": htVar.sURLLabels,
                "htData": {
                    "category":  $(this).data('category'),
                    "project_id": htVar.nProjectId,
                    "limit": 8
                }
            });

            htElement.welInputCategoryBox.hide();
            htElement.welInputLabelBox.show();
            $(this).after(htElement.welInputLabelBox);
            jQuery.map(htElement.aBtnPlusLabel, function(btn) { btn.show(); });
            $(this).hide();

            if (!htElement.welInputLabel.is(":focus")) {
                htElement.welInputLabel.trigger('focus');
            }
        }

        /**
        * Show all delete buttons for all labels if bFlag is true, and hide if
        * bFlag is false.
        *
        * @param {Boolean} bFlag
        */
        function _setLabelsRemovability(bFlag) {
            jQuery.map(htElement.aLabel,
                    function(label) { label.setRemovability(bFlag); });
        }

        /**
        * Make .project-info div editable.
        *
        * @param {Boolean} bFlag
        */
        function _hideLabelEditor() {
            _setLabelsRemovability(false);

            jQuery.map(htElement.aBtnPlusLabel, function(btn) { btn.hide(); });
            htElement.welBtnPlusCategory.hide();

            htElement.welInputCategoryBox.hide();
            htElement.welInputLabelBox.hide();

            htElement.welLabelBoard
                .css('height', htVar.nLabelBoardHeight);
            htElement.welLabelBoard.parent()
                .css('height', htVar.labelBoardParentHeight);
        }

        /**
        * Make .project-info div uneditable.
        *
        * @param {Boolean} bFlag
        */
        function _showLabelEditor() {
            _setLabelsRemovability(true);

            jQuery.map(htElement.aBtnPlusLabel, function(btn) { btn.show(); });
            htElement.welBtnPlusCategory.show();

            htVar.nLabelBoardHeight =
                htElement.welLabelBoard.css('height');
            htVar.nLabelBoardParentHeight =
                htElement.welLabelBoard.parent().css('height');

            htElement.welLabelBoard.css('height', 'auto');
            htElement.welLabelBoard.parent().css('height', 'auto');
        }

		_init(htOptions || {});
	};
	
})("hive.project.Home");
