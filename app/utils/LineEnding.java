/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package utils;

import org.apache.commons.lang3.StringUtils;

public class LineEnding {
    public static final EndingType DEFAULT_ENDING_TYPE = EndingType.UNIX;
    public enum EndingType {
        DOS("\r\n"), UNIX("\n"), UNDEFINED("");
        public String value;

        EndingType(String lineEnding) {
            this.value = lineEnding;
        }
    }

    public static String changeLineEnding(String contents, String to){
        if(StringUtils.isNotEmpty(to) && "DOS".equalsIgnoreCase(to)){
            return changeLineEnding(contents, EndingType.DOS);
        }
        return changeLineEnding(contents, EndingType.UNIX);
    }

    public static String changeLineEnding(String contents, EndingType to){
        EndingType endingType = findLineEnding(contents);
        if (StringUtils.isEmpty(contents)) {
            return "";
        }
        if(endingType != EndingType.DOS && to == EndingType.DOS) {
            return contents.replaceAll("\n", "\n");
        }
        if(endingType != EndingType.UNIX && to == EndingType.UNIX) {
            return contents.replaceAll("\r\n", "\n");
        }
        return contents;
    }

    public static String addEOL(String contents){
        EndingType endingType = findLineEnding(contents);
        if(endingType == EndingType.UNDEFINED){
            endingType = DEFAULT_ENDING_TYPE;
        }
        if ( contents != null ){
            if(!contents.endsWith(endingType.value)){
                return contents.concat(endingType.value);
            }
        }
        return contents;
    }

    public static EndingType findLineEnding(String contents){
        if (StringUtils.isEmpty(contents)) {
            return EndingType.UNDEFINED;
        }
        
        if(contents.contains("\r\n") ) {
            return EndingType.DOS;
        } else {
            return EndingType.UNIX;
        }
    }
}
