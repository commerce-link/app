package pl.commercelink.starter.util;

/** Time an operation has taken, measured from the moment the instance is created. */
public final class ElapsedTime {

    private final long startNanos = System.nanoTime();

    public static ElapsedTime started() {
        return new ElapsedTime();
    }

    public long inMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
