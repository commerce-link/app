package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DropshipTrackingSweepTriggerWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DropshipTrackingSweep.class, () -> mock(DropshipTrackingSweep.class))
            .withUserConfiguration(DropshipTrackingSweepScheduler.class, DropshipTrackingSweepListener.class);

    @Test
    void outsideAwsOnlyTheLocalCronTriggersTheSweep() {
        runner.withPropertyValues("application.env=localhost").run(context -> {
            assertThat(context).hasSingleBean(DropshipTrackingSweepScheduler.class);
            assertThat(context).doesNotHaveBean(DropshipTrackingSweepListener.class);
        });
    }

    @Test
    void inAwsOnlyTheQueueListenerTriggersTheSweep() {
        runner.withPropertyValues("application.env=prod").run(context -> {
            assertThat(context).hasSingleBean(DropshipTrackingSweepListener.class);
            assertThat(context).doesNotHaveBean(DropshipTrackingSweepScheduler.class);
        });
    }

    @Test
    void aMissingEnvironmentValueStillLeavesALocalTrigger() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DropshipTrackingSweepScheduler.class);
            assertThat(context).doesNotHaveBean(DropshipTrackingSweepListener.class);
        });
    }
}
