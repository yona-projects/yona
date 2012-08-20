var NForge = function () {
    var proto,
      PERIOD = '.',
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

      require : function () {
        var args = Array.prototype.slice.call(arguments),
          modules = args.shift(),
          _modules,
          defaultInitFn = 'init',
          loadFn = function(module) {
            module = module.replace('nforge.','');
            if (module.indexOf(PERIOD) < 0) {
              var newFunction = new nforge[module]();
              return newFunction.init.apply(nforge,args);
            }
            _modules = module.split(PERIOD);
            var _tObj = nforge._objectDeepProp('get', module);
            if (typeof _tObj === 'object') {
              return _tObj;
            } else if (typeof _tObj === 'undefined') {
              var mLength = _modules.length - 1;
              defaultInitFn = _modules[mLength];
              module = _modules.slice(0, mLength).join('.');
            }
            return commandFn(module);
          },
          commandFn = function (module) {
            var _obj = nforge._objectDeepProp('get', module);
            if (typeof _obj === 'function') {
              _obj = new _obj();
              if (_obj.hasOwnProperty(defaultInitFn)) {
                _obj = _obj[defaultInitFn].apply(nforge, args);
                nforge._objectDeepProp('add', module, _obj);
                return _obj;
              }
            }
          };
        if (!$.isArray(modules)) {
          return loadFn(modules);
        }else{
          $.each(modules, function (idx, module) {
            loadFn(module);
          });
          return this;
        }
      },

      _objectDeepProp : function (act, name) {
        var target = nforge,
          _name = name;

        if (name.indexOf(PERIOD) > 0) {
          var i,
            isNoThere = true;
          names = name.split(PERIOD);
          for (i = 0; i < names.length - 1; i++) {
            if (target.hasOwnProperty([names[i]])) {
              target = target[names[i]];
              isNoThere = false;
            }
          }
          if (isNoThere) {
            return false;
          }
          _name = names[names.length - 1];
        }
        return (act === 'check' ? target.hasOwnProperty(_name) : (act === 'get' ? target[_name] || undefined : (target[_name] = arguments[2])));
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
  }
  ;

var nforge = new NForge();
jQuery(function ($) {
  nforge.init();
});