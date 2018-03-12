/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function yonaIssueSharerModule(findUsersByloginIdsApiUrl, findSharableUsersApiUrl, updateSharingApiUrl, message){
  function formatter(result){
    if(!result.avatarUrl){
      return "<div>" + result.name + "</div>";
    }

    // Template text. Also you can use predefined template: $("#tplSelect2FormatUser").text()
    var tplUserItem = "<div class='usf-group' title='${name} ${loginId}'>" +
        "<strong class='name'>${name}</strong>";

    var formattedResult = $yobi.tmpl(tplUserItem, {
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

  var $issueSharer = $("#issueSharer");
  $issueSharer.select2({
    minimumInputLength: 1,
    multiple: true,
    id: function(obj) {
      return obj.loginId; // use slug field for id
    },
    ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
      url: findSharableUsersApiUrl,
      dataType: "json",
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

      var ids = $(element).val();
      if (ids !== "") {
        $.ajax(findUsersByloginIdsApiUrl+ "?query=" + ids, {
          dataType: "json"
        }).done(function(data) {
          if(data && data.length > 0) {
            callback(data);
          }
        });
      }
    },
    formatResult: formatter, // omitted for brevity, see the source of this page
    formatSelection: formatter,  // omitted for brevity, see the source of this page
    matcher: matcher,
    escapeMarkup: function (m) { return m; } // we do not want to escape markup since we are displaying html in results
  });

  $issueSharer.on("select2-selecting", function(selected) {
    var data = { sharer: {loginId: selected.object.loginId, type: selected.object.type}, action: 'add'};

    if(updateSharingApiUrl){
        $.ajax(updateSharingApiUrl, {
            method: "POST",
            dataType: "json",
            contentType: "application/json",
            data: JSON.stringify(data)
        }).done(function(response){
            $yobi.notify(response.action + ": " + response.sharer, 3000);
        });
    }
  });

  $issueSharer.on("select2-removing", function(selected) {
    var data = { sharer: {loginId: selected.choice.loginId, type: selected.choice.type}, action: 'delete'};

    if(updateSharingApiUrl){
      $.ajax(updateSharingApiUrl, {
        method: "POST",
        dataType: "json",
        contentType: "application/json",
        data: JSON.stringify(data)
      }).done(function(response){
        $yobi.notify(response.action + ": " + response.sharer, 3000);
      });
    }
  });

  $issueSharer.on('change', function (e) {
    $(".issue-sharer-count").text(e.val.length);
  });
}
