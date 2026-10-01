package pl.commercelink.testsupport;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.mockito.stubbing.Answer;
import org.springframework.retry.ExhaustedRetryException;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class OptimisticLockingExecutorMocks {

    private OptimisticLockingExecutorMocks() {
    }

    @SuppressWarnings("unchecked")
    public static Answer<Object> passThroughModifyAndSave() {
        return invocation -> {
            Supplier<Object> loader = invocation.getArgument(0);
            Consumer<Object> mutator = invocation.getArgument(1);
            Consumer<Object> saver = invocation.getArgument(2);
            Object entity = loader.get();
            mutator.accept(entity);
            saver.accept(entity);
            return entity;
        };
    }

    @SuppressWarnings("unchecked")
    public static Answer<Object> passThroughModifyAndSaveReturning() {
        return invocation -> {
            Supplier<Object> loader = invocation.getArgument(0);
            Function<Object, Object> mutator = invocation.getArgument(1);
            Consumer<Object> saver = invocation.getArgument(2);
            Object entity = loader.get();
            Object result = mutator.apply(entity);
            saver.accept(entity);
            return result;
        };
    }

    /**
     * What the Spring Retry proxy of OptimisticLockingExecutor (starter 0.1.10) does, without Spring: the whole
     * load-modify-save runs again on a ConditionalCheckFailedException, and once {@code maxAttempts} are used up the
     * @Recover method turns the last one into an OptimisticLockingExhaustedException. Any other exception is not
     * retried and, since no @Recover method takes it, comes out wrapped in an ExhaustedRetryException ("Cannot locate
     * recovery method") -- never as itself. RetryingOptimisticLockingExecutor pins this against the real proxy.
     */
    @SuppressWarnings("unchecked")
    public static Answer<Object> retryingModifyAndSave(int maxAttempts) {
        return invocation -> {
            Supplier<Object> loader = invocation.getArgument(0);
            Consumer<Object> mutator = invocation.getArgument(1);
            Consumer<Object> saver = invocation.getArgument(2);
            ConditionalCheckFailedException last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    Object entity = loader.get();
                    mutator.accept(entity);
                    saver.accept(entity);
                    return entity;
                } catch (ConditionalCheckFailedException e) {
                    last = e;
                } catch (RuntimeException e) {
                    throw new ExhaustedRetryException("Cannot locate recovery method", e);
                }
            }
            throw new OptimisticLockingExhaustedException(maxAttempts, last);
        };
    }
}
