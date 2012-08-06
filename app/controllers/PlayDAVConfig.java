package controllers;

import java.io.File;

import org.tmatesoft.svn.core.internal.server.dav.DAVConfig;

public class PlayDAVConfig extends DAVConfig {
    public PlayDAVConfig() {
    }
    
    public String getRepositoryParentPath() {
       return  new File("repo/svn").getAbsolutePath();
    }
}
