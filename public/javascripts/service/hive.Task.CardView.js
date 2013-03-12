var cardLoad;
(function() {
  //angular!
  var URL_PREFIX = "";
  var cardapp = angular.module('cardApp', [ 'ui' ]);
  function CardCtrl($scope) {
    $scope.card; //model
    $scope.board = {};
    $scope.input = {
      comment : ""
    };
    cardLoad = function(id) {
      $.ajax(URL_PREFIX + "c/" + id, {
        dataType : "json",
        success : function(data) {
          $scope.card = data;
          $scope.$apply();
          adjust();
        }
      });
    }
    $.ajax(URL_PREFIX + "member", {
      success : function(data) {
        $scope.board.members = data;
        adjust();
      }
    });
    $.ajax(URL_PREFIX + "labels", {
      success : function(data) {
        $scope.board.labels = data;
        adjust();
      },
      dataType : "json"
    });
    function adjust() {
      if ($scope.card && $scope.board && $scope.board.members) {
        var assignee = $scope.card.assignee;
        var members = $scope.board.members;
        for ( var i = 0; i < members.length; i++) {
          members[i].assigned = false;
          for ( var j = 0; j < assignee.length; j++) {
            if (members[i]._id == assignee[j]._id) {
              members[i].assigned = true;
              assignee[j] = members[i];
            }
          }
        }
        $scope.$apply();
      }
      if ($scope.card && $scope.board && $scope.board.labels) {
        var cardLabels = $scope.card.labels;
        var boardLabels = $scope.board.labels;
        for ( var i = 0; i < boardLabels.length; i++) {
          boardLabels[i].state = false;
          for ( var j = 0; j < cardLabels.length; j++) {
            if (cardLabels[j]._id == boardLabels[i]._id) {
              boardLabels[i].state = true;
            }
          }
        }
      }
      $scope.$apply()
    }
    //inti function end 
    $scope.addComment = function() {
      $scope.card.comments.push($scope.input.comment);
      server.addComment($scope.card._id, $scope.input.comment);
      $scope.input.comment = "";

    }
    $scope.saveCard = function() {
      $scope.card.labels = [];
      for ( var i = 0; i < $scope.board.labels.length; i++) {
        if ($scope.board.labels[i].state == true) {
          $scope.card.labels.push($scope.board.labels[i]);
        }
      }
      server.saveCard($scope.card);
      console.log($scope.card);
    }
    $scope.showMemberList = function() {
      $scope.input.assign = !$scope.input.assign;
    }
    $scope.makeCheckList = function() {
      $scope.card.checklist = {
        items : [],
        title : "TODO"
      };
    }
    $scope.addChecklist = function() {
      $scope.card.checklist.items.push({
        body : $scope.input.checklist,
        state : false
      });
      $scope.input.checklist = "";
      $scope.saveCard();
    }
    $scope.checkItem = function(item) {
      $scope.saveCard();
    }
    $scope.assign = function(member) {
      if (member.assigned) {
        $scope.card.assignee.push(member);
      } else {
        $scope.card.assignee = _.without($scope.card.assignee, member);
      }
      $scope.saveCard();
    }
  }
  
  window.initCardView = function(url){
    URL_PREFIX = url;
    cardapp.controller('CardCtrl', CardCtrl);//여기는 실제함수와 어트리뷰트간의 관계맺기
    angular.bootstrap(document, [ 'cardApp' ]);
  }
  var server = {};
  server.addComment = function(_id, str) {
    $.ajax(URL_PREFIX + "comment", {
      type : "POST",
      data : {
        body : str,
        _id : _id
      },
      success : function(data) {
        cardLoad(_id);
      }
    });
  };
  server.saveCard = function(card, callback) {
    //서버로 카드 전체를 보내서 저장합니다.
    $.ajax(URL_PREFIX + "card", {
      type : "post",
      data : JSON.stringify(card),
      contentType : "text/json",
      success : function(data) {
        if (callback)
          callback();
      }
    });
  }
})();
