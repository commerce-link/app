package pl.commercelink.web.dtos;

public record ConnectedIntegration(String name, boolean connected, boolean isDefault) {

    public ConnectedIntegration(String name, boolean connected) {
        this(name, connected, false);
    }
}
