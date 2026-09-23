package pl.commercelink.provider;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderCallLimiterTest {

    interface Api {
        String hello();
    }

    private static ProviderCallLimiter limiter(int maxConcurrent, int perMinute, Duration timeout) {
        return new ProviderCallLimiter(new ProviderCallLimitProperties(timeout,
                Map.of("fakturownia", new ProviderCallLimitProperties.Limit(maxConcurrent, perMinute))));
    }

    @Test
    void neverRunsMoreCallsAtOnceThanAllowed() throws Exception {
        ProviderCallLimiter limiter = limiter(2, 1000, Duration.ofSeconds(5));
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch done = new CountDownLatch(6);
        for (int i = 0; i < 6; i++) {
            pool.submit(() -> {
                limiter.call("fakturownia", (Supplier<Void>) () -> {
                    peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                    sleep(50);
                    running.decrementAndGet();
                    return null;
                });
                done.countDown();
            });
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(peak.get()).isEqualTo(2);
    }

    @Test
    void refusesWhenNoPermitComesInTime() throws Exception {
        ProviderCallLimiter limiter = limiter(1, 1000, Duration.ofMillis(100));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> limiter.call("fakturownia", () -> {
            inside.countDown();
            await(release);
            return null;
        }));
        holder.start();
        inside.await();

        assertThatThrownBy(() -> limiter.call("fakturownia", () -> "x"))
                .isInstanceOf(ProviderCallRejectedException.class);
        release.countDown();
        holder.join();
    }

    @Test
    void refusesWhenTheMinuteBudgetIsSpent() {
        ProviderCallLimiter limiter = limiter(2, 2, Duration.ofMillis(100));
        limiter.call("fakturownia", () -> "1");
        limiter.call("fakturownia", () -> "2");

        assertThatThrownBy(() -> limiter.call("fakturownia", () -> "3"))
                .isInstanceOf(ProviderCallRejectedException.class);
    }

    @Test
    void providersWithoutALimitAreNotWrapped() {
        ProviderCallLimiter limiter = limiter(1, 1, Duration.ofMillis(10));
        Api api = () -> "hi";

        assertThat(limiter.wrap(Api.class, "other", api)).isSameAs(api);
    }

    @Test
    void wrappedCallsCountAgainstTheBudgetAndKeepExceptions() {
        ProviderCallLimiter limiter = limiter(2, 1, Duration.ofMillis(100));
        Api api = limiter.wrap(Api.class, "fakturownia", () -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(api::hello).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThatThrownBy(api::hello).isInstanceOf(ProviderCallRejectedException.class);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
