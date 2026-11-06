package pl.commercelink.marketplace.offermatching;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketplaceOfferMatchSweepListenerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(MarketplaceOfferMatchSweep.class, () -> mock(MarketplaceOfferMatchSweep.class))
            .withUserConfiguration(MarketplaceOfferMatchSweepListener.class);

    @Test
    void inProductionTheQueueMessageRunsTheSweep() {
        runner.withPropertyValues("application.env=prod").run(context -> {
            // given
            MarketplaceOfferMatchSweepListener listener = context.getBean(MarketplaceOfferMatchSweepListener.class);

            // when
            listener.handleMessage("");

            // then
            verify(context.getBean(MarketplaceOfferMatchSweep.class)).sweep();
        });
    }

    @Test
    void outsideProductionNothingRunsTheSweep() {
        runner.withPropertyValues("application.env=localhost").run(context -> {
            // then
            assertThat(context).doesNotHaveBean(MarketplaceOfferMatchSweepListener.class);
        });
    }

    @Test
    void aMissingEnvironmentValueRunsNoSweep() {
        runner.run(context -> {
            // then
            assertThat(context).doesNotHaveBean(MarketplaceOfferMatchSweepListener.class);
        });
    }
}
