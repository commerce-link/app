package pl.commercelink.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * One budget per provider API for the whole instance: at most {@code maxConcurrent} calls at a time and
 * {@code perMinute} call starts in any 60 seconds. A caller waits up to {@code acquireTimeout}; otherwise the call is
 * not made and {@link ProviderCallRejectedException} is thrown. Several instances behind one NAT address must divide
 * the limits between them (configuration).
 */
@Component
public class ProviderCallLimiter {

    private final Duration acquireTimeout;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public ProviderCallLimiter(ProviderCallLimitProperties properties) {
        this.acquireTimeout = properties.acquireTimeout();
        properties.limits().forEach((name, limit) -> buckets.put(name, new Bucket(limit)));
    }

    public static ProviderCallLimiter unlimited() {
        return new ProviderCallLimiter(new ProviderCallLimitProperties(Duration.ZERO, Map.of()));
    }

    @SuppressWarnings("unchecked")
    public <T> T wrap(Class<T> type, String providerName, T target) {
        if (target == null || !buckets.containsKey(providerName)) {
            return target;
        }
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(target, args);
            }
            return call(providerName, () -> {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw sneaky(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            });
        });
    }

    public <R> R call(String providerName, Supplier<R> call) {
        Bucket bucket = buckets.get(providerName);
        if (bucket == null) {
            return call.get();
        }
        long deadline = System.nanoTime() + acquireTimeout.toNanos();
        bucket.acquire(providerName, deadline);
        try {
            return call.get();
        } finally {
            bucket.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneaky(Throwable e) throws E {
        throw (E) e;
    }

    /** Test-only: available concurrency permits for a provider's bucket, to check none leaked after rejection. */
    int availablePermits(String providerName) {
        Bucket bucket = buckets.get(providerName);
        return bucket == null ? -1 : bucket.availablePermits();
    }

    private static final class Bucket {

        private final Semaphore concurrent;
        private final int perMinute;
        private final Deque<Long> starts = new ArrayDeque<>();

        Bucket(ProviderCallLimitProperties.Limit limit) {
            this.concurrent = new Semaphore(limit.maxConcurrent(), true);
            this.perMinute = limit.perMinute();
        }

        void acquire(String providerName, long deadline) {
            boolean acquired = false;
            try {
                if (!concurrent.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    throw new ProviderCallRejectedException(providerName);
                }
                acquired = true;
                while (true) {
                    long wait = reserveStart();
                    if (wait == 0) {
                        return;
                    }
                    if (System.nanoTime() + wait > deadline) {
                        concurrent.release();
                        acquired = false;
                        throw new ProviderCallRejectedException(providerName);
                    }
                    TimeUnit.NANOSECONDS.sleep(wait);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // The concurrency permit was granted (acquired == true) before the wait for the per-minute budget
                // was interrupted: release it here, or it leaks forever and starves every future call to this
                // provider. An interrupt during the tryAcquire call above never grants a permit, so nothing to
                // release in that case.
                if (acquired) {
                    concurrent.release();
                }
                throw new ProviderCallRejectedException(providerName);
            }
        }

        /** Test-only: permits currently free to acquire, to check none leaked after a rejected/interrupted call. */
        int availablePermits() {
            return concurrent.availablePermits();
        }

        /** Records a call start and returns 0, or returns how long to wait until the window has room. */
        private synchronized long reserveStart() {
            long now = System.nanoTime();
            long windowStart = now - TimeUnit.MINUTES.toNanos(1);
            while (!starts.isEmpty() && starts.peekFirst() <= windowStart) {
                starts.pollFirst();
            }
            if (starts.size() < perMinute) {
                starts.addLast(now);
                return 0;
            }
            return starts.peekFirst() - windowStart;
        }

        void release() {
            concurrent.release();
        }
    }
}
