jQuery.fn.highlight = function(language){ // may be jQuery.fn.hljs ?
  this.each(function(){
    var q = jQuery(this).first();
    var r = hljs(q.text(), language);
    if(!q.hasClass(r.language)){ q.addClass(r.language); }
    q.html(r.value);
  });
  return(this);
}
