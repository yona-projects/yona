/******/ (function(modules) { // webpackBootstrap
    /******/ 	// The module cache
    /******/ 	var installedModules = {};
    /******/
    /******/ 	// The require function
    /******/ 	function __webpack_require__(moduleId) {
        /******/
        /******/ 		// Check if module is in cache
        /******/ 		if(installedModules[moduleId])
        /******/ 			return installedModules[moduleId].exports;
        /******/
        /******/ 		// Create a new module (and put it into the cache)
        /******/ 		var module = installedModules[moduleId] = {
            /******/ 			exports: {},
            /******/ 			id: moduleId,
            /******/ 			loaded: false
            /******/ 		};
        /******/
        /******/ 		// Execute the module function
        /******/ 		modules[moduleId].call(module.exports, module, module.exports, __webpack_require__);
        /******/
        /******/ 		// Flag the module as loaded
        /******/ 		module.loaded = true;
        /******/
        /******/ 		// Return the exports of the module
        /******/ 		return module.exports;
        /******/ 	}
    /******/
    /******/
    /******/ 	// expose the modules object (__webpack_modules__)
    /******/ 	__webpack_require__.m = modules;
    /******/
    /******/ 	// expose the module cache
    /******/ 	__webpack_require__.c = installedModules;
    /******/
    /******/ 	// __webpack_public_path__
    /******/ 	__webpack_require__.p = "";
    /******/
    /******/ 	// Load entry module and return exports
    /******/ 	return __webpack_require__(0);
    /******/ })
/************************************************************************/
/******/ ([
    /* 0 */
    /***/ function(module, exports, __webpack_require__) {

        /* WEBPACK VAR INJECTION */(function($) {"use strict";
            __webpack_require__(2);
            var GFMTaskList = (function () {
                function GFMTaskList($element, settings) {
                    var _this = this;
                    this.incomplete = "[ ]";
                    this.complete = "[x]";
                    this.incompletePattern = RegExp(this.escapePattern(this.incomplete));
                    this.completePattern = RegExp(this.escapePattern(this.complete));
                    this.itemPattern = RegExp("^(?:\\s*[-+*]|(?:\\d+\\.))?\\s*(" + this.escapePattern(this.complete) + "|" + this.escapePattern(this.incomplete) + ")(?=\\s)");
                    this.codeFencesPattern = /^`{3}(?:\s*\w+)?[\S\s].*[\S\s]^`{3}$/mg;
                    this.itemsInParasPattern = RegExp("^(" + this.escapePattern(this.complete) + "|" + this.escapePattern(this.incomplete) + ").+$", 'g');
                    this.$element = $element;
                    this.$markdownContainer = this.$element.find(settings.markdownContainer);
                    this.$renderedContainer = this.$element.find(settings.renderedContainer);
                    this.onUpdate = function (event) {
                        var update = _this.updateTaskList($(event.target));
                        if (update)
                            settings.onUpdate(update);
                    };
                    this.$renderedContainer.on('change', '.task-list-item-checkbox', this.onUpdate);
                    this.enable();
                }
                GFMTaskList.prototype.destroy = function () {
                    this.$renderedContainer.off('change', '.task-list-item-checkbox', this.onUpdate);
                };
                GFMTaskList.prototype.enable = function () {
                    this.$renderedContainer
                        .find('.task-list-item').addClass('enabled')
                        .find('.task-list-item-checkbox').attr('disabled', null);
                    this.$element.trigger('tasklist:enabled');
                };
                GFMTaskList.prototype.disable = function () {
                    this.$renderedContainer
                        .find('.task-list-item').removeClass('enabled')
                        .find('.task-list-item-checkbox').attr('disabled', 'disabled');
                    this.$element.trigger('tasklist:disabled');
                };
                GFMTaskList.prototype.updateTaskListItem = function (source, itemIndex, checked) {
                    var clean = source
                        .replace(/\r/g, '')
                        .replace(this.codeFencesPattern, '')
                        .replace(this.itemsInParasPattern, '')
                        .split("\n");
                    var index = 0;
                    var updatedMarkdown = [];
                    for (var _i = 0, _a = source.split('\n'); _i < _a.length; _i++) {
                        var line = _a[_i];
                        if (clean.indexOf(line) >= 0 && this.itemPattern.test(line)) {
                            index++;
                            if (index === itemIndex) {
                                if (checked) {
                                    line = line.replace(this.incompletePattern, this.complete);
                                }
                                else {
                                    line = line.replace(this.completePattern, this.incomplete);
                                }
                            }
                        }
                        updatedMarkdown.push(line);
                    }
                    return updatedMarkdown.join('\n');
                };
                ;
                GFMTaskList.prototype.updateTaskList = function ($item) {
                    var index = 1 + this.$renderedContainer.find('.task-list-item-checkbox').index($item);
                    var checked = $item.prop('checked');
                    var event = $.Event('tasklist:change');
                    this.$element.trigger(event, [index, checked]);
                    if (event.isDefaultPrevented())
                        return;
                    var updatedMarkdown = this.updateTaskListItem(this.$markdownContainer.val(), index, checked);
                    this.$markdownContainer.val(updatedMarkdown);
                    this.$markdownContainer.trigger('change');
                    this.$markdownContainer.trigger('tasklist:changed', [index, checked]);
                    return updatedMarkdown;
                };
                ;
                GFMTaskList.prototype.escapePattern = function (str) {
                    return str
                        .replace(/([\[\]])/g, '\\$1')
                        .replace(/\s/, '\\s')
                        .replace('x', '[xX]');
                };
                return GFMTaskList;
            }());
            var jQuery;
            (function (jQuery) {
                $.fn.gfmTaskList = function (action) {
                    var instance = $.data(this, GFMTaskList.name);
                    if (typeof action === 'string') {
                        if (!instance) {
                            throw new Error("Must construct gfmTaskList before calling methods on it.");
                        }
                        instance[action]();
                        return this;
                    }
                    var settings;
                    if (typeof action === 'object') {
                        settings = action;
                        var requiredKeys = ['renderedContainer', 'markdownContainer'];
                        var keys_1 = Object.keys(settings);
                        requiredKeys.forEach(function (requiredKey) {
                            if (keys_1.indexOf(requiredKey) === -1) {
                                throw new Error("Missing key '" + requiredKey + "'");
                            }
                        });
                        action = undefined;
                    }
                    else {
                        throw new Error("Must pass an object to $.fn.gfmTaskList().");
                    }
                    if (instance)
                        instance.destroy();
                    instance = $.data(this, GFMTaskList.name, new GFMTaskList(this, settings));
                    return this;
                };
            })(jQuery || (jQuery = {}));

            /* WEBPACK VAR INJECTION */}.call(exports, __webpack_require__(1)))

        /***/ },
    /* 1 */
    /***/ function(module, exports) {

        module.exports = jQuery;

        /***/ },
    /* 2 */
    /***/ function(module, exports) {

        // removed by extract-text-webpack-plugin

        /***/ }
    /******/ ]);
//# sourceMappingURL=gfm-task-list.js.map