package pl.commercelink.registration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreSeeder;
import pl.commercelink.stores.StoreSeedingException;
import pl.commercelink.users.CognitoUserService;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "app.registration.enabled", havingValue = "true")
public class RegistrationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int STORE_NAME_MIN = 2;
    private static final int STORE_NAME_MAX = 60;

    private final CognitoUserService cognitoUserService;
    private final StoreSeeder storeSeeder;
    private final StoreCreationService storeCreationService;
    private final StoreDeletionService storeDeletionService;
    private final RegistrationRateLimiter rateLimiter;
    private final Clock clock;
    private final int ttlDays;
    private final boolean demoMode;

    @Autowired
    public RegistrationService(CognitoUserService cognitoUserService,
                                StoreSeeder storeSeeder,
                                StoreCreationService storeCreationService,
                                StoreDeletionService storeDeletionService,
                                RegistrationRateLimiter rateLimiter,
                                @Value("${app.registration.ttl-days}") int ttlDays,
                                @Value("${app.registration.demo:false}") boolean demoMode) {
        this(cognitoUserService, storeSeeder, storeCreationService, storeDeletionService, rateLimiter,
                Clock.systemUTC(), ttlDays, demoMode);
    }

    RegistrationService(CognitoUserService cognitoUserService,
                        StoreSeeder storeSeeder,
                        StoreCreationService storeCreationService,
                        StoreDeletionService storeDeletionService,
                        RegistrationRateLimiter rateLimiter,
                        Clock clock,
                        int ttlDays,
                        boolean demoMode) {
        this.cognitoUserService = cognitoUserService;
        this.storeSeeder = storeSeeder;
        this.storeCreationService = storeCreationService;
        this.storeDeletionService = storeDeletionService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.ttlDays = ttlDays;
        this.demoMode = demoMode;
    }

    public String validateCandidate(String email, String storeName) {
        String normalized = normalize(email);
        validateEmail(normalized);
        validatedStoreName(storeName);
        if (cognitoUserService.userExists(normalized)) {
            throw new RegistrationException(RegistrationException.Reason.EMAIL_EXISTS);
        }
        return normalized;
    }

    public RegistrationResult register(String email, String storeName, String clientIp, String password) {
        String normalized = normalize(email);
        validateEmail(normalized);
        String name = validatedStoreName(storeName);
        acquireRateLimit(clientIp);
        if (cognitoUserService.userExists(normalized)) {
            throw new RegistrationException(RegistrationException.Reason.EMAIL_EXISTS);
        }

        return demoMode ? registerDemo(normalized, name, password) : registerProduction(normalized, name, password);
    }

    public boolean isEmailVerifiedOnCreation() {
        return demoMode;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String normalized) {
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_EMAIL);
        }
    }

    private void acquireRateLimit(String clientIp) {
        if (!rateLimiter.tryAcquire(clientIp)) {
            throw new RegistrationException(RegistrationException.Reason.RATE_LIMITED);
        }
    }

    private String validatedStoreName(String storeName) {
        String trimmed = storeName == null ? "" : storeName.trim();
        if (!demoMode && trimmed.isEmpty()) {
            throw new RegistrationException(RegistrationException.Reason.STORE_NAME_REQUIRED);
        }
        if (trimmed.length() < STORE_NAME_MIN || trimmed.length() > STORE_NAME_MAX) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_STORE_NAME);
        }
        return trimmed;
    }

    private RegistrationResult registerProduction(String email, String storeName, String password) {
        Store store;
        try {
            store = storeCreationService.createStore(CreateStoreRequest.registered(storeName, email));
        } catch (RuntimeException e) {
            System.err.println("[Registration] Store creation failed for " + email + ": " + e.getMessage());
            throw new RegistrationException(RegistrationException.Reason.CREATION_FAILED);
        }
        return createAdmin(email, store.getStoreId(), password, StoreDeletionService.Guard.ANY);
    }

    private RegistrationResult registerDemo(String email, String storeName, String password) {
        Instant now = clock.instant();
        DemoStoreMetadata metadata = new DemoStoreMetadata(email, now.toString(),
                now.plus(ttlDays, ChronoUnit.DAYS).toString());
        Store store;
        try {
            store = storeCreationService.createStore(
                    CreateStoreRequest.seeded(storeName, metadata, storeSeeder));
        } catch (StoreSeedingException e) {
            System.err.println("[Registration] Store seeding failed for " + email + ", rolling back store " + e.getStoreId() + ": " + e.getMessage());
            rollBack(e.getStoreId(), StoreDeletionService.Guard.DEMO_ONLY);
            throw new RegistrationException(RegistrationException.Reason.CREATION_FAILED);
        } catch (RuntimeException e) {
            System.err.println("[Registration] Store creation failed for " + email + ": " + e.getMessage());
            throw new RegistrationException(RegistrationException.Reason.CREATION_FAILED);
        }
        return createAdmin(email, store.getStoreId(), password, StoreDeletionService.Guard.DEMO_ONLY);
    }

    private RegistrationResult createAdmin(String email, String storeId, String password,
                                           StoreDeletionService.Guard rollbackGuard) {
        try {
            cognitoUserService.createStoreAdmin(email, storeId, password, demoMode);
            return new RegistrationResult(storeId);
        } catch (RuntimeException e) {
            System.err.println("[Registration] User creation failed for " + email + ", rolling back store " + storeId + ": " + e.getMessage());
            rollBack(storeId, rollbackGuard);
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
}
