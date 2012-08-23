nforge.namespace("milestone");

nforge.milestone.common = function() {
  var $validates;
  return {
    init : function() {
      $validates = $('.validate');
      return this;
    },

    validate : function() {
      $.each($validates, function(idx, validate){
        var $validate  = $(this);
        /* @TODO put validate code */
      });
      return true;
    },

    requireField : function() {

    },

    dateField : function() {

    }
  };
};

nforge.milestone.manage = function() {
  var that,
    common =  nforge.require('milestone.common');
  that = {
    init : function() {
      $('.save').click(that.save);
    },

    save : function(e) {
      if(!common.validate()) {
        return false;
      }
    }
  };
  return that;
};


