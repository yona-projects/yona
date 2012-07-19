package controllers;

import play.Logger;
import play.mvc.*;

public class GitApp extends Controller{
    public static Result post(String path){
        Logger.info(path);
        Logger.info(request().toString());
        return ok();
    }
    
    public static Result get(String path){
        Logger.info(path);
        Logger.info(request().toString());
        return ok();
    }

}
