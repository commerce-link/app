package pl.commercelink.testsupport;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import pl.commercelink.starter.autoconfigure.OptimisticLockingProperties;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

/**
 * The real OptimisticLockingExecutor behind the real Spring Retry proxy, as the application runs it, with no delay
 * between attempts. Its @Retryable retries only ConditionalCheckFailedException and its only @Recover takes that
 * exception: anything else thrown by a closure comes out as an ExhaustedRetryException ("Cannot locate recovery
 * method") with the original as its cause -- which a test with a hand-written executor answer would never show.
 */
public final class RetryingOptimisticLockingExecutor {

    public static final int MAX_ATTEMPTS = 3;

    private RetryingOptimisticLockingExecutor() {
    }

    @Configuration
    @EnableRetry
    static class Config {

        @Bean
        OptimisticLockingProperties optimisticLockingProperties() {
            OptimisticLockingProperties properties = new OptimisticLockingProperties();
            properties.setMaxAttempts(MAX_ATTEMPTS);
            properties.setDelay(1);
            properties.setMultiplier(1.0);
            properties.setMaxDelay(1);
            properties.setRandom(false);
            return properties;
        }

        @Bean
        OptimisticLockingExecutor optimisticLockingExecutor(OptimisticLockingProperties optimisticLockingProperties) {
            return new OptimisticLockingExecutor(optimisticLockingProperties);
        }
    }

    public static OptimisticLockingExecutor create() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
        return context.getBean(OptimisticLockingExecutor.class);
    }
}
