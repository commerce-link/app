package pl.commercelink.stores;

public record CreateStoreRequest(String storeId, String name, String apiKey, DemoStoreMetadata demoMetadata, TrialPeriod trial,
                                 StoreSeeder seeder, String ownerEmail) {

    /**
     * A store created by the super admin. storeId is the id handed out with the form (null: generate one), so the same
     * form sent twice names one store.
     */
    public static CreateStoreRequest bare(String storeId, String name, String apiKey) {
        return new CreateStoreRequest(storeId, name, apiKey, null, null, null, null);
    }

    public static CreateStoreRequest registered(String name, TrialPeriod trial) {
        return new CreateStoreRequest(null, name, null, null, trial, null, trial.getOwnerEmail());
    }

    public static CreateStoreRequest seeded(String name, DemoStoreMetadata demoMetadata, StoreSeeder seeder) {
        return new CreateStoreRequest(null, name, null, demoMetadata, null, seeder, demoMetadata.getOwnerEmail());
    }
}
