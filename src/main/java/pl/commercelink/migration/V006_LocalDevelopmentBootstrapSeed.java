package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import pl.commercelink.demo.DemoStoreSeeder;
import pl.commercelink.localdev.CatalogSeed;
import pl.commercelink.localdev.LocalDevFeedSeeder;

@Profile("localdev")
@ChangeUnit(id = "V006-local-development-bootstrap-seed", order = "006", author = "commercelink", runAlways = true)
@RequiredArgsConstructor
public class V006_LocalDevelopmentBootstrapSeed {

    private static final String STORE_ID = "uma2dqukxr";

    private final DemoStoreSeeder demoStoreSeeder;
    private final LocalDevFeedSeeder feedSeeder;

    @Execution
    public void seed() {
        demoStoreSeeder.seedStore(STORE_ID, "Demo Store", null);
        feedSeeder.seedGlobalFeeds(CatalogSeed.load());
    }

    @RollbackExecution
    public void rollback() {
    }
}
