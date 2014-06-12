/* =============================================================
 * bootstrap-better-typeahead.js v1.0.0 by Philipp Nolte
 * https://github.com/ptnplanet/Bootstrap-Better-Typeahead
 * =============================================================
 * This plugin makes use of twitter bootstrap typeahead
 * http://twitter.github.com/bootstrap/javascript.html#typeahead
 *
 * Bootstrap is licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 * ============================================================ */

// **************************************************
// Yobi modifications by Yi EungJun
// http://github.com/nforge/yobi/
//
// Modifications are tagged with "yobi"
// **************************************************

!function($) {

    "use strict";

    /**
     * The better typeahead plugin will extend the bootstrap typeahead plugin and provide the abbility to set the
     * maxLength option to zero. The tab keyup event handler had to be moved to the keydown event handler, so that
     * the full list of available items is shown on tab-focus and the original behaviour is preserved as best as
     * possible.
     *
     * @type {object}
     */
    var BetterTypeahead = {

        lookup: function(event) {
            var items;

            // Now supports empty queries (eg. with a length of 0).
            this.query = this.$element.val() || '';

            if (this.query.length < this.options.minLength) {
                return this.shown ? this.hide() : this;
            }

            items = $.isFunction(this.source) ? this.source(this.query, $.proxy(this.process, this)) : this.source;

            return items ? this.process(items) : this;
        }

        , process: function (items) {
            var that = this;

            items = $.grep(items, function (item) {
                return that.matcher(item);
            });

            items = this.sorter(items);

            if (!items.length) {
                return this.shown ? this.hide() : this;
            }

            // yobi: If-clause to make this work only if query's length is more
            // than 0, has been removed from the original version.
            items = items.slice(0, this.options.items);

            return this.render(items).show();
        }

        , render: function (items) {
            var that = this

            items = $(items).map(function (i, item) {
                i = $(that.options.item).attr('data-value', item);
                i.find('a').html(that.highlighter(item));
                return i[0];
            });

            if (this.query.length > 0) {
                items.first().addClass('active');
            }

            this.$menu.html(items);
            return this;
        }

        , keydown: function (e) {
            this.suppressKeyPressRepeat = ~$.inArray(e.keyCode, [40,38,9,13,27]);

            this.cachedKeyCode = e.KeyCode;
            // Added tab handler. Tabbing out of the input (thus blurring).
            if (e.keyCode === 9) { // tab
                if (!this.shown) return;
                this.select();
            } else {
                this.move(e);
            }
        }

        , keyup: function (e) {
            switch(e.keyCode) {
                case 40: // down arrow
                case 38: // up arrow
                case 16: // shift
                case 17: // ctrl
                case 18: // alt
                    break;

                // Moved tab handler to keydown.
                case 13: // enter
                    /**
                     * If you type the Enter key while Hangul(other unfinished character) input in MacOS IME, 
                     * there is a problem that is occurring twice keyup (13) event .
                     * 
                     * - Mac
                     * keydown : 229
                     * keyup : 13
                     * keydown : 13
                     * keyup : 13
                     * 
                     * - Windows
                     * keydown : 229
                     * keydown : 13
                     * keyup : 13
                     * 
                     * In order to solve this problem , keyCode(13) after keyCode(229) ignore.
                     */
                    if (!this.shown && this.cachedKeyCode != 229) return;
                    this.select();
                    break;

                case 27: // escape;
                    if (!this.shown) return;
                    this.hide();
                    break;

                default:
                    this.lookup();
            }

            e.stopPropagation();
            e.preventDefault();
        }

        , focus: function(e) {
            this.focused = true;

            if (!this.mousedover) {
                this.lookup(e);
            }
        }
    };

    $.extend($.fn.typeahead.Constructor.prototype, BetterTypeahead);

}(window.jQuery);
