var NForge = function () {
  var proto,
    nforge = this;
  proto = {
    init : function () {

    },

    log : function () {
      if (typeof console !== 'undefined' && typeof console.log !== 'undefined') {
        console.log.apply(nforge, arguments);
      }
    },

    dir : function () {
      if (typeof console !== 'undefined' && typeof console.dir !== 'undefined') {
        console.dir.apply(nforge, arguments);
      }
    },

    namespace : function () {
      var names,
        i = 0,
        j,
        obj = this,
        arg;
      for (; i < arguments.length; i++) {
        arg = arguments[i];
        if (arg.indexOf(PERIOD) > 0) {
          names = arg.split(PERIOD);
          for (j = (names[0] === 'nforge') ? 1 : 0; j < names.length; j++) {
            obj[names[j]] = obj[names[j]] || {};
            obj = obj[names[j]];
          }
        } else {
          obj[arg] = obj[arg] || {};
        }
      }
      return obj;
    },

    stopEvent : function (e) {
      if (e) {
        e.cancelBubble = true;
        e.returnValue = false;

        if (e.stopPropagation) {
          e.stopPropagation();
        }
        if (e.preventDefault) {
          e.preventDefault();
        }
      }
    },

    loadJavascript : function (src, callback) {
      var script,
        id = arguments[2] || +(new Date());
      if (document.getElementById(id)) {
        return;
      }
      script = document.createElement('script');
      script.type = 'text/javascript';
      script.async = true;
      script.id = id;
      script.src = src;
      if (script.onload === undefined) {   //for ie.
        script.onreadystatechange = function () {
          if (this.readyState === 'complete' || this.readyState === 'loaded') {
            nforge.callback(callback);
          }
        };
      } else {
        script.onload = callback;
      }
      document.getElementsByTagName('head')[0].appendChild(script);
    },

    callback : function (callback) {
      if (typeof callback === 'function') {
        return callback.apply(this, Array.prototype.slice.call(arguments, 1));
      }
      return nforge;
    }
  }; //end of proto.

  nforge = NForge.prototype = proto;
  return nforge;
};

var nforge = new NForge();
jQuery(function ($) {
  nforge.init();
});