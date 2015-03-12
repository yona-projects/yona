/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Ahn Hyeok Jun
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
import java.nio.charset.Charset;
import java.nio.file.Path;

public class FileUtil {

    public static void rm_rf(File file) throws Exception {
        if(file.isDirectory()){
            File[] list = file.listFiles();
            if (list == null) {
                throw new Exception("Unexpected error while deleting: " + file);
            }
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
     * We use mozilla's UniversalDetector instead of Tika's CharsetDetector
     * which raises "mark/reset not supported" IOException for FileInputStream.
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
     * We use mozilla's UniversalDetector instead of Tika's CharsetDetector
     * which raises "mark/reset not supported" IOException for FileInputStream.
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

    public static MediaType detectMediaType(File file, String name) throws IOException {
        return detectMediaType(new FileInputStream(file), name);
    }

    public static MediaType detectMediaType(byte[] bytes, String name) throws IOException {
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
    public static MediaType detectMediaType(InputStream is, String name)
        throws IOException {
        Metadata meta = new Metadata();
        meta.add(Metadata.RESOURCE_NAME_KEY, name);
        MediaType mediaType = new Tika().getDetector().detect(
                new BufferedInputStream(is), meta);

        if (mediaType.getType().toLowerCase().equals("text")) {
            return new MediaType(mediaType, Charset.forName(FileUtil.detectCharset(is)));
        } else if (mediaType.equals(MediaType.audio("ogg"))
                && FilenameUtils.getExtension(name).toLowerCase().equals("ogv")) {
            // This fixes Tika's misjudge of media type for ogg videos.
            return new MediaType("video", "ogg");
        }

        return mediaType;
    }

    public static String getCharset(MediaType mediaType) {
        return mediaType.hasParameters()
                ? mediaType.getParameters().get("charset") : null;
    }

    /**
     * Checks whether the subpath is a subpath of the given path
     *
     * @param subpath
     * @param path
     * @return true if the subpath is a subpath of the given path
     * @throws IOException
     */
    public static boolean isSubpathOf(Path subpath, Path path) throws IOException {
        return isSubpathOf(subpath, path, true);
    }

    public static boolean isSubpathOf(Path subpath, Path path, boolean resolveSymlink) throws IOException {
        if (resolveSymlink) {
            path = path.toRealPath();
            subpath = subpath.toRealPath();
        }

        return subpath.normalize().startsWith(path.normalize());
    }
}
