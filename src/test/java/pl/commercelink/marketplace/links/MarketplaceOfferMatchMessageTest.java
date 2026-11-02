package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceOfferMatchMessageTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private static OfferMatchProgress progressAt(String runId, String nextCursor) {
        return new OfferMatchProgress(runId, NOW, NOW, OfferMatchProgress.Status.RUNNING, nextCursor, 0, null, 0, 0,
                null, null, null, null, null, null);
    }

    @Test
    void readsAMessageOfTheFormerShapeAsAStart() {
        // when
        Optional<MarketplaceOfferMatchMessage> parsed =
                MarketplaceOfferMatchMessage.parse("{\"storeId\":\"s1\",\"marketplace\":\"Empik\"}");

        // then
        assertThat(parsed).hasValueSatisfying(message -> assertThat(message.isContinuation()).isFalse());
    }

    @Test
    void writesAStartWithoutRunFields() {
        // when
        String json = MarketplaceOfferMatchMessage.start("s1", "Empik").toJson();

        // then
        assertThat(json).isEqualTo("{\"storeId\":\"s1\",\"marketplace\":\"Empik\"}");
    }

    @Test
    void readsBackAContinuation() {
        // given
        MarketplaceOfferMatchMessage continuation = MarketplaceOfferMatchMessage.continuation("s1", "Empik", "run-1", "200");

        // when
        Optional<MarketplaceOfferMatchMessage> parsed = MarketplaceOfferMatchMessage.parse(continuation.toJson());

        // then
        assertThat(parsed).contains(continuation);
        assertThat(parsed.get().isContinuation()).isTrue();
    }

    @Test
    void continuesOnlyTheRunsNextStep() {
        // given
        MarketplaceOfferMatchMessage message = MarketplaceOfferMatchMessage.continuation("s1", "Empik", "run-1", "200");

        // when / then
        assertThat(message.continues(progressAt("run-1", "200"))).isTrue();
        assertThat(message.continues(progressAt("run-1", "300"))).isFalse();
        assertThat(message.continues(progressAt("run-2", "200"))).isFalse();
        assertThat(message.continues(progressAt("run-1", "200").failed(NOW, "boom"))).isFalse();
    }

    @Test
    void continuesARunPausedBeforeItsFirstPage() {
        // given
        MarketplaceOfferMatchMessage message = MarketplaceOfferMatchMessage.continuation("s1", "Empik", "run-1", null);

        // when / then
        assertThat(message.isContinuation()).isTrue();
        assertThat(message.continues(progressAt("run-1", null))).isTrue();
    }
}
