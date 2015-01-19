/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Suwon Chae
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

public class LineEnding {
    public static final EndingType DEFAULT_ENDING_TYPE = EndingType.UNIX;
    public enum EndingType {
        DOS("\r\n"), UNIX("\n"), UNDEFINED("");
        public String value;

        EndingType(String lineEnding) {
            this.value = lineEnding;
        }
    }

    public static String changeLineEnding(String contents, EndingType to){
        EndingType endingType = findLineEnding(contents);
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
        if(contents.contains("\r\n") ) {
            return EndingType.DOS;
        } else {
            return EndingType.UNIX;
        }
    }
}
