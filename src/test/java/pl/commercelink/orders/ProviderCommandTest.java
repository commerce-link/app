package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCommandTest {

    private static final LocalDateTime SENT = LocalDateTime.of(2026, 10, 7, 10, 0);

    @Test
    void aCommandIsOverdueOnlyAfterTheTimeout() {
        // given
        ProviderCommand command = ProviderCommand.sent("cmd-1", SENT);

        // then
        assertThat(command.isOverdue(SENT.plus(ProviderCommandTimeout.UNCONFIRMED_AFTER))).isFalse();
        assertThat(command.isOverdue(SENT.plus(ProviderCommandTimeout.UNCONFIRMED_AFTER).plusSeconds(1))).isTrue();
    }

    @Test
    void aCommandWithoutRequestTimeIsOverdue() {
        assertThat(ProviderCommand.notSent().isOverdue(SENT)).isTrue();
    }

    @Test
    void failingKeepsTheCommandAndReplacesTheReason() {
        // given
        ProviderCommand sent = ProviderCommand.sent("cmd-1", SENT);

        // when
        ProviderCommand refused = sent.failed("Nieprawidłowy kod pocztowy");
        ProviderCommand unconfirmed = refused.failedWithKey("shipping.pickup.unconfirmed");

        // then
        assertThat(refused.getCommandId()).isEqualTo("cmd-1");
        assertThat(refused.getRequestedAt()).isEqualTo(SENT);
        assertThat(refused.failureReason()).isEqualTo("Nieprawidłowy kod pocztowy");
        assertThat(unconfirmed.getError()).isNull();
        assertThat(unconfirmed.failureReason()).isEqualTo("shipping.pickup.unconfirmed");
        assertThat(sent.getError()).isNull();
    }

    @Test
    void aCommandMatchesOnlyItsOwnId() {
        // given
        ProviderCommand command = ProviderCommand.sent("cmd-1", SENT);

        // then
        assertThat(command.hasId("cmd-1")).isTrue();
        assertThat(command.hasId("cmd-2")).isFalse();
        assertThat(ProviderCommand.notSent().hasId(null)).isFalse();
    }
}
