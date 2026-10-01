package pl.commercelink.testsupport;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.retry.ExhaustedRetryException;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the real executor does with what a closure throws, and that the hand-written answer the unit tests use does
 * the same: a conflict is retried and ends in OptimisticLockingExhaustedException; anything else is not retried and
 * comes out wrapped in ExhaustedRetryException, so a caller catching it by its own type never sees it.
 */
class RetryingOptimisticLockingExecutorTest {

    private final OptimisticLockingExecutor real = RetryingOptimisticLockingExecutor.create();

    @Test
    void theRealExecutorRetriesAConflictAndThenGivesUp() {
        // given
        AtomicInteger loads = new AtomicInteger();

        // when / then
        assertThatThrownBy(() -> real.modifyAndSave(loads::incrementAndGet, value -> { },
                value -> { throw new ConditionalCheckFailedException("version changed"); }))
                .isInstanceOf(OptimisticLockingExhaustedException.class);
        assertThat(loads).hasValue(RetryingOptimisticLockingExecutor.MAX_ATTEMPTS);
    }

    @Test
    void theRealExecutorWrapsAnyOtherExceptionOfAClosure() {
        // given
        AtomicInteger loads = new AtomicInteger();

        // when / then
        assertThatThrownBy(() -> real.modifyAndSave(loads::incrementAndGet,
                value -> { throw new IllegalStateException("protected"); }, value -> { }))
                .isInstanceOf(ExhaustedRetryException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(loads).hasValue(1);
    }

    @Test
    void theMockAnswerBehavesLikeTheRealExecutor() throws Throwable {
        // given
        Supplier<Integer> loader = () -> 1;
        Consumer<Integer> failing = value -> { throw new IllegalStateException("protected"); };
        Consumer<Integer> conflicting = value -> { throw new ConditionalCheckFailedException("version changed"); };
        Consumer<Integer> nothing = value -> { };

        // when / then
        assertThatThrownBy(() -> OptimisticLockingExecutorMocks.retryingModifyAndSave(3).answer(invocation(loader, failing, nothing)))
                .isInstanceOf(ExhaustedRetryException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OptimisticLockingExecutorMocks.retryingModifyAndSave(3).answer(invocation(loader, nothing, conflicting)))
                .isInstanceOf(OptimisticLockingExhaustedException.class);
    }

    private static InvocationOnMock invocation(Object loader, Object mutator, Object saver) {
        InvocationOnMock invocation = mock(InvocationOnMock.class);
        when(invocation.getArgument(0)).thenReturn(loader);
        when(invocation.getArgument(1)).thenReturn(mutator);
        when(invocation.getArgument(2)).thenReturn(saver);
        return invocation;
    }
}
