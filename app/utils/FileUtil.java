package utils;

import java.io.File;

public class FileUtil {
    public static void rm_rf(File file){
        if(file.isDirectory()){
            File[] list = file.listFiles();
            for(int i = 0; i < list.length; i++) {
                rm_rf(list[i]);
            }
            file.delete();
        } else {
            file.delete();
        }
    }
}
