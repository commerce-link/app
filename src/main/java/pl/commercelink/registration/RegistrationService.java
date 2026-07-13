package pl.commercelink.registration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.demo.DemoStoreSeeder;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreSeedingException;
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
    private static final int STORE_NAME_MIN = 2;
    private static final int STORE_NAME_MAX = 60;

    private final CognitoUserService cognitoUserService;
    private final DemoStoreSeeder demoStoreSeeder;
    private final StoreCreationService storeCreationService;
    private final StoreDeletionService storeDeletionService;
    private final RegistrationRateLimiter rateLimiter;
    private final Clock clock;
    private final int ttlDays;
    private final boolean revealPassword;
    private final boolean demoMode;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RegistrationService(CognitoUserService cognitoUserService,
                                DemoStoreSeeder demoStoreSeeder,
                                StoreCreationService storeCreationService,
                                StoreDeletionService storeDeletionService,
                                RegistrationRateLimiter rateLimiter,
                                @Value("${app.registration.ttl-days}") int ttlDays,
                                @Value("${app.registration.reveal-password}") boolean revealPassword,
                                @Value("${app.registration.demo}") boolean demoMode) {
        this(cognitoUserService, demoStoreSeeder, storeCreationService, storeDeletionService, rateLimiter,
                Clock.systemUTC(), ttlDays, revealPassword, demoMode);
    }

    RegistrationService(CognitoUserService cognitoUserService,
                        DemoStoreSeeder demoStoreSeeder,
                        StoreCreationService storeCreationService,
                        StoreDeletionService storeDeletionService,
                        RegistrationRateLimiter rateLimiter,
                        Clock clock,
                        int ttlDays,
                        boolean revealPassword,
                        boolean demoMode) {
        this.cognitoUserService = cognitoUserService;
        this.demoStoreSeeder = demoStoreSeeder;
        this.storeCreationService = storeCreationService;
        this.storeDeletionService = storeDeletionService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.ttlDays = ttlDays;
        this.revealPassword = revealPassword;
        this.demoMode = demoMode;
    }

    public RegistrationResult register(String email, String storeName, String clientIp) {
        String normalized = normalize(email);
        validate(normalized, clientIp);
        String name = validatedStoreName(storeName);
        if (cognitoUserService.userExists(normalized)) {
            throw new RegistrationException(RegistrationException.Reason.EMAIL_EXISTS);
        }

        return demoMode ? registerDemo(normalized, name) : registerProduction(normalized, name);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void validate(String normalized, String clientIp) {
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_EMAIL);
        }
        if (!rateLimiter.tryAcquire(clientIp)) {
            throw new RegistrationException(RegistrationException.Reason.RATE_LIMITED);
        }
    }

    private static String validatedStoreName(String storeName) {
        String trimmed = storeName == null ? "" : storeName.trim();
        if (trimmed.length() < STORE_NAME_MIN || trimmed.length() > STORE_NAME_MAX) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_STORE_NAME);
        }
        return trimmed;
    }

    private RegistrationResult registerProduction(String email, String storeName) {
        Store store = storeCreationService.createStore(CreateStoreRequest.bare(storeName, null));
        try {
            cognitoUserService.createStoreAdmin(email, store.getStoreId());
            return new RegistrationResult(store.getStoreId(), null);
        } catch (RuntimeException e) {
            System.err.println("[Registration] User creation failed for " + email + ", rolling back store " + store.getStoreId() + ": " + e.getMessage());
            rollBack(store.getStoreId(), StoreDeletionService.Guard.ANY);
            throw new RegistrationException(RegistrationException.Reason.CREATION_FAILED);
        }
    }

    private RegistrationResult registerDemo(String email, String storeName) {
        Instant now = clock.instant();
        DemoStoreMetadata metadata = new DemoStoreMetadata(email, now.toString(),
                now.plus(ttlDays, ChronoUnit.DAYS).toString());
        Store store;
        try {
            store = storeCreationService.createStore(
                    CreateStoreRequest.seeded(storeName, metadata, demoStoreSeeder));
        } catch (StoreSeedingException e) {
            System.err.println("[Registration] Store seeding failed for " + email + ", rolling back store " + e.getStoreId() + ": " + e.getMessage());
            rollBack(e.getStoreId(), StoreDeletionService.Guard.DEMO_ONLY);
            throw new RegistrationException(RegistrationException.Reason.CREATION_FAILED);
        }
        try {
            if (revealPassword) {
                String password = generatePassword();
                cognitoUserService.createStoreAdmin(email, store.getStoreId(), password);
                return new RegistrationResult(store.getStoreId(), password);
            }
            cognitoUserService.createStoreAdmin(email, store.getStoreId());
            return new RegistrationResult(store.getStoreId(), null);
        } catch (RuntimeException e) {
            System.err.println("[Registration] User creation failed for " + email + ", rolling back store " + store.getStoreId() + ": " + e.getMessage());
            rollBack(store.getStoreId(), StoreDeletionService.Guard.DEMO_ONLY);
            throw new RegistrationException(RegistrationException.Reason.CREATION_FAILED);
        }
    }

    private void rollBack(String storeId, StoreDeletionService.Guard guard) {
        try {
            storeDeletionService.deleteStore(storeId, guard);
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
