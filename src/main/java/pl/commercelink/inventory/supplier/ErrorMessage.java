package pl.commercelink.inventory.supplier;

public record ErrorMessage(String code, Object[] args) {

    public static ErrorMessage of(String code, Object... args) {
        return new ErrorMessage(code, args);
    }
}
