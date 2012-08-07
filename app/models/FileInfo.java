package models;

import java.util.Date;

public class FileInfo {
    public String name;
    public Date date;
    public String commitMessage;
    
    public FileInfo(String name, Date date, String commitMessage) {
        this.name = name;
        this.date = date;
        this.commitMessage = commitMessage;
    }
}
