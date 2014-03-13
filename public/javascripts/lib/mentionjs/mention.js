// Copyright (c) 2013 Jacob Kelley
// Copyright (C) 2013 Bijan Ebrahimi <bijanebrahimi@lavabit.com>
//      overriding delimiter
//      optional key/name/image object variable name 
//      removed already mentions objects from emptyQuery result
//      fixed unclosed mention menu bug
// 
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
// 
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

;(function($) {
    $.fn.extend({
        mention: function(options) {
            this.opts = {
                users: [],
                delimiter: '@',
                delimiters: '',
                sensitive: true,
                emptyQuery: false,
                key: 'username',
                name: 'name',
                image: 'image',
                queryBy: [],
                suffix : ' ',
                useSuffix : true,
                typeaheadOpts: {}
            };

            var settings = $.extend({}, this.opts, options),
                _checkDependencies = function() {
                    if (typeof $ == 'undefined') {
                        throw new Error("jQuery is Required");
                    }
                    else {
                        if (typeof $.fn.typeahead == 'undefined') {
                            throw new Error("Typeahead is Required");
                        }
                    }
                    return true;
                },
                _extractCurrentQuery = function(query, caratPos) {
                    for (var i = caratPos; i >= 0; i--) {
                        if (query[i] == settings.delimiter) {
                            break;
                        }
                    }
                    return query.substring(i, caratPos);
                },
                _matcher = function(itemProps) {
                    // default value for queryBy is [settings.key], 
                    if(settings.queryBy.length == 0)
                        settings.queryBy = [settings.key]
                    
                    // local variable
                    var current_delimiter = (itemProps['delimiter'] ? itemProps['delimiter'] : settings.delimiter),
                        itemKey = itemProps[settings.key].toLowerCase(),
                        q = (this.query.toLowerCase()),
                        caratPos = this.$element[0].selectionStart,
                        lastChar = q.slice(caratPos-1,caratPos);
                    
                    // list all the usernames already in text (in lower case)
                    var usernames = (q.toLowerCase().match(new RegExp(current_delimiter + '\[^\\t]+', "g"))||[]).map(function(b){ return b.toLowerCase(); })

                    // query only the word between cursor and the first space/delimiter behind it
                    var q = (q.substring(0, caratPos).match(new RegExp('([^ '+settings.delimiters+']+)$')) || [''])[0]
                    
                    // in emptyQuery, try to list all but those already selected
                    if(settings.emptyQuery){
                        if(lastChar==current_delimiter){
		                    if (usernames.indexOf(current_delimiter+itemKey)==-1)
                                return true
	                    }
                    }
                    
                    // at this moment, don't bother to search empty query
                    if(q == '') return false
                    
                    // list possible answers
                    for (var i in settings.queryBy) {
                        if (itemProps[settings.queryBy[i]]) {
                            var item = itemProps[settings.queryBy[i]].toLowerCase()
                            if(q.trim().toLowerCase().substring(1)==itemProps[settings.key].toLowerCase())
                                return false
                            for (var j = 0; j < usernames.length; j++) {
                                // remove sqaure brackets in item
                                var modifiedItem = item.replace(new RegExp("\\[.*?\\]","g"),"");
                                var username = (usernames[j].substring(1)).toLowerCase(),
                                    re = new RegExp(current_delimiter + modifiedItem, "g"),
                                    used = ((q.toLowerCase()).match(re));
                                if (item.indexOf(username) != -1 && used === null && usernames.indexOf(current_delimiter+itemProps[settings.key].toLowerCase()) == -1) {
                                    return true;
                                }
                            }
                        }
                    }
                },
                _updater = function(item, delimiter) {
                    var data = this.query,
                        caratPos = this.$element[0].selectionStart,
                        i;
                    
                    for (i = caratPos; i >= 0; i--) {
                        if (data[i] == delimiter) {
                            break;
                        }
                    }
                    var replace = data.substring(i, caratPos),
                    	textBefore = data.substring(0, i),
                    	textAfter = data.substring(caratPos),
                    	data = textBefore + delimiter + item + textAfter;
                    	
                    this.tempQuery = data;

                    return (settings.useSuffix) ? data+settings.suffix : data;
                },
                _sorter = function(items) {
                    if (items.length && settings.sensitive) {
                        var currentUser = _extractCurrentQuery(this.query, this.$element[0].selectionStart).substring(1),
                            i, len = items.length,
                            priorities = {
                                highest: [],
                                high: [],
                                med: [],
                                low: []
                            }, finals = [];
                        if (currentUser.length == 1) {
                            for (i = 0; i < len; i++) {
                                var currentRes = items[i];

                                if ((currentRes[settings.key][0] == currentUser)) {
                                    priorities.highest.push(currentRes);
                                }
                                else if ((currentRes[settings.key][0].toLowerCase() == currentUser.toLowerCase())) {
                                    priorities.high.push(currentRes);
                                }
                                else if (currentRes[settings.key].indexOf(currentUser) != -1) {
                                    priorities.med.push(currentRes);
                                }
                                else {
                                    priorities.low.push(currentRes);
                                }
                            }
                            for (i in priorities) {
                                var j;
                                for (j in priorities[i]) {
                                    finals.push(priorities[i][j]);
                                }
                            }
                            return finals;
                        }
                    }
                    return items;
                },
                _render = function(items) {
                    var that = this;
                    items = $(items).map(function(i, item) {

                        i = $(that.options.item).attr('data-value', item[settings.key]);

                        var _linkHtml = $('<div />');

                        if (item[settings.image]) {
                            _linkHtml.append('<img class="mention_image" src="' + item[settings.image] + '">');
                        }
                        if (item[settings.name]) {
                            _linkHtml.append('<b class="mention_name">' + item[settings.name] + '</b>');
                        }
                        if (item[settings.key]) {
                            _linkHtml.append('<span class="mention_username"> ' + (item['delimiter'] ? item['delimiter'] : settings.delimiter) + item[settings.key] + '</span>');
                        }
                        i.find('a').attr('data-delimiter', (item['delimiter'] ? item['delimiter'] : settings.delimiter)).html(that.highlighter(_linkHtml.html()));
                        return i[0];
                    });
                    items.first().addClass('active');
                    this.$menu.html(items);
                    return this;
                };

            // fill settings.delimiters if empty
            if(settings.delimiters.length==0){
                settings.delimiters = settings.delimiter
                for(var i=0; i<settings.users.length; i++){
                    if(settings.users[i].delimiter)
                        if(settings.delimiters.indexOf(settings.users[i].delimiter)==-1)
                            settings.delimiters += settings.users[i].delimiter
                }
            }

            $.fn.typeahead.Constructor.prototype.render = _render;
            $.fn.typeahead.Constructor.prototype.select = function () {
                var val = this.$menu.find('.active').attr('data-value'),
                delimiter = this.$menu.find('.active').find('a').attr('data-delimiter')
                this.$element
                    .val(this.updater(val, delimiter))
                    .change()
                return this.hide();
            }
            $.fn.typeahead.Constructor.prototype.updater = function (item, delimiter) {
                return item
            }

            return this.each(function() {
                var _this = $(this);
                if (_checkDependencies()) {
                    _this.typeahead($.extend({
                        source: settings.users,
                        matcher: _matcher,
                        updater: _updater,
                        sorter: _sorter
                    }, settings.typeaheadOpts));
                }
            });
        }
    });
})(jQuery);
