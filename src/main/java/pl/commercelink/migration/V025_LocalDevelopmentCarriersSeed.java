package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import pl.commercelink.carriers.CarrierSeed;

import java.io.IOException;
import java.io.InputStream;

/**
 * Local carriers from the public example file. Production carriers and dictionaries are not seeded by the app:
 * they are loaded from outside this repository, before the deploy.
 */
@Profile("localdev")
@ChangeUnit(id = "V025-local-development-carriers-seed", order = "025", author = "commercelink")
@RequiredArgsConstructor
public class V025_LocalDevelopmentCarriersSeed {

    static final String EXAMPLE = "local-init/carriers/carriers.example.json";

    private final CarrierSeed carrierSeed;

    @Execution
    public void seed() throws IOException {
        try (InputStream example = new ClassPathResource(EXAMPLE).getInputStream()) {
            carrierSeed.load(example);
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
