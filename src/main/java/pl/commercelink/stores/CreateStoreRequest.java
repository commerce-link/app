package pl.commercelink.stores;

public record CreateStoreRequest(String name, String apiKey, DemoStoreMetadata demoMetadata, TrialPeriod trial,
                                 StoreSeeder seeder, String ownerEmail) {

    public static CreateStoreRequest bare(String name, String apiKey) {
        return new CreateStoreRequest(name, apiKey, null, null, null, null);
    }

    public static CreateStoreRequest registered(String name, TrialPeriod trial) {
        return new CreateStoreRequest(name, null, null, trial, null, trial.getOwnerEmail());
    }

    public static CreateStoreRequest seeded(String name, DemoStoreMetadata demoMetadata, StoreSeeder seeder) {
        return new CreateStoreRequest(name, null, demoMetadata, null, seeder, demoMetadata.getOwnerEmail());
    }
}
