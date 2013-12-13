package utils;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.mozilla.universalchardet.UniversalDetector;

public class FileUtil {

    public static final int MAX_SIZE_FOR_BINARY_DETECTION = 512;

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

    static private String or(String a, String b) {
        return StringUtils.isNotEmpty(a) ? a : b;
    }

    /**
     * 문자열을 읽어들여 해당 문자열이 어떤 charset을 사용하고 있는지 알아낸다.
     *
     * 실패한 경우 UTF-8을 반환한다.
     *
     * @param bytes
     * @return
     * @throws IOException
     */
    public static String detectCharset(byte bytes[]) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);
        int offset = 0;

        do {
            int blockSize = Math.min(4096, bytes.length - offset);
            detector.handleData(bytes, offset, blockSize);
            offset += blockSize;
        } while(offset < bytes.length);
        detector.dataEnd();

        return or(detector.getDetectedCharset(), "UTF-8");
    }

    /**
     * 문자열을 읽어들여 해당 문자열이 어떤 charset을 사용하고 있는지 알아낸다.
     *
     * 실패한 경우 UTF-8을 반환한다.
     *
     * @param is
     * @return
     * @throws IOException
     */
    static protected String detectCharset(InputStream is) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[4096];
        int nRead = 0;

        while ((nRead = is.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, nRead);
        }
        detector.dataEnd();

        return or(detector.getDetectedCharset(), "UTF-8");
    }
}
