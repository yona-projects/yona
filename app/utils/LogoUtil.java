package utils;

import play.mvc.Http;

public class LogoUtil {
    public static final int LOGO_FILE_LIMIT_SIZE = 1024*1000*5; //5M

    /** 프로젝트 로고로 사용할 수 있는 이미지 확장자 */
    public static final String[] LOGO_TYPE = {"jpg", "jpeg", "png", "gif", "bmp"};

    /**
     * {@code filePart} 정보가 비어있는지 확인한다.<p />
     * @param filePart
     * @return {@code filePart}가 null이면 true, {@code filename}이 null이면 true, {@code fileLength}가 0 이하이면 true
     */
    public static boolean isEmptyFilePart(Http.MultipartFormData.FilePart filePart) {
        return filePart == null || filePart.getFilename() == null || filePart.getFilename().length() <= 0;
    }

    /**
     * {@code filename}의 확장자를 체크하여 이미지인지 확인한다.<p />
     *
     * 이미지 확장자는 {@link #LOGO_TYPE} 에 정의한다.
     * @param filename the filename
     * @return true, if is image file
     */
    public static boolean isImageFile(String filename) {
        boolean isImageFile = false;
        for(String suffix : LOGO_TYPE) {
            if(filename.toLowerCase().endsWith(suffix))
                isImageFile = true;
        }
        return isImageFile;
    }
}
