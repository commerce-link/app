package pl.commercelink.products.information;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Records what a class logs, for tests that pin the absence or presence of a log level. Close it to detach. */
final class CapturedLogs implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CapturedLogs(Class<?> source) {
        this.logger = (Logger) LoggerFactory.getLogger(source);
        appender.start();
        logger.addAppender(appender);
    }

    static CapturedLogs of(Class<?> source) {
        return new CapturedLogs(source);
    }

    List<String> errors() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** Polls (no sleep) until at least {@code count} errors are logged or the timeout passes; returns the errors. */
    List<String> awaitErrors(int count, java.time.Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (errors().size() < count && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        }
        return errors();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
