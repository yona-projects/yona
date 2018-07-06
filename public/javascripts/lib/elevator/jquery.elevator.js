/*! jQuery Elevator - v1.0.6 - 2015
 * https://inzycle.github.com/jquery-elevator
 * Copyright (c) 2015 inZycle; Licensed MIT */

(function(factory) {
    if (typeof define === 'function' && define.amd) {
        return define(['jquery'], function($) {
            return factory($, window, document);
        });
    } else if (typeof exports === 'object') {
        return module.exports = factory(require('jquery'), window, document);
    } else {
        return factory(jQuery, window, document);
    }
})(function($, window, document) {

    'use strict';

        /**
         a classname for main elevator container.
         @property CLASS_DIV
         @type String
         @default 'jq-elevator'
         */
    var CLASS_DIV = 'jq-elevator',

        /**
         a classname for active elements.
         @property CLASS_ACTIVE
         @type String
         @default 'active'
         */
        CLASS_ACTIVE = 'active',

        /**
         a classname for touchable elevator version.
         @property CLASS_TOUCH
         @type String
         @default 'touch'
         */
        CLASS_TOUCH = 'touch',

        /**
         a classname for big elements.
         @property CLASS_BIG
         @type String
         @default 'jq-big'
         */
        CLASS_BIG = 'jq-big',

        /**
         a classname for middle elements.
         @property CLASS_MIDDLE
         @type String
         @default 'jq-mid'
         */
        CLASS_MIDDLE = 'jq-mid',

        /**
         a classname for small elements.
         @property CLASS_SMALL
         @type String
         @default 'jq-sml'
         */
        CLASS_SMALL = 'jq-sml',

        /**
         a classname for square shape.
         @property CLASS_CIRCLE
         @type String
         @default 'circle'
         */
        CLASS_CIRCLE = 'circle',

        /**
         a classname for rounded shape elements.
         @property CLASS_ROUNDED
         @type String
         @default 'rounded'
         */
        CLASS_ROUNDED = 'rounded',

        /**
         a classname for square shape elements.
         @property CLASS_SQUARE
         @type String
         @default 'square'
         */
        CLASS_SQUARE = 'square',

        /**
         a classname for glass translucent elements.
         @property CLASS_GLASS
         @type String
         @default 'glass'
         */
        CLASS_GLASS = 'glass',

        /**
         a classname for glass translucent elements.
         @property CLASS_AUTO_HIDE
         @type String
         @default 'auto-hide'
         */
        CLASS_AUTO_HIDE = 'auto-hide',

        /**
         a classname for right alignment.
         @property CLASS_ALIGN_TOP
         @type String
         @default 'align-top'
         */
        CLASS_ALIGN_TOP = 'align-top',

        /**
         a classname for right alignment.
         @property CLASS_ALIGN_RIGHT
         @type String
         @default 'align-right'
         */
        CLASS_ALIGN_RIGHT = 'align-right',

        /**
         a classname for bottom alignment.
         @property CLASS_ALIGN_BOTTOM
         @type String
         @default 'align-bottom'
         */
        CLASS_ALIGN_BOTTOM = 'align-bottom',

        /**
         a classname for left alignment.
         @property CLASS_ALIGN_LEFT
         @type String
         @default 'align-left'
         */
        CLASS_ALIGN_LEFT = 'align-left',

        /**
         a classname for tooltip item.
         @property CLASS_TITLE
         @type String
         @default 'jq-title'
         */
        CLASS_TITLE = 'jq-title',

        /**
         a classname for go to top item.
         @property CLASS_ITEM_TOP
         @type String
         @default 'jq-top'
         */
        CLASS_ITEM_TOP = 'jq-top',

        /**
         a classname for navigation items container.
         @property CLASS_ITEM_CONTAINER
         @type String
         @default 'jq-items'
         */
        CLASS_ITEM_CONTAINER = 'jq-items',

        /**
         a classname for navigation items container.
         @property CLASS_ITEM_CONTAINER
         @type String
         @default 'jq-items'
         */
        CLASS_ITEM_TOGGLE = 'jq-items-toggle',

        /**
         a classname for navigation item.
         @property CLASS_ITEM
         @type String
         @default 'jq-item'
         */
        CLASS_ITEM = 'jq-item',

        /**
         a classname for go to bottom item.
         @property CLASS_ITEM_BOTTOM
         @type String
         @default 'jq-bottom'
         */
        CLASS_ITEM_BOTTOM = 'jq-bottom',

        /**
         a classname for items with text.
         @property CLASS_ITEM_TEXT
         @type String
         @default 'jq-text'
         */
        CLASS_ITEM_TEXT = 'jq-text';

    var defaults = {

            /**
             a setting to enable go to top button.
             @property show_top
             @type Boolean
             @default true
             */
            show_top: true,

            /**
             a setting to enable go to bottom button.
             @property show_bottom
             @type Boolean
             @default true
             */
            show_bottom: true,

            /**
             a setting to establish an item which acts as top.
             @property item_top
             @type Object
             @default null
             */
            item_top: null,

            /**
             a setting to establish an item which acts as bottom.
             @property item_bottom
             @type Object
             @default null
             */
            item_bottom: null,

            /**
             a setting to establish the position of the elevator object.
             @property align
             @type String
             @default 'bottom right'
             */
            align: 'bottom right',

            /**
             a setting to establish the list of navigation items.
             @property navigation
             @type Object[]
             @default []
             */
            navigation: [],

            /**
             a setting to enable text for navigation items.
             @property navigation_text
             @type Boolean
             @default false
             */
            navigation_text: false,

            /**
             a setting to establish an extra margin for top and bottom sections.
             @property margin
             @type Number
             @default 100
             */
            margin: 100,

            /**
             a setting to establish the speed of animation.
             @property speed
             @type Number
             @default 1000
             */
            speed: 1000,

            /**
             a setting to enable glass translucent effect.
             @property glass
             @type Boolean
             @default false
             */
            glass: false,

            /**
             a setting to enable title tooltips.
             @property tooltips
             @type Boolean
             @default false
             */
            tooltips: false,

            /**
             a setting to establish the callback before general movement.
             @property onBeforeMove
             @type Function
             @default Function
             */
            onBeforeMove: function(){},

            /**
             a setting to establish the callback after general movement.
             @property onAfterMove
             @type Function
             @default Function
             */
            onAfterMove: function(){},

            /**
             a setting to establish the callback before top movement.
             @property onBeforeGoTop
             @type Function
             @default Function
             */
            onBeforeGoTop: function(){},

            /**
             a setting to establish the callback after top movement.
             @property onAfterGoTop
             @type Function
             @default Function
             */
            onAfterGoTop: function(){},

            /**
             a setting to establish the callback before bottom movement.
             @property onBeforeGoBottom
             @type Function
             @default Function
             */
            onBeforeGoBottom: function(){},

            /**
             a setting to establish the callback after bottom movement.
             @property onAfterGoBottom
             @type Function
             @default Function
             */
            onAfterGoBottom: function(){},

            /**
             a setting to establish the callback before section movement.
             @property onBeforeGoSection
             @type Function
             @default Function
             */
            onBeforeGoSection: function(){},

            /**
             a setting to establish the callback after section movement.
             @property onAfterGoSection
             @type Function
             @default Function
             */
            onAfterGoSection: function(){}

        },
        settings = {},
        $doc = $(document),
        $win = $(window),
        $div = $('<div>'),
        top_link,
        bottom_link;

    $.elevator = function(options) {

        settings = $.extend({}, defaults, options);

        function scrollTo(target, callback_before, callback_after) {

            settings.onBeforeMove.call();

            if (typeof callback_before === 'function') {
                callback_before.call();
            }

            $.when($('html,body').animate({
                scrollTop: target
            }, {
                duration: settings.speed
            })).then(function() {
                if (typeof callback_after === 'function') {
                    callback_after.call();
                }
                settings.onAfterMove.call();
            });

        }

        function createTopLink() {

            var anchor = '#',
                title = 'Move to Top',
                item_top = settings.item_top;

            if(item_top && typeof(item_top) == 'object') {
                if (!item_top.attr('id')) { item_top.attr('id', 'jq-TOP'); }
                anchor = '#' + item_top.attr('id');
                title = item_top.attr('title') ? item_top.attr('title') : ( item_top.attr('data-title') ? item_top.attr('data-title') : title );
            }

            top_link = $('<a>')
                .addClass(CLASS_ITEM_TOP)
                .addClass(CLASS_MIDDLE)
                .attr('href', anchor)
                .html('&#9650;');

            if(settings.tooltips){
                top_link.append(
                    $('<span>').addClass(CLASS_TITLE).text(title)
                );
            } else {
                top_link.attr('title', title);
            }

            top_link.on('click.' + CLASS_ITEM_TOP, function(e) {

                e.preventDefault();

                var pos;

                if(item_top && typeof(item_top) == 'object') {
                    pos = item_top.offset().top;
                } else {
                    pos = 0;
                }

                scrollTo(pos,settings.onBeforeGoTop,settings.onAfterGoTop);

            });

            $div.append(top_link);

        }

        function createNavigationLinks() {

            var anchor = '#',
                title = 'Go to Section',
                navigation = settings.navigation;

            var $container = $('<div>')
                .addClass(CLASS_ITEM_CONTAINER);

            $.each(navigation, function(key, val) {

                if (!$(val).attr('id')) {
                    $(val).attr('id', 'jq-' + parseInt($(val).offset().top));
                }

                var _anchor = anchor + $(val).attr('id');

                title = $(val).attr('title') ? $(val).attr('title') : ( $(val).attr('data-title') ? $(val).attr('data-title') : title );

                var item_link = $('<a>')
                    .addClass(CLASS_ITEM)
                    .addClass(CLASS_SMALL)
                    .attr('href', _anchor)
                    .html('&nbsp');

                if(settings.tooltips){
                    item_link.append(
                        $('<span>').addClass(CLASS_TITLE).text(title)
                    );
                } else {
                    item_link.attr('title', title);
                }

                if (settings.navigation_text){
                    item_link.html(title);
                    item_link.addClass(CLASS_ITEM_TEXT);
                }

                item_link.attr('data-section',$container.children().length + 1);

                $container.append(item_link);

            });

            $div.append($container);

            var items_togle = $('<a>')
                .addClass(CLASS_ITEM_TOGGLE)
                .attr('href', anchor)
                .html('&#9679;');

            items_togle.on('click.' + CLASS_ITEM_TOGGLE, function(e) {

                e.preventDefault();

                if ( $(this).hasClass(CLASS_ACTIVE) ){

                    $(this).removeClass(CLASS_ACTIVE);

                    $container.hide();

                } else {

                    $(this).addClass(CLASS_ACTIVE);

                    $container.show();

                }

            });

            $div.append(items_togle);

            $(document).on('click', '.' + CLASS_ITEM, function(e) {
                e.preventDefault();
                var _item = $($(this).attr('href'));
                scrollTo(_item.offset().top,settings.onBeforeGoSection,settings.onAfterGoSection);
            });

        }

        function createBottomLink() {

            var anchor = '#',
                title = 'Move to Bottom',
                item_bottom = settings.item_bottom;

            if(item_bottom && typeof(item_bottom) == 'object') {
                if (!item_bottom.attr('id')) { item_bottom.attr('id', 'jq-BOTTOM'); }
                anchor = '#' + item_bottom.attr('id');
                title = item_bottom.attr('title') ? item_bottom.attr('title') : ( item_bottom.attr('data-title') ? item_bottom.attr('data-title') : title );
            }

            bottom_link = $('<a>')
                .addClass(CLASS_ITEM_BOTTOM)
                .addClass(CLASS_MIDDLE)
                .attr('href', anchor)
                .html('&#9660;');

            if(settings.tooltips){
                bottom_link.append(
                    $('<span>').addClass(CLASS_TITLE).text(title)
                );
            } else {
                bottom_link.attr('title', title);
            }

            bottom_link.on('click.' + CLASS_ITEM_BOTTOM, function(e) {

                e.preventDefault();

                var pos = 0;

                if(item_bottom && typeof(item_bottom) == 'object') {
                    pos = item_bottom.offset().top + (item_bottom.outerHeight(true) - $win.height());
                } else {
                    pos = $doc.height();
                }

                scrollTo(pos,settings.onBeforeGoBottom,settings.onAfterGoBottom);

            });

            $div.append(bottom_link);

        }

        function atTop() {

            var item_top = settings.item_top,
                ret = null;

            if(item_top && typeof(item_top) == 'object') {
                ret = $win.scrollTop() <= item_top.offset().top + settings.margin;
            } else {
                ret = $win.scrollTop() <= settings.margin;
            }

            return ret;
        }

        function atBottom() {

            var item_bottom = settings.item_bottom,
                ret = null;

            if(item_bottom && typeof(item_bottom) == 'object') {
                ret = $win.scrollTop() >= item_bottom.offset().top - $win.height() - settings.margin;
            } else {
                ret = $win.scrollTop() + $win.height() >= $doc.height() - settings.margin;
            }

            return ret;

        }

        function setAlign() {

            var positions = settings.align.split(' ');

            if (positions.indexOf('top') >= 0) {
                $div.addClass(CLASS_ALIGN_TOP);
            }

            if (positions.indexOf('bottom') >= 0) {
                $div.addClass(CLASS_ALIGN_BOTTOM);
            }

            if (positions.indexOf('left') >= 0) {
                $div.addClass(CLASS_ALIGN_LEFT);
            }

            if (positions.indexOf('right') >= 0) {
                $div.addClass(CLASS_ALIGN_RIGHT);
            }

        }

        function setShape() {

            var shape = settings.shape;

            switch (shape) {
                case 'square':
                    $div.addClass(CLASS_SQUARE);
                    break;
                case 'rounded':
                    $div.addClass(CLASS_ROUNDED);
                    break;
                case 'circle':
                    $div.addClass(CLASS_CIRCLE);
                    break;
                default:
                    $div.addClass(CLASS_CIRCLE);
            }

        }

        function setStyle() {

            if ( settings.glass ) {
                $div.addClass(CLASS_GLASS);

            }

            if ( settings.auto_hide ) {
                $div.addClass(CLASS_AUTO_HIDE);
            }

        }

        function moveTo(section){

            var _section = parseInt(section);

            if ( isNaN(_section) ){

                switch (section) {

                    case 'top':
                        $('.' + CLASS_ITEM_TOP).trigger('click');
                        break;

                    case 'bottom':
                        $('.' + CLASS_ITEM_BOTTOM).trigger('click');
                        break;

                    default:

                        return false;

                }

            } else if ( $.isNumeric(_section) && (+_section === _section && !(_section % 1)) && _section > 0 && _section <= $('.' + CLASS_ITEM_CONTAINER).children().length ){

                $('.' + CLASS_ITEM +'[data-section="' + _section + '"]').trigger('click');

            } else {

                return false;

            }

        }

        function init() {

            $div.addClass(CLASS_DIV);

            if ( 'ontouchstart' in window || navigator.msMaxTouchPoints ){ $div.addClass(CLASS_TOUCH); }

            setAlign();
            setShape();
            setStyle();

            $div.html('');

            if (settings.show_top) {
                createTopLink();
            }

            if (settings.navigation.length > 0) {
                createNavigationLinks();
            }

            if (settings.show_bottom) {
                createBottomLink();
            }

            if (atTop()) {
                if (settings.show_top) { top_link.removeClass(CLASS_MIDDLE).addClass(CLASS_SMALL); }
                if (settings.show_bottom) { bottom_link.removeClass(CLASS_MIDDLE).addClass(CLASS_BIG); }
            } else if (atBottom()) {
                if (settings.show_top) { top_link.removeClass(CLASS_MIDDLE).addClass(CLASS_BIG); }
                if (settings.show_bottom) { bottom_link.removeClass(CLASS_MIDDLE).addClass(CLASS_SMALL); }
            }

            $doc.scroll(function() {

                if (atTop()) {
                    if (settings.show_top) { top_link.removeClass(CLASS_MIDDLE).addClass(CLASS_SMALL); }
                    if (settings.show_bottom) { bottom_link.removeClass(CLASS_MIDDLE).addClass(CLASS_BIG); }
                } else if (atBottom()) {
                    if (settings.show_top) { top_link.removeClass(CLASS_MIDDLE).addClass(CLASS_BIG); }
                    if (settings.show_bottom) { bottom_link.removeClass(CLASS_MIDDLE).addClass(CLASS_SMALL); }
                } else {
                    if (settings.show_top) { top_link.removeClass(CLASS_BIG).removeClass(CLASS_SMALL).addClass(CLASS_MIDDLE); }
                    if (settings.show_bottom) { bottom_link.removeClass(CLASS_BIG).removeClass(CLASS_SMALL).addClass(CLASS_MIDDLE); }
                }
            });

            $('body').append($div);

            $('body').on('click','*[data-elevator]',function(e){

                e.preventDefault();

                moveTo( $(this).attr('data-elevator') );

            });

        }

        init();

        function _class(){

            /**
             Reset the alignment
             @method reset_align
             @param align {String} A setting to reestablish the position of the elevator object.
             @example
             elevator.reset_align('right bottom');
             */
            this.reset_align = function(align){

                var positions = align.split(' ');

                $div.removeClass(CLASS_ALIGN_TOP)
                    .removeClass(CLASS_ALIGN_BOTTOM)
                    .removeClass(CLASS_ALIGN_LEFT)
                    .removeClass(CLASS_ALIGN_RIGHT);

                if (positions.indexOf('top') >= 0) {
                    $div.addClass(CLASS_ALIGN_TOP);
                }

                if (positions.indexOf('bottom') >= 0) {
                    $div.addClass(CLASS_ALIGN_BOTTOM);
                }

                if (positions.indexOf('left') >= 0) {
                    $div.addClass(CLASS_ALIGN_LEFT);
                }

                if (positions.indexOf('right') >= 0) {
                    $div.addClass(CLASS_ALIGN_RIGHT);
                }

                settings.align = align;

            };

            /**
             Reset the shape
             @method reset_shape
             @param shape {String} A setting to reestablish the shape of the elevator object.
             @example
             elevator.reset_shape('circle');
             */
            this.reset_shape = function(shape){

                $div.removeClass(CLASS_SQUARE)
                    .removeClass(CLASS_ROUNDED)
                    .removeClass(CLASS_CIRCLE);

                switch (shape) {
                    case 'square':
                        $div.addClass(CLASS_SQUARE);
                        break;
                    case 'rounded':
                        $div.addClass(CLASS_ROUNDED);
                        break;
                    case 'circle':
                        $div.addClass(CLASS_CIRCLE);
                        break;
                    default:
                        $div.addClass(CLASS_CIRCLE);
                }

                settings.shape = shape;

            };

            /**
             Reset the movement speed
             @method reset_speed
             @param speed {Number} A setting to reestablish the speed of the elevator object.
             @example
             elevator.reset_speed(1000);
             */
            this.reset_speed = function(speed){

                settings.speed = speed;

            };

            /**
             Reset the glass translucent effect
             @method reset_glass
             @param glass {Boolean} A setting to reestablish the speed of the elevator object.
             @example
             elevator.reset_glass(true);
             */
            this.reset_glass = function(glass){

                if (glass){
                    $div.addClass(CLASS_GLASS)
                } else {
                    $div.removeClass(CLASS_GLASS)
                }

                settings.glass = glass;

            };

            /**
             Reset the auto hidden status of the navigation items
             @method auto_hide
             @param hide {Boolean} A setting to reestablish the auto hidden status of the navigation items.
             @example
             elevator.auto_hide(true);
             */
            this.auto_hide = function(auto_hide){

                if (auto_hide){
                    $div.addClass(CLASS_AUTO_HIDE)
                } else {
                    $div.removeClass(CLASS_AUTO_HIDE)
                }

                settings.auto_hide = auto_hide;

            };

            /**
             Reset the auto hidden status of the navigation items
             @method auto_hide
             @param hide {Boolean} A setting to reestablish the auto hidden status of the navigation items.
             @example
             elevator.auto_hide(true);
             */
            this.move_to = function(section){

                moveTo(section);

            };

            /**
             Return the actual settings
             @method get_settings
             @example
             elevator.get_settings();
             */
            this.get_settings = function(){

                return settings;

            };

            /**
             Destroy the elevator element
             @method destroy
             @example
             elevator.destroy();
             */
            this.destroy = function(){

                top_link.off('click.' + CLASS_ITEM_TOP);
                bottom_link.off('click.' + CLASS_ITEM_BOTTOM);
                $(document).off('click', '.' + CLASS_ITEM);

                $div = $('<div>');

                $('body').find('.' + CLASS_DIV).remove();

                return null;

            };

        }

        return new _class()

    };

});