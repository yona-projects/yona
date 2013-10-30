package controllers;

import org.tmatesoft.svn.core.internal.server.dav.*;

import java.io.*;

public class PlayDAVConfig extends DAVConfig {
    public PlayDAVConfig() {
    }

    public String getRepositoryParentPath() {
       return  new File("repo/svn").getAbsolutePath();
    }
}
