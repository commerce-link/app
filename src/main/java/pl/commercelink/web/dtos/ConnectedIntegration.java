package pl.commercelink.web.dtos;

public record ConnectedIntegration(String name, boolean connected, boolean isDefault, boolean deviceAuth) {

    public ConnectedIntegration(String name, boolean connected) {
        this(name, connected, false, false);
    }

    public ConnectedIntegration(String name, boolean connected, boolean isDefault) {
        this(name, connected, isDefault, false);
    }
}
