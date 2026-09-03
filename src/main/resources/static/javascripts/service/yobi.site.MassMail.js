/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
         */
        function _init(htOptions){
            var htOpt = htOptions || {};
            _initVar(htOpt);
            _initElement(htOpt);
            _attachEvent();
        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.sURLProjects = htOptions.sURLProjects;
            htVar.sURLMailList = htOptions.sURLMailList;
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            // projects
            htElement.welInputProject = $('#input-project');
            htElement.welSelectedProjects = $('#selected-projects');
            htElement.welBtnSelectProject = $('#select-project');
            htElement.welBtnWriteEmail = $('#write-email');
            htElement.welProjectList = $('#project-list-wrap')
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welInputProject.keypress(_onKeyPressInputProject);
            htElement.welBtnSelectProject.click(_onClickSelectProject);
            htElement.welBtnWriteEmail.click(_onClickWriteEmail);
            new yobi.ui.Typeahead(htElement.welInputProject, {
                "sActionURL": htVar.sURLProjects
            });

            $('.mess-mail-wrap').on('click','[data-toggle="mail-type"]',_clickMailTypeLabel)
        }

        function _clickMailTypeLabel() {
            var sAction = $(this).data('action');
            htElement.welSelectedProjects.html("");
            htElement.welProjectList[sAction]();

        }

        /**
         * Launch a mail client to write an email.
         */
        function _onClickWriteEmail() {
            // Get project names from labels in #selected-projects div.
            var sMailingType = $('[name=mailingType]:checked').val();
            var waProjectSpan, aProjects;
            if (sMailingType == 'all') {
                aProjects = {'all': 'true'}
            } else {
                waProjectSpan = $('#selected-projects > .label');
                aProjects = [];
                for (var i = 0; i < waProjectSpan.length; i++) {
                    aProjects.push(waProjectSpan[i].childNodes[0].nodeValue.trim());
                }
            }

            // Send a request contains project names to get email addresses and
            // launch user's mail client with them using mailto scheme.
            htElement.welBtnWriteEmail.button('loading');

            $yobi.sendForm({
                "sURL"      : htVar.sURLMailList,
                "htOptForm": {"method":"POST"},
                "htData"    : aProjects,
                "sDataType" : "json",
                "fOnLoad"   : function(data) {
                    var form = $('<form>');
                    var mailto = 'mailto:';
                    for (var i = 0; i < data.length; i++) {
                        mailto += data[i] + ',';
                    }
                    console.log(mailto);
                    form.attr('method', 'POST');
                    form.attr('action', mailto);
                    form.attr('enctype', 'text/plain');
                    form.submit();
                    htElement.welBtnWriteEmail.button('reset');
                }
            });
        }

        /**
         * Add a project, which user types in #input-project element, into
         * #selected-projects div.
         */
        function _onClickSelectProject() {
            _appendProjectLabel(htElement.welInputProject.val());
            htElement.welInputProject.val("");
            return false;
        }

        /**
         * Same as _onClickSelectProject but triggered by pressing enter.
         *
         * @param {Object} oEvent
         */
        function _onKeyPressInputProject(oEvent) {
            if (oEvent.keyCode == 13) {
                _appendProjectLabel(htElement.welInputProject.val());
                htElement.welInputProject.val("");
                return false;
            }
        }

        /**
         * Make a project label by given name.
         *
         * @param {String} sName
         */
        function _createProjectLabel(sName) {
            var fOnClickUnselect = function() {
                welProject.remove();
            };

            var welProject = $('<span class="label label-info">' + sName + " </span>")
                .css('margin-right','5px')
                .append($('<a href="javascript:void(0)">x</a>')
                .click(fOnClickUnselect));

            return welProject;
        }

        /**
         * Append the given projects on #selected-projects div to show them.
         *
         * @param {Object} htProjects
         */
        function _appendProjectLabel(sTags) {
            htElement.welSelectedProjects.append(_createProjectLabel(sTags));
        }

        _init(htOptions);
    };

})("yobi.site.MassMail");
