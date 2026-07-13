package pl.commercelink.registration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.demo.DemoStoreSeeder;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.users.CognitoUserService;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "app.registration.enabled", havingValue = "true")
public class RegistrationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final CognitoUserService cognitoUserService;
    private final DemoStoreSeeder demoStoreSeeder;
    private final StoreDeletionService storeDeletionService;
    private final RegistrationRateLimiter rateLimiter;
    private final Clock clock;
    private final int ttlDays;
    private final boolean revealPassword;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RegistrationService(CognitoUserService cognitoUserService,
                                DemoStoreSeeder demoStoreSeeder,
                                StoreDeletionService storeDeletionService,
                                RegistrationRateLimiter rateLimiter,
                                @Value("${app.registration.ttl-days}") int ttlDays,
                                @Value("${app.registration.reveal-password}") boolean revealPassword) {
        this(cognitoUserService, demoStoreSeeder, storeDeletionService, rateLimiter, Clock.systemUTC(), ttlDays, revealPassword);
    }

    RegistrationService(CognitoUserService cognitoUserService,
                        DemoStoreSeeder demoStoreSeeder,
                        StoreDeletionService storeDeletionService,
                        RegistrationRateLimiter rateLimiter,
                        Clock clock,
                        int ttlDays,
                        boolean revealPassword) {
        this.cognitoUserService = cognitoUserService;
        this.demoStoreSeeder = demoStoreSeeder;
        this.storeDeletionService = storeDeletionService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.ttlDays = ttlDays;
        this.revealPassword = revealPassword;
    }

    public RegistrationResult register(String email, String clientIp) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_EMAIL);
        }
        if (!rateLimiter.tryAcquire(clientIp)) {
            throw new RegistrationException(RegistrationException.Reason.RATE_LIMITED);
        }
        if (cognitoUserService.userExists(normalized)) {
            throw new RegistrationException(RegistrationException.Reason.EMAIL_EXISTS);
        }

        String storeId = UniqueIdentifierGenerator.generate();
        Instant now = clock.instant();
        DemoStoreMetadata metadata = new DemoStoreMetadata(normalized, now.toString(),
                now.plus(ttlDays, ChronoUnit.DAYS).toString());

        try {
            demoStoreSeeder.seedStore(storeId, "Sklep demo — " + normalized, metadata);
            if (revealPassword) {
                String password = generatePassword();
                cognitoUserService.createStoreAdmin(normalized, storeId, password);
                return new RegistrationResult(storeId, password);
            }
            cognitoUserService.createStoreAdmin(normalized, storeId);
            return new RegistrationResult(storeId, null);
        } catch (RuntimeException e) {
            System.err.println("[Registration] User creation failed for " + normalized + ", rolling back store " + storeId + ": " + e.getMessage());
            rollBack(storeId);
            throw new RegistrationException(RegistrationException.Reason.CREATION_FAILED);
        }
    }

    private void rollBack(String storeId) {
        try {
            storeDeletionService.deleteDemoStore(storeId);
        } catch (RuntimeException e) {
            System.err.println("[Registration] Rollback failed for store " + storeId + ": " + e.getMessage());
        }
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder("Demo1!");
        for (int i = 0; i < 10; i++) {
            password.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
