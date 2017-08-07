/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function yonaAssgineeModule(findAssignableUsersApiUrl, updateAssgineesApiUrl, message){
  function formatter(result){
    if(!result.avatarUrl){
      return '<div>' + result.name + '</div>';
    }

    // Template text
    var tplUserItem = $("#tplSelect2FormatUser").text() || '<div class="usf-group" title="${name} ${loginId}">' +
        '<span class="avatar-wrap smaller"><img src="${avatarURL}" width="20" height="20"></span>' +
        '<strong class="name">${name}</strong>' +
        '<span class="loginid">${loginId}</span></div>';

    var formattedResult = $.tmpl(tplUserItem, {
      "avatarURL": result.avatarUrl,
      "name"     : result.name,
      "loginId"  : result.loginId
    });

    return formattedResult;
  }

  function matcher(term, formattedResult, result){
    term = term.toLowerCase();
    formattedResult = formattedResult.toLowerCase();

    var loginId = (typeof result.loginId !== "undefined") ? result.loginId.toLowerCase() : "";

    return (loginId.indexOf(term) > -1) || (formattedResult.indexOf(term) > -1);
  }

  var $assignee = $("#assignee");
  $assignee.select2({
    minimumInputLength: 0,
    id: function(obj) {
      return obj.loginId; // use slug field for id
    },
    ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
      url: findAssignableUsersApiUrl,
      dataType: 'json',
      quietMillis: 300,
      data: function (term, page) {
        return {
          query: term, // search term
        };
      },
      results: function (data, page) { // parse the results into the format expected by Select2.
        // since we are using custom formatting functions we do not need to alter the remote JSON data
        return { results: data };
      },
      cache: true
    },
    initSelection: function(element, callback) {
      // the input tag has a value attribute preloaded that points to a preselected repository's id
      // this function resolves that id attribute to an object that select2 can render
      // using its formatResult renderer - that way the repository name is shown preselected
      var id = $(element).val();

      if (id !== "") {
        $.ajax(findAssignableUsersApiUrl + "?query=" + id + "&type=loginId", {
          dataType: "json"
        }).done(function(data) {
          if(data && data.length > 0) {
            callback(data[0]);
          }
        });
      }
    },
    formatResult: formatter, // omitted for brevity, see the source of this page
    formatSelection: formatter,  // omitted for brevity, see the source of this page
    matcher: matcher,
    escapeMarkup: function (m) { return m; } // we do not want to escape markup since we are displaying html in results
  });

  $assignee.on("select2-selecting", function(selected) {
    var data = { assignees: [selected.val] };

    if(updateAssgineesApiUrl){
        $.ajax(updateAssgineesApiUrl, {
            method: "POST",
            dataType: "json",
            contentType: "application/json",
            data: JSON.stringify(data)
        }).done(function(response){
            $yobi.notify(message + ": " + response.assignee.name, 3000);
        });
    }
  });
}
