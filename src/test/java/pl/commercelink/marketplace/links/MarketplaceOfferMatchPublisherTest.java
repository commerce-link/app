package pl.commercelink.marketplace.links;

import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.function.Consumer;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketplaceOfferMatchPublisherTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @InjectMocks
    private MarketplaceOfferMatchPublisher publisher;

    @SuppressWarnings("unchecked")
    private SqsSendOptions<String> sentOptions() {
        ArgumentCaptor<Consumer<SqsSendOptions<String>>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsTemplate).send(captor.capture());
        SqsSendOptions<String> options = mock(SqsSendOptions.class, RETURNS_SELF);
        captor.getValue().accept(options);
        return options;
    }

    @Test
    void ordersARunWithAStartMessage() {
        // when
        publisher.order("s1", "Empik");

        // then
        verify(sqsTemplate).send("marketplace-offer-match-queue", "{\"storeId\":\"s1\",\"marketplace\":\"Empik\"}");
    }

    @Test
    void publishesContinuationDelayedByRetryAfter() {
        // when
        publisher.continueRun("s1", "Empik", "run-1", "200", Duration.ofSeconds(60));

        // then
        SqsSendOptions<String> options = sentOptions();
        verify(options).queue("marketplace-offer-match-queue");
        verify(options).payload("{\"storeId\":\"s1\",\"marketplace\":\"Empik\",\"runId\":\"run-1\",\"cursor\":\"200\"}");
        verify(options).delaySeconds(60);
    }

    @Test
    void capsTheDelayAtWhatSqsAllows() {
        // when
        publisher.continueRun("s1", "Empik", "run-1", "200", Duration.ofMinutes(20));

        // then
        verify(sentOptions()).delaySeconds(900);
    }

    @Test
    void roundsAPartSecondUp() {
        // when
        publisher.continueRun("s1", "Empik", "run-1", "200", Duration.ofMillis(59_200));

        // then
        verify(sentOptions()).delaySeconds(60);
    }

    @Test
    void sendsANegativeDelayAtOnce() {
        // when
        publisher.continueRun("s1", "Empik", "run-1", "200", Duration.ofSeconds(-5));

        // then
        verify(sentOptions()).delaySeconds(0);
    }

    @Test
    void sendsAContinuationWithoutDelayAtOnce() {
        // when
        publisher.continueRun("s1", "Empik", "run-1", "200", Duration.ZERO);

        // then
        verify(sentOptions()).delaySeconds(0);
    }
}
