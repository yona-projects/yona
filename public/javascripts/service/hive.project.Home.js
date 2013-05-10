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
            _initTags();
		}

        function _initVar(htOptions){
            htVar.sURLProjectTags = htOptions.sURLProjectTags;
            htVar.sURLTags = htOptions.sURLTags;
            htVar.nProjectId = htOptions.nProjectId;
		}

		/**
		 * initialize element
		 */
		function _initElement(htOptions){
			htElement.welRepoURL = $("#repositoryURL");

            // tags
            htElement.welInputAddTag = $('input[name="newTag"]');
            htElement.welTags = $('#tags');
            htElement.welBtnAddTag = $('#addTag');
            htElement.welTagEditorToggle = $('#tag-editor-toggle');
            htElement.waTag = $();
		}
		
		/**
		 * attach event handler
		 */
		function _attachEvent(){
			htElement.welRepoURL.click(_onClickRepoURL);
            htElement.welInputAddTag.keypress(_onKeyPressNewTag);
            htElement.welBtnAddTag.click(_submitTag);
            htElement.welTagEditorToggle.on('click', function() {
                if ($(this).hasClass('active')) {
                    // Now inactive
                    _hideTagEditor();
                } else {
                    // Now active
                    _showTagEditor();
                }
            });
            
            new hive.ui.Typeahead(htElement.welInputAddTag, {
            	"sActionURL": htVar.sURLTags,
                "htData": {
                    "context": "PROJECT_TAGGING_TYPEAHEAD",
                    "project_id": htVar.nProjectId,
                    "limit": 8
                }
            });
		}

		function _onClickRepoURL(){
			htElement.welRepoURL.select();
		}

        /**
        * Add a tag, which user types in htElement.welInputAddTag, into #tags div.
        *
        * @param {Object} oEvent
        */
        function _onKeyPressNewTag(oEvent) {
            if (oEvent.keyCode == 13) {
                _submitTag();
                htElement.welInputAddTag.val("");
                return false;
            }
        }

        function _parseTag(sTag) {
            var sSeparator = ' - ';
            var aPart =
                jQuery.map(sTag.split(sSeparator), function(s) { return s.trim(); });
            var htTag;

            if (aPart.length > 2) {
                aPart = [aPart.shift(), aPart.join(sSeparator)];
            }

            if (aPart.length > 1) {
                htTag = {"category": aPart[0], "name": aPart[1]};
            } else if (aPart.length == 1) {
                htTag = {"category": "Tag", "name": aPart[0]};
            } else {
                return null;
            }

            return htTag;
        }

        /**
        * Submit new tag to add that.
        */
        function _submitTag () {
            var htTag = _parseTag(htElement.welInputAddTag.val());

            if (htTag == null) {
                return;
            }

		$hive.sendForm({
			"sURL"   : htVar.sURLProjectTags,
			"htData" : htTag,
			"fOnLoad": _appendTags
		});
        }

        /**
        * Get list of tags from the server and show them in #tags div.
        */
        function _initTags() {
		$hive.sendForm({
			"sURL"     : htVar.sURLProjectTags,
			"htOptForm": {"method":"get"},
			"fOnLoad"  : function(data) {
                    _appendTags(data);
                    _hideTagEditor();
                }
		});
        }

        /**
        * Make a tag element by given instance id and name.
        *
        * @param {String} sInstanceId
        * @param {String} sName
        */
        function _createTag(sInstanceId, sName) {
            // If someone clicks a delete button, remove the tag which contains
            // the button, and also hide its category in .project-info div if
            // the category becomes to have no tag.
            var fOnClickDelete = function() {
		$hive.sendForm({
			"sURL"   : htVar.sURLProjectTags + '/' + sInstanceId,
			"htData" : {"_method":"DELETE"},
			"fOnLoad": function(){
                        var welCategory = welTag.parent();
				welTag.remove();
                        if (welCategory.children('span').length == 0) {
                            welCategory.remove();
                        }
			}
		});
            };

            var welDeleteButton = $('<a href="javascript:void(0)">&times;</a>').click(fOnClickDelete);
            var welTag = $('<span class="label">' + sName + '</span>').append(welDeleteButton);

            welTag.setRemovability = function(bFlag) {
                if (bFlag === true) {
                    welTag.addClass('label');
                    welDeleteButton.show();
                } else {
                    welTag.removeClass('label');
                    welDeleteButton.hide();
                }
            }

            htElement.waTag.push(welTag);

            return welTag;
        }

        /**
        * Append the given tags on #tags div to show them.
        *
        * @param {Object} htTags
        */
        function _appendTags(htTags) {
            for(var sInstanceId in htTags) {
                var waChildren, newCategory;
                var htTag = _parseTag(htTags[sInstanceId]);

                waChildren =
                    htElement.welTags.children("[category=" + htTag.category + "]");

                if (waChildren.length > 0) {
                    waChildren.append(_createTag(sInstanceId, htTag.name));
                } else {
                    newCategory = $('<li class="info">')
                        .attr('category', htTag.category)
                        .append($('<strong>').text(htTag.category + ' : '))
                        .append(_createTag(sInstanceId, htTag.name));
                    htElement.welTags.append(newCategory);
                }
            }
        }

        /**
        * Show all delete buttons for all tags if bFlag is true, and hide if
        * bFlag is false.
        *
        * @param {Boolean} bFlag
        */
        function _setTagsRemovability(bFlag) {
            jQuery.map(htElement.waTag, function(tag) { tag.setRemovability(bFlag); });
        }

        /**
        * Make .project-info div editable.
        *
        * @param {Boolean} bFlag
        */
        function _hideTagEditor() {
            _setTagsRemovability(false);
            htElement.welInputAddTag.addClass('hidden');
            htElement.welBtnAddTag.addClass('hidden');
        }

        /**
        * Make .project-info div uneditable.
        *
        * @param {Boolean} bFlag
        */
        function _showTagEditor() {
            _setTagsRemovability(true);
            htElement.welInputAddTag.removeClass('hidden');
            htElement.welBtnAddTag.removeClass('hidden');
        }

		_init(htOptions || {});
	};
	
})("hive.project.Home");