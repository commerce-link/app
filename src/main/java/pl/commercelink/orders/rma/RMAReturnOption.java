package pl.commercelink.orders.rma;

import pl.commercelink.stores.PackageTemplate;

public class RMAReturnOption {

    private final String id;
    private final String name;
    private final boolean isDefault;

    public RMAReturnOption(String id, String name, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
    }

    public static RMAReturnOption from(PackageTemplate template) {
        return new RMAReturnOption(
                template.getId(),
                template.getName(),
                template.isDefault()
        );
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
