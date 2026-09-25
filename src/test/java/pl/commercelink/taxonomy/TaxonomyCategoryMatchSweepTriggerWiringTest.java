package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TaxonomyCategoryMatchSweepTriggerWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TaxonomyCategoryMatchSweep.class, () -> mock(TaxonomyCategoryMatchSweep.class))
            .withUserConfiguration(TaxonomyCategoryMatchSweepScheduler.class, TaxonomyCategoryMatchSweepListener.class);

    @Test
    void outsideAwsOnlyTheLocalCronTriggersTheSweep() {
        // when / then
        runner.withPropertyValues("application.env=localhost").run(context -> {
            assertThat(context).hasSingleBean(TaxonomyCategoryMatchSweepScheduler.class);
            assertThat(context).doesNotHaveBean(TaxonomyCategoryMatchSweepListener.class);
        });
    }

    @Test
    void inAwsOnlyTheQueueListenerTriggersTheSweep() {
        // when / then
        runner.withPropertyValues("application.env=prod").run(context -> {
            assertThat(context).hasSingleBean(TaxonomyCategoryMatchSweepListener.class);
            assertThat(context).doesNotHaveBean(TaxonomyCategoryMatchSweepScheduler.class);
        });
    }

    @Test
    void aMissingEnvironmentValueStillLeavesALocalTrigger() {
        // when / then
        runner.run(context -> {
            assertThat(context).hasSingleBean(TaxonomyCategoryMatchSweepScheduler.class);
            assertThat(context).doesNotHaveBean(TaxonomyCategoryMatchSweepListener.class);
        });
    }
}
