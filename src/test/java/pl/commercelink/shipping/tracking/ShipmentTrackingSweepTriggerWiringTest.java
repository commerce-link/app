package pl.commercelink.shipping.tracking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ShipmentTrackingSweepTriggerWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ShipmentTrackingSweep.class, () -> mock(ShipmentTrackingSweep.class))
            .withUserConfiguration(ShipmentTrackingSweepScheduler.class, ShipmentTrackingSweepListener.class);

    @Test
    void outsideAwsOnlyTheLocalCronTriggersTheSweep() {
        runner.withPropertyValues("application.env=localhost").run(context -> {
            assertThat(context).hasSingleBean(ShipmentTrackingSweepScheduler.class);
            assertThat(context).doesNotHaveBean(ShipmentTrackingSweepListener.class);
        });
    }

    @Test
    void inAwsOnlyTheQueueListenerTriggersTheSweep() {
        runner.withPropertyValues("application.env=prod").run(context -> {
            assertThat(context).hasSingleBean(ShipmentTrackingSweepListener.class);
            assertThat(context).doesNotHaveBean(ShipmentTrackingSweepScheduler.class);
        });
    }

    @Test
    void aMissingEnvironmentValueStillLeavesALocalTrigger() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ShipmentTrackingSweepScheduler.class);
            assertThat(context).doesNotHaveBean(ShipmentTrackingSweepListener.class);
        });
    }
}
