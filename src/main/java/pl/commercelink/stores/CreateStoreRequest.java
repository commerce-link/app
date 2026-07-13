package pl.commercelink.stores;

public record CreateStoreRequest(String name, String apiKey, DemoStoreMetadata demoMetadata, StoreSeeder seeder,
                                 boolean welcomeNotification) {

    public static CreateStoreRequest bare(String name, String apiKey) {
        return bare(name, apiKey, true);
    }

    public static CreateStoreRequest bare(String name, String apiKey, boolean welcomeNotification) {
        return new CreateStoreRequest(name, apiKey, null, null, welcomeNotification);
    }

    public static CreateStoreRequest seeded(String name, DemoStoreMetadata demoMetadata, StoreSeeder seeder) {
        return new CreateStoreRequest(name, null, demoMetadata, seeder, false);
    }
}
