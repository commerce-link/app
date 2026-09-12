package pl.commercelink.clientaccess;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.clientaccess.ClientVerificationException.Reason;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.starter.email.EmailClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClientVerificationService {

    static final int CODE_VALIDITY_MINUTES = 10;
    static final int EDIT_TOKEN_VALIDITY_MINUTES = 15;
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_ISSUES_PER_HOUR = 3;
    static final int RECORD_TTL_HOURS = 24;

    private final ClientVerificationsRepository repository;
    private final EmailClient emailClient;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public ClientVerificationService(ClientVerificationsRepository repository, EmailClient emailClient) {
        this(repository, emailClient, Clock.systemUTC());
    }

    ClientVerificationService(ClientVerificationsRepository repository, EmailClient emailClient, Clock clock) {
        this.repository = repository;
        this.emailClient = emailClient;
        this.clock = clock;
    }

    public String issue(ClientVerificationSubject subject, ClientVerificationPurpose purpose, String recipientEmail, String recipientName) {
        LocalDateTime now = now();
        LocalDateTime hourAgo = now.minusHours(1);
        long issuedLastHour = repository.findBySubjectKey(subject.getKey()).stream()
                .filter(v -> v.wasCreatedAfter(hourAgo))
                .count();
        if (issuedLastHour >= MAX_ISSUES_PER_HOUR) {
            throw new ClientVerificationException(Reason.TOO_MANY_REQUESTS);
        }

        String verificationId = UUID.randomUUID().toString();
        String code = String.format("%06d", random.nextInt(1_000_000));
        ClientVerification verification = new ClientVerification(
                subject.getKey(),
                verificationId,
                purpose,
                hashCode(verificationId, code),
                now,
                now.plusMinutes(CODE_VALIDITY_MINUTES),
                now.plusHours(RECORD_TTL_HOURS).toEpochSecond(ZoneOffset.UTC));

        ClientVerificationEmailNotification msg = new ClientVerificationEmailNotification(
                recipientEmail, recipientName, code, CODE_VALIDITY_MINUTES, subject);
        if (!emailClient.send(subject.getStoreId(), EmailNotificationType.CLIENT_VERIFICATION_CODE, msg)) {
            throw new ClientVerificationException(Reason.EMAIL_NOT_SENT);
        }

        repository.save(verification);
        return verificationId;
    }

    public String confirm(ClientVerificationSubject subject, String verificationId, String code) {
        LocalDateTime now = now();
        ClientVerification verification = repository.findById(subject.getKey(), verificationId)
                .orElseThrow(() -> new ClientVerificationException(Reason.NOT_FOUND));

        if (verification.isCodeConfirmed() || verification.isConsumed()) {
            throw new ClientVerificationException(Reason.NOT_FOUND);
        }
        if (verification.isCodeExpired(now)) {
            throw new ClientVerificationException(Reason.CODE_EXPIRED);
        }
        if (!verification.hasAttemptsLeft(MAX_ATTEMPTS)) {
            throw new ClientVerificationException(Reason.TOO_MANY_ATTEMPTS);
        }

        // Charge the attempt before comparing: with optimistic locking only one of concurrent
        // requests wins the write, so every comparison result costs exactly one attempt.
        verification.registerAttempt();
        saveOrThrow(verification, Reason.INVALID_CODE);
        if (!digestsMatch(verification.getCodeHash(), hashCode(verificationId, code))) {
            throw new ClientVerificationException(Reason.INVALID_CODE);
        }

        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String editToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        verification.confirmCode(hashToken(editToken), now.plusMinutes(EDIT_TOKEN_VALIDITY_MINUTES));
        saveOrThrow(verification, Reason.INVALID_CODE);
        return editToken;
    }

    public Optional<ClientVerification> findByEditToken(ClientVerificationSubject subject, String editToken) {
        if (editToken == null || editToken.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime now = now();
        String tokenHash = hashToken(editToken);
        return repository.findBySubjectKey(subject.getKey()).stream()
                .filter(v -> v.isEditTokenValid(now))
                .filter(v -> digestsMatch(v.getEditTokenHash(), tokenHash))
                .findFirst();
    }

    public ClientVerification consume(ClientVerificationSubject subject, String editToken) {
        ClientVerification verification = findByEditToken(subject, editToken)
                .orElseThrow(() -> new ClientVerificationException(Reason.INVALID_TOKEN));
        verification.consume(now());
        saveOrThrow(verification, Reason.INVALID_TOKEN);
        return verification;
    }

    private void saveOrThrow(ClientVerification verification, Reason onConflict) {
        try {
            repository.save(verification);
        } catch (ConditionalCheckFailedException e) {
            throw new ClientVerificationException(onConflict);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String hashCode(String verificationId, String code) {
        return sha256(verificationId + code);
    }

    private static String hashToken(String editToken) {
        return sha256(editToken);
    }

    private static boolean digestsMatch(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8), actualHex.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
