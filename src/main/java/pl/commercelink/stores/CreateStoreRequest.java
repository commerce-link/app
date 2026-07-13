package pl.commercelink.stores;

public record CreateStoreRequest(String name, String apiKey, DemoStoreMetadata demoMetadata, StoreSeeder seeder) {

    public static CreateStoreRequest bare(String name, String apiKey) {
        return new CreateStoreRequest(name, apiKey, null, null);
    }

    public static CreateStoreRequest seeded(String name, DemoStoreMetadata demoMetadata, StoreSeeder seeder) {
        return new CreateStoreRequest(name, null, demoMetadata, seeder);
    }
}
