package pl.commercelink.stores;

public record CreateStoreRequest(String name, DemoStoreMetadata demoMetadata, StoreSeeder seeder,
                                 String ownerEmail) {

    public static CreateStoreRequest bare(String name) {
        return new CreateStoreRequest(name, null, null, null);
    }

    public static CreateStoreRequest registered(String name, String ownerEmail) {
        return new CreateStoreRequest(name, null, null, ownerEmail);
    }

    public static CreateStoreRequest seeded(String name, DemoStoreMetadata demoMetadata, StoreSeeder seeder) {
        return new CreateStoreRequest(name, demoMetadata, seeder, demoMetadata.getOwnerEmail());
    }
}
