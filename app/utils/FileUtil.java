/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Ahn Hyeok Jun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package utils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.*;

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

    public static String detectMediaType(File file, String name) throws IOException {
        return detectMediaType(new FileInputStream(file), name);
    }

    public static String detectMediaType(byte[] bytes, String name) throws IOException {
        return detectMediaType(new ByteArrayInputStream(bytes), name);
    }

    /**
     * Detects media type of the given resource, by using Apache Tika.
     *
     * This method does following additional tasks besides Tika:
     * 1. Adds a charset parameter for text resources.
     * 2. Fixes Tika's misjudge of media type for ogg videos
     *
     * @param is    the input stream to read the resource
     * @param name  the filename of the resource
     * @return the detected media type which optionally includes a charset parameter
     *         e.g. "text/plain; charset=utf-8"
     * @throws IOException
     */
    public static String detectMediaType(InputStream is, String name) throws IOException {
        Metadata meta = new Metadata();
        meta.add(Metadata.RESOURCE_NAME_KEY, name);
        MediaType mediaType = new Tika().getDetector().detect(
                new BufferedInputStream(is), meta);
        String mimeType = mediaType.toString();
        if (mediaType.getType().toLowerCase().equals("text")) {
            mimeType += "; charset=" + FileUtil.detectCharset(is);
        } else if (mediaType.equals(MediaType.audio("ogg"))
                && FilenameUtils.getExtension(name).toLowerCase().equals("ogv")) {
            // This fixes Tika's misjudge of media type for ogg videos.
            mimeType = "video/ogg";
        }

        return mimeType;
    }
}
