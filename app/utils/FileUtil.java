package utils;

import org.apache.commons.lang.StringUtils;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class FileUtil {

    public static final int MAX_SIZE_FOR_BINARY_DETECTION = 512;

    public static void rm_rf(File file){
        if(file.isDirectory()){
            File[] list = file.listFiles();
            for (File f : list) {
                rm_rf(f);
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
     * Detect the charset used for the given byte array.
     *
     * Return "UTF-8" if it fails.
     *
     * @param bytes - a byte array to be checked
     * @return charset - the charset used
     * @throws IOException
     */
    public static String detectCharset(byte bytes[]) {
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
     * Detect the charset used for the given stream.
     *
     * Return "UTF-8" if it fails.
     *
     * @param is - an input stream to be checked
     * @return charset - the charset used
     * @throws IOException
     */
    public static String detectCharset(InputStream is) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[4096];
        int nRead;

        while ((nRead = is.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, nRead);
        }
        detector.dataEnd();

        return or(detector.getDetectedCharset(), "UTF-8");
    }
}
