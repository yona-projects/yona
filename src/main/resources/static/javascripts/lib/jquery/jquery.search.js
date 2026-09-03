/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Insanehong
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

!function ($) {

  "use strict"; // jshint ;_;

  var Search = function (element, options) {
    this.$element = $(element);
    this.options = $.extend({}, $.fn.search.defaults, options);
    this.item = this.options.item || this.$element.data('items');
    this.items = $('[data-item="'+this.item+'"]');
    this.lookup();
    this.searchTimer;
  }

  Search.prototype = {
    constructor: Search
    , lookup : function() {
        this.$element.on('keyup', $.proxy(this.keyup, this));
        this.$element.on('keydown', $.proxy(this.kedown, this));
    }
    , keyup : function() {
        this.searchTimer = setTimeout(
            $.proxy(this.search, this), 
            this.options.delay, 
            this.$element.val().toLowerCase()
        );
    }
    , keydown : function() {
        clearTimeout(this.searchTimer);
    }  
    , search : function(filter) {
        var $this = this;

        if(!$.trim(filter)) {
            this.items.show();
            return ;        
        }

        this.items.each(function(index, item) {
            ($this.matcher(filter, item)) ? $(item).show() : $(item).hide();
        });
    } 
    , matcher : function(filter, item) {
        return ($(item).data('value').toLowerCase().indexOf(filter) !== -1);
    }
    
  }

  $.fn.search = function ( option ) {
    return this.each(function () {
      var $this = $(this)
        , data = $this.data('search')
        , options = typeof option == 'object' && option
      if (!data) $this.data('search', (data = new Search(this, options)))
      if (typeof option == 'string') data[option]()
    })
  }

  $.fn.search.defaults ={
    items : "",
    delay : 200
  }


  $(document).on('focus' , '[data-toggle="item-search"]', function(e) {
    var $this = $(this);
    if ($this.data('search')) return;
    $this.search($this.data());
  });

}(window.jQuery);
