package utils;

import play.mvc.Result;

public class ValidationResult {
    private Result result;
    private boolean hasError;

    public ValidationResult(Result result, boolean hasError) {
        this.result = result;
        this.hasError = hasError;
    }

    public boolean hasError(){
        return hasError;
    }
    public Result getResult() {
        return result;
    }
}
