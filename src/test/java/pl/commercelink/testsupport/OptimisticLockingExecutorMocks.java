package pl.commercelink.testsupport;

import org.mockito.stubbing.Answer;

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
}
