package pl.commercelink.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoresRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoStoreCleanupTriggerWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(StoresRepository.class, () -> mock(StoresRepository.class))
            .withBean(StoreDeletionService.class, () -> mock(StoreDeletionService.class))
            .withUserConfiguration(DemoStoreCleanup.class, DemoStoreCleanupScheduler.class, DemoStoreCleanupListener.class);

    @Test
    void outsideAwsOnlyTheLocalCronTriggersTheCleanup() {
        // when / then
        runner.withPropertyValues("app.registration.demo=true", "application.env=localhost").run(context -> {
            assertThat(context).hasSingleBean(DemoStoreCleanupScheduler.class);
            assertThat(context).doesNotHaveBean(DemoStoreCleanupListener.class);
        });
    }

    @Test
    void inAwsOnlyTheQueueListenerTriggersTheCleanup() {
        // when / then
        runner.withPropertyValues("app.registration.demo=true", "application.env=prod").run(context -> {
            assertThat(context).hasSingleBean(DemoStoreCleanupListener.class);
            assertThat(context).doesNotHaveBean(DemoStoreCleanupScheduler.class);
        });
    }

    @Test
    void aMissingEnvironmentValueStillLeavesALocalTrigger() {
        // when / then
        runner.withPropertyValues("app.registration.demo=true").run(context -> {
            assertThat(context).hasSingleBean(DemoStoreCleanupScheduler.class);
            assertThat(context).doesNotHaveBean(DemoStoreCleanupListener.class);
        });
    }

    @Test
    void aDeployedEnvironmentWithoutDemoRegistrationRunsNoCleanup() {
        // when / then
        runner.withPropertyValues("application.env=prod").run(context -> {
            assertThat(context).doesNotHaveBean(DemoStoreCleanupListener.class);
            assertThat(context).doesNotHaveBean(DemoStoreCleanupScheduler.class);
            assertThat(context).doesNotHaveBean(DemoStoreCleanup.class);
        });
    }

    @Test
    void localDevelopmentWithoutDemoRegistrationRunsNoCleanup() {
        // when / then
        runner.withPropertyValues("application.env=localhost").run(context -> {
            assertThat(context).doesNotHaveBean(DemoStoreCleanupScheduler.class);
            assertThat(context).doesNotHaveBean(DemoStoreCleanup.class);
        });
    }
}
