package controllers;

import play.mvc.Controller;
import play.mvc.Result;

import views.html.board.list;
import views.html.board.newBoard;

public class BoardApp extends Controller {

    public static Result boardList() {
        return ok(list.render("Board List"));
    }

    public static Result getNewBoard() {
        return ok(newBoard.render("New Board"));
    }

    public static Result newBoard() {
        return ok(newBoard.render("New Board"));
    }

}
