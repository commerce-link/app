package pl.commercelink.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.stores.DemoStoreMetadata;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "app.demo.registration.enabled", havingValue = "true")
public class DemoRegistrationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final DemoUserService demoUserService;
    private final DemoStoreSeeder demoStoreSeeder;
    private final DemoStoreDeletionService demoStoreDeletionService;
    private final DemoRegistrationRateLimiter rateLimiter;
    private final Clock clock;
    private final int ttlDays;
    private final boolean revealPassword;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public DemoRegistrationService(DemoUserService demoUserService,
                                   DemoStoreSeeder demoStoreSeeder,
                                   DemoStoreDeletionService demoStoreDeletionService,
                                   DemoRegistrationRateLimiter rateLimiter,
                                   @Value("${app.demo.registration.ttl-days}") int ttlDays,
                                   @Value("${app.demo.registration.reveal-password}") boolean revealPassword) {
        this(demoUserService, demoStoreSeeder, demoStoreDeletionService, rateLimiter, Clock.systemUTC(), ttlDays, revealPassword);
    }

    DemoRegistrationService(DemoUserService demoUserService,
                            DemoStoreSeeder demoStoreSeeder,
                            DemoStoreDeletionService demoStoreDeletionService,
                            DemoRegistrationRateLimiter rateLimiter,
                            Clock clock,
                            int ttlDays,
                            boolean revealPassword) {
        this.demoUserService = demoUserService;
        this.demoStoreSeeder = demoStoreSeeder;
        this.demoStoreDeletionService = demoStoreDeletionService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.ttlDays = ttlDays;
        this.revealPassword = revealPassword;
    }

    public DemoRegistrationResult register(String email, String clientIp) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new DemoRegistrationException(DemoRegistrationException.Reason.INVALID_EMAIL);
        }
        if (!rateLimiter.tryAcquire(clientIp)) {
            throw new DemoRegistrationException(DemoRegistrationException.Reason.RATE_LIMITED);
        }
        if (demoUserService.userExists(normalized)) {
            throw new DemoRegistrationException(DemoRegistrationException.Reason.EMAIL_EXISTS);
        }

        String storeId = UniqueIdentifierGenerator.generate();
        Instant now = clock.instant();
        DemoStoreMetadata metadata = new DemoStoreMetadata(normalized, now.toString(),
                now.plus(ttlDays, ChronoUnit.DAYS).toString());

        try {
            demoStoreSeeder.seedStore(storeId, "Sklep demo — " + normalized, metadata);
            if (revealPassword) {
                String password = generatePassword();
                demoUserService.createDemoAdmin(normalized, storeId, password);
                return new DemoRegistrationResult(storeId, password);
            }
            demoUserService.createDemoAdmin(normalized, storeId);
            return new DemoRegistrationResult(storeId, null);
        } catch (RuntimeException e) {
            System.err.println("[DemoRegistration] User creation failed for " + normalized + ", rolling back store " + storeId + ": " + e.getMessage());
            rollBack(storeId);
            throw new DemoRegistrationException(DemoRegistrationException.Reason.CREATION_FAILED);
        }
    }

    private void rollBack(String storeId) {
        try {
            demoStoreDeletionService.deleteDemoStore(storeId);
        } catch (RuntimeException e) {
            System.err.println("[DemoRegistration] Rollback failed for store " + storeId + ": " + e.getMessage());
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
