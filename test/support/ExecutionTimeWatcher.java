package support;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import play.Logger;

public class ExecutionTimeWatcher extends TestWatcher {
    DateTime start;
    DateTime end;

    @Override
    protected void starting(Description description) {
        this.start = new DateTime();
    }

    @Override
    protected void finished(Description description) {
        this.end = new DateTime();
        Interval interval = new Interval(start, end);
        if ( interval.toDurationMillis() / 1000 > 3 ){
            Logger.debug("\u001B[0;35m" + description.getMethodName() + ": " + interval.toDurationMillis() / 1000 + " sec\u001B[0m");
        } else {
            Logger.debug(description.getMethodName() + ": " + interval.toDurationMillis() / 1000 + " sec");
        }
    }
}
