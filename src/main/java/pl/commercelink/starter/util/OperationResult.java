package pl.commercelink.starter.util;

public class OperationResult<T> {

    private final boolean success;
    private final String message;
    private final T payload;

    private OperationResult(boolean success, String message, T payload) {
        this.success = success;
        this.message = message;
        this.payload = payload;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getPayload() {
        return payload;
    }

    public boolean hasPayload() {
        return payload != null;
    }

    public static <T> OperationResult<T> success() {
        return new OperationResult<>(true, "", null);
    }

    public static <T> OperationResult<T> success(T payload) {
        return new OperationResult<>(true, "", payload);
    }

    public static <T> OperationResult<T> failure(String message) {
        return new OperationResult<>(false, message, null);
    }
}
