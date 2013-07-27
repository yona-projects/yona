package utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

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

    /**
     * {@code bytes}를 앞에서부터 {@code size}만큼 읽어, 바이너리인지 그렇지 않은지를 판단하여 그 결과를
     * 반환한다.
     *
     * 주의: 이 메소드는 node.js 라이브러리를 자바로 포팅한 것으로, 향후 원본에 어떤 개선 사항이 있을 경우 그
     * 반영을 쉽게 할 수 있도록 의도적으로 자바 컨벤션을 적용하지 않았다. 원본에 대해서는 아래 설명을 보라.
     *
     * This method is imported from
     * https://github.com/gjtorikian/isBinaryFile/blob/b34108f9d93b36f1b2956ee7d516c5ce6bada5f1/index.js
     * Originally authored by by Garen Torikian in Javascript and licensed by MIT.
     *
     * Modification: Decide that the given data is binary if 0.5% of total bytes are suspicious.
     * Originally the threshold was 10%.
     *
     * @param bytes
     * @param size
     * @return 주어진 데이터가 바이너리인지 아닌지의 여부
     */
    public static boolean isBinary(byte[] bytes, long size) {
        int max_bytes = 512;

        if (size == 0)
            return false;

        int suspicious_bytes = 0;
        long total_bytes = Math.min(size, max_bytes);

        int[] intBytes = new int[512];
        for (int i = 0; i < total_bytes; i++) {
            intBytes[i] = bytes[i] & 0xFF;
        }

        if (size >= 3 && intBytes[0] == 0xEF && intBytes[1] == 0xBB && intBytes[2] == 0xBF) {
            // UTF-8 BOM. This isn't binary.
            return false;
        }

        for (int i = 0; i < total_bytes; i++) {
            if (intBytes[i] == 0) { // NULL byte--it's binary!
                return true;
            }
            else if ((intBytes[i] < 7 || intBytes[i] > 14) && (intBytes[i] < 32 || intBytes[i] > 127)) {
                // UTF-8 detection
                if (intBytes[i] > 191 && intBytes[i] < 224 && i + 1 < total_bytes) {
                    // 2 bytes
                    i++;
                    if (intBytes[i] < 192) {
                        continue;
                    }
                }
                else if (intBytes[i] > 223 && intBytes[i] < 239 && i + 2 < total_bytes) {
                    // 3 bytes
                    i++;
                    if (intBytes[i] < 192 && intBytes[i + 1] < 192) {
                        i++;
                        continue;
                    }
                }
                else if (intBytes[i] > 239 && intBytes[i] < 248 && i + 3 < total_bytes) {
                    // 4 bytes
                    i++;
                    if (intBytes[i] < 192 && intBytes[i + 1] < 192 && intBytes[i + 2] < 192) {
                        i += 2;
                        continue;
                    }
                }

                suspicious_bytes++;

                // Read at least 32 bytes before making a decision
                if (i > 32 && (float)suspicious_bytes / total_bytes > 0.005) {
                    return true;
                }
            }
        }

        if ((float)suspicious_bytes / total_bytes > 0.005) {
            return true;
        }

        return false;
    }

    /**
     * {@code inputStream}을 읽어 그 데이터가 바이너리인지 그렇지 않은지를 판단하여 그 결과를 반환한다.
     *
     * 최대 512바이트까지만 읽어들인다.
     *
     * @param inputStream
     * @return 읽어들인 데이터가 바이너리인지 아닌지의 여부
     * @throws java.io.IOException
     * @see {@link FileUtil#isBinary(byte[], long)}
     */
    public static boolean isBinary(InputStream inputStream) throws IOException {
        byte[] bytes = new byte[MAX_SIZE_FOR_BINARY_DETECTION];
        int size = inputStream.read(bytes, 0, MAX_SIZE_FOR_BINARY_DETECTION);
        return isBinary(bytes, size);
    }

    public static boolean isBinary(byte[] bytes) {
        return isBinary(bytes, Math.min(bytes.length, MAX_SIZE_FOR_BINARY_DETECTION));
    }
}
