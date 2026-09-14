package pl.commercelink.clientaccess;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.clientaccess.ClientVerificationException.Reason;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.starter.email.EmailNotification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientVerificationServiceTest {

    private static final Instant START = Instant.parse("2026-09-12T10:00:00Z");
    private static final ClientVerificationSubject SUBJECT = ClientVerificationSubject.order("store-1", "order-1");
    private static final String EMAIL = "jan@example.com";

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Mock
    private ClientVerificationsRepository repository;
    @Mock
    private EmailClient emailClient;

    private ClientVerificationService service;

    @BeforeEach
    void setUp() {
        service = new ClientVerificationService(repository, emailClient, clock, null);
        when(repository.findBySubjectKey(SUBJECT.getKey())).thenReturn(List.of());
        when(emailClient.send(eq("store-1"), eq(EmailNotificationType.CLIENT_VERIFICATION_CODE), any(EmailNotification.class)))
                .thenReturn(true);
    }

    @Test
    @DisplayName("issue e-mails a six character code and stores only its hash with a ten minute expiry")
    void issueSendsCodeAndStoresHashedRecord() {
        // when
        String verificationId = service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");

        // then
        ClientVerificationEmailNotification sent = sentNotification();
        assertThat(sent.getCode()).matches("[A-HJ-KM-NP-Z2-9]{6}");
        assertThat(sent.getRecipientEmail()).isEqualTo(EMAIL);
        assertThat(sent.getOrderId()).isEqualTo("order-1");
        assertThat(sent.getExpiresInMinutes()).isEqualTo(10);

        ClientVerification saved = savedVerification();
        assertThat(saved.getVerificationId()).isEqualTo(verificationId);
        assertThat(saved.getSubjectKey()).isEqualTo("ORDER#store-1#order-1");
        assertThat(saved.getPurpose()).isEqualTo(ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE);
        assertThat(saved.getCodeHash()).doesNotContain(sent.getCode()).hasSize(64);
        assertThat(saved.getExpiresAt()).isEqualTo(localNow().plusMinutes(10));
        assertThat(saved.getTtl()).isEqualTo(localNow().plusHours(24).toEpochSecond(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("issue uses the configured fixed code when one is set for the environment")
    void issueUsesFixedCodeWhenConfigured() {
        // given
        ClientVerificationService fixed = new ClientVerificationService(repository, emailClient, clock, "123456");

        // when
        String verificationId = fixed.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");

        // then
        assertThat(sentNotification().getCode()).isEqualTo("123456");
        ClientVerification stored = savedVerification();
        when(repository.findById(SUBJECT.getKey(), verificationId)).thenReturn(Optional.of(stored));
        assertThat(fixed.confirm(SUBJECT, verificationId, "123456")).isNotBlank();
    }

    @Test
    @DisplayName("confirm accepts the code regardless of letter case and surrounding whitespace")
    void confirmIgnoresCaseAndWhitespace() {
        // given
        String verificationId = service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");
        ClientVerification stored = savedVerification();
        when(repository.findById(SUBJECT.getKey(), verificationId)).thenReturn(Optional.of(stored));
        String typed = " " + sentNotification().getCode().toLowerCase() + " ";

        // when
        String editToken = service.confirm(SUBJECT, verificationId, typed);

        // then
        assertThat(editToken).isNotBlank();
    }

    @Test
    @DisplayName("issue refuses a fourth code within one hour for the same subject")
    void issueRefusesWhenThreeCodesWereIssuedWithinAnHour() {
        // given
        when(repository.findBySubjectKey(SUBJECT.getKey())).thenReturn(List.of(
                issuedAt(localNow().minusMinutes(50)),
                issuedAt(localNow().minusMinutes(30)),
                issuedAt(localNow().minusMinutes(5))));

        // when / then
        assertThatThrownBy(() -> service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan"))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.TOO_MANY_REQUESTS);
        verify(emailClient, never()).send(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("issue ignores codes issued more than an hour ago when counting the limit")
    void issueCountsOnlyLastHour() {
        // given
        when(repository.findBySubjectKey(SUBJECT.getKey())).thenReturn(List.of(
                issuedAt(localNow().minusMinutes(90)),
                issuedAt(localNow().minusMinutes(70)),
                issuedAt(localNow().minusMinutes(5))));

        // when
        service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");

        // then
        verify(repository).save(any(ClientVerification.class));
    }

    @Test
    @DisplayName("issue does not store a record and fails when the e-mail could not be sent")
    void issueFailsWithoutSavingWhenEmailNotSent() {
        // given
        when(emailClient.send(any(), any(), any())).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan"))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.EMAIL_NOT_SENT);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("confirm with the e-mailed code returns an edit token valid for fifteen minutes")
    void confirmWithCorrectCodeReturnsEditToken() {
        // given
        String verificationId = service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");
        ClientVerification stored = savedVerification();
        when(repository.findById(SUBJECT.getKey(), verificationId)).thenReturn(Optional.of(stored));

        // when
        String editToken = service.confirm(SUBJECT, verificationId, sentNotification().getCode());

        // then
        assertThat(editToken).isNotBlank();
        assertThat(stored.getEditTokenHash()).isNotNull().doesNotContain(editToken);
        assertThat(stored.getEditTokenExpiresAt()).isEqualTo(localNow().plusMinutes(15));
        assertThat(stored.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("confirm charges the attempt before comparing and rejects the code when the charge loses a concurrent write")
    void confirmRejectsCodeWhenAttemptChargeConflicts() {
        // given
        String verificationId = service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");
        ClientVerification stored = savedVerification();
        when(repository.findById(SUBJECT.getKey(), verificationId)).thenReturn(Optional.of(stored));
        doThrow(new ConditionalCheckFailedException("stale version")).when(repository).save(stored);

        // when / then
        assertThatThrownBy(() -> service.confirm(SUBJECT, verificationId, sentNotification().getCode()))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.INVALID_CODE);
        assertThat(stored.isCodeConfirmed()).isFalse();
        verify(repository, times(2)).save(any(ClientVerification.class));
    }

    @Test
    @DisplayName("consume rejects the token when marking it consumed loses a concurrent write")
    void consumeRejectsTokenWhenConsumeConflicts() {
        // given
        ClientVerification stored = confirmedVerification();
        String editToken = service.confirm(SUBJECT, stored.getVerificationId(), sentNotification().getCode());
        when(repository.findBySubjectKey(SUBJECT.getKey())).thenReturn(List.of(stored));
        doThrow(new ConditionalCheckFailedException("stale version")).when(repository).save(stored);

        // when / then
        assertThatThrownBy(() -> service.consume(SUBJECT, editToken))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.INVALID_TOKEN);
    }

    @Test
    @DisplayName("confirm with a wrong code increments attempts, persists them and fails")
    void confirmWithWrongCodeIncrementsAttempts() {
        // given
        String verificationId = service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");
        ClientVerification stored = savedVerification();
        when(repository.findById(SUBJECT.getKey(), verificationId)).thenReturn(Optional.of(stored));
        String wrongCode = sentNotification().getCode().equals("000000") ? "000001" : "000000";

        // when / then
        assertThatThrownBy(() -> service.confirm(SUBJECT, verificationId, wrongCode))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.INVALID_CODE);
        assertThat(stored.getAttempts()).isEqualTo(1);
        assertThat(stored.isCodeConfirmed()).isFalse();
    }

    @Test
    @DisplayName("confirm refuses even the correct code after five failed attempts")
    void confirmRefusesAfterFiveFailedAttempts() {
        // given
        String verificationId = service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");
        ClientVerification stored = savedVerification();
        when(repository.findById(SUBJECT.getKey(), verificationId)).thenReturn(Optional.of(stored));
        for (int i = 0; i < 5; i++) {
            stored.registerAttempt();
        }

        // when / then
        assertThatThrownBy(() -> service.confirm(SUBJECT, verificationId, sentNotification().getCode()))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.TOO_MANY_ATTEMPTS);
        assertThat(stored.isCodeConfirmed()).isFalse();
    }

    @Test
    @DisplayName("confirm refuses a code older than ten minutes")
    void confirmRefusesExpiredCode() {
        // given
        String verificationId = service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");
        ClientVerification stored = savedVerification();
        when(repository.findById(SUBJECT.getKey(), verificationId)).thenReturn(Optional.of(stored));
        now.set(START.plusSeconds(10 * 60));

        // when / then
        assertThatThrownBy(() -> service.confirm(SUBJECT, verificationId, sentNotification().getCode()))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.CODE_EXPIRED);
    }

    @Test
    @DisplayName("confirm fails for an unknown verification id")
    void confirmFailsForUnknownVerification() {
        // given
        when(repository.findById(SUBJECT.getKey(), "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.confirm(SUBJECT, "missing", "123456"))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.NOT_FOUND);
    }

    @Test
    @DisplayName("consume accepts the edit token once and marks the record as consumed")
    void consumeAcceptsEditTokenOnlyOnce() {
        // given
        ClientVerification stored = confirmedVerification();
        String editToken = service.confirm(SUBJECT, stored.getVerificationId(), sentNotification().getCode());
        when(repository.findBySubjectKey(SUBJECT.getKey())).thenReturn(List.of(stored));

        // when
        ClientVerification consumed = service.consume(SUBJECT, editToken);

        // then
        assertThat(consumed).isSameAs(stored);
        assertThat(stored.getConsumedAt()).isEqualTo(localNow());
        assertThatThrownBy(() -> service.consume(SUBJECT, editToken))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.INVALID_TOKEN);
    }

    @Test
    @DisplayName("consume refuses an edit token older than fifteen minutes")
    void consumeRefusesExpiredEditToken() {
        // given
        ClientVerification stored = confirmedVerification();
        String editToken = service.confirm(SUBJECT, stored.getVerificationId(), sentNotification().getCode());
        when(repository.findBySubjectKey(SUBJECT.getKey())).thenReturn(List.of(stored));
        now.set(START.plusSeconds(15 * 60));

        // when / then
        assertThatThrownBy(() -> service.consume(SUBJECT, editToken))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.INVALID_TOKEN);
        assertThat(stored.isConsumed()).isFalse();
    }

    @Test
    @DisplayName("consume refuses a token that does not match any record of the subject")
    void consumeRefusesForeignToken() {
        // given
        ClientVerification stored = confirmedVerification();
        service.confirm(SUBJECT, stored.getVerificationId(), sentNotification().getCode());
        when(repository.findBySubjectKey(SUBJECT.getKey())).thenReturn(List.of(stored));

        // when / then
        assertThatThrownBy(() -> service.consume(SUBJECT, "not-the-token"))
                .isInstanceOf(ClientVerificationException.class)
                .extracting("reason").isEqualTo(Reason.INVALID_TOKEN);
        assertThat(service.findByEditToken(SUBJECT, "")).isEmpty();
    }

    @Test
    @DisplayName("findByEditToken returns the record without consuming it")
    void findByEditTokenDoesNotConsume() {
        // given
        ClientVerification stored = confirmedVerification();
        String editToken = service.confirm(SUBJECT, stored.getVerificationId(), sentNotification().getCode());
        when(repository.findBySubjectKey(SUBJECT.getKey())).thenReturn(List.of(stored));

        // when
        Optional<ClientVerification> found = service.findByEditToken(SUBJECT, editToken);

        // then
        assertThat(found).contains(stored);
        assertThat(stored.isConsumed()).isFalse();
    }

    private ClientVerification confirmedVerification() {
        String verificationId = service.issue(SUBJECT, ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE, EMAIL, "Jan");
        ClientVerification stored = savedVerification();
        when(repository.findById(SUBJECT.getKey(), verificationId)).thenReturn(Optional.of(stored));
        return stored;
    }

    private ClientVerificationEmailNotification sentNotification() {
        ArgumentCaptor<EmailNotification> captor = ArgumentCaptor.forClass(EmailNotification.class);
        verify(emailClient).send(eq("store-1"), eq(EmailNotificationType.CLIENT_VERIFICATION_CODE), captor.capture());
        return (ClientVerificationEmailNotification) captor.getValue();
    }

    private ClientVerification savedVerification() {
        ArgumentCaptor<ClientVerification> captor = ArgumentCaptor.forClass(ClientVerification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private LocalDateTime localNow() {
        return LocalDateTime.now(clock);
    }

    private static ClientVerification issuedAt(LocalDateTime createdAt) {
        ClientVerification verification = new ClientVerification();
        verification.setSubjectKey(SUBJECT.getKey());
        verification.setCreatedAt(createdAt);
        return verification;
    }
}
