package pl.commercelink.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreSeeder;
import pl.commercelink.stores.StoreSeedingException;
import pl.commercelink.stores.TrialPeriod;
import pl.commercelink.users.CognitoUserService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-08T10:00:00Z");
    private static final String PASSWORD = "Tajne1!haslo";
    private static final int TRIAL_DAYS = 21;

    @Mock private CognitoUserService cognitoUserService;
    @Mock private StoreSeeder storeSeeder;
    @Mock private StoreCreationService storeCreationService;
    @Mock private StoreDeletionService storeDeletionService;
    @Mock private RegistrationRateLimiter rateLimiter;

    private RegistrationService service(boolean demoMode) {
        return new RegistrationService(cognitoUserService, storeSeeder, storeCreationService, storeDeletionService,
                rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC), 14, TRIAL_DAYS, demoMode);
    }

    private static TrialPeriod trial(String ownerEmail) {
        return TrialPeriod.starting(ownerEmail, NOW, TRIAL_DAYS);
    }

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void createsSeededStoreAndVerifiedAdminInDemoMode() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("demo-store-1"));
        ArgumentCaptor<CreateStoreRequest> requestCaptor = ArgumentCaptor.forClass(CreateStoreRequest.class);

        // when
        RegistrationResult result = service(true).register("User@Example.com ", "Sklep Testowy", "1.1.1.1", PASSWORD);

        // then
        verify(storeCreationService).createStore(requestCaptor.capture());
        assertEquals("Sklep Testowy", requestCaptor.getValue().name());
        assertSame(storeSeeder, requestCaptor.getValue().seeder());
        assertEquals("user@example.com", requestCaptor.getValue().demoMetadata().getOwnerEmail());
        assertEquals(NOW.toString(), requestCaptor.getValue().demoMetadata().getCreatedAt());
        assertEquals(NOW.plusSeconds(14 * 24 * 3600).toString(), requestCaptor.getValue().demoMetadata().getExpiresAt());
        assertNull(requestCaptor.getValue().trial());
        verify(cognitoUserService).createStoreAdmin("user@example.com", "demo-store-1", PASSWORD, true);
        assertEquals("demo-store-1", result.storeId());
    }

    @Test
    void createsUnverifiedAdminInProductionMode() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.registered("Moja Firma", trial("user@firma.pl"))))
                .thenReturn(store("prod-store-1"));

        // when
        RegistrationResult result = service(false).register("user@firma.pl", "Moja Firma", "10.0.0.1", PASSWORD);

        // then
        assertEquals("prod-store-1", result.storeId());
        verify(cognitoUserService).createStoreAdmin("user@firma.pl", "prod-store-1", PASSWORD, false);
        verifyNoInteractions(storeSeeder);
    }

    @Test
    void createsEmptyTrialStoreEndingAfterTrialDays() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("prod-store-1"));
        ArgumentCaptor<CreateStoreRequest> requestCaptor = ArgumentCaptor.forClass(CreateStoreRequest.class);

        // when
        service(false).register("user@firma.pl", "Moja Firma", "10.0.0.1", PASSWORD);

        // then
        verify(storeCreationService).createStore(requestCaptor.capture());
        CreateStoreRequest request = requestCaptor.getValue();
        assertEquals("user@firma.pl", request.trial().getOwnerEmail());
        assertEquals(NOW.toString(), request.trial().getStartedAt());
        assertEquals(NOW.plusSeconds(TRIAL_DAYS * 24 * 3600L).toString(), request.trial().getExpiresAt());
        assertEquals("user@firma.pl", request.ownerEmail());
        assertNull(request.seeder());
        assertNull(request.demoMetadata());
    }

    @Test
    void reportsWhetherEmailIsVerifiedOnCreation() {
        // when / then
        assertTrue(service(true).isEmailVerifiedOnCreation());
        assertFalse(service(false).isEmailVerifiedOnCreation());
    }

    @Test
    void validateCandidateNormalisesEmailWithoutCreatingAnything() {
        // given
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);

        // when
        String normalized = service(true).validateCandidate("  User@Example.COM ", "Sklep Testowy");

        // then
        assertEquals("user@example.com", normalized);
        verifyNoInteractions(storeCreationService, rateLimiter);
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void validateCandidateRejectsTakenEmail() {
        // given
        when(cognitoUserService.userExists("user@example.com")).thenReturn(true);

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true).validateCandidate("user@example.com", "Sklep Testowy"));
        assertEquals(RegistrationException.Reason.EMAIL_EXISTS, e.getReason());
        verifyNoInteractions(storeCreationService, rateLimiter);
    }

    @Test
    void rejectsInvalidEmail() {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true).register("not-an-email", "Sklep Testowy", "1.1.1.1", PASSWORD));
        assertEquals(RegistrationException.Reason.INVALID_EMAIL, e.getReason());
        verifyNoInteractions(storeSeeder, cognitoUserService, storeCreationService);
    }

    @Test
    void rejectsWhenRateLimited() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(false);

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true).register("user@example.com", "Sklep Testowy", "1.1.1.1", PASSWORD));
        assertEquals(RegistrationException.Reason.RATE_LIMITED, e.getReason());
        verifyNoInteractions(storeCreationService);
    }

    @Test
    void rejectsExistingEmail() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(true);

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true).register("user@example.com", "Sklep Testowy", "1.1.1.1", PASSWORD));
        assertEquals(RegistrationException.Reason.EMAIL_EXISTS, e.getReason());
        verifyNoInteractions(storeCreationService);
    }

    @Test
    void rollsBackStoreWhenUserCreationFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("demo-store-1"));
        doThrow(new RuntimeException("cognito down"))
                .when(cognitoUserService).createStoreAdmin(anyString(), anyString(), anyString(), anyBoolean());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true).register("user@example.com", "Sklep Testowy", "1.1.1.1", PASSWORD));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService).deleteStore("demo-store-1", StoreDeletionService.Guard.DEMO_ONLY);
    }

    @Test
    void productionModeRollsBackWithAnyGuard() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.registered("Moja Firma", trial("user@firma.pl"))))
                .thenReturn(store("prod-store-1"));
        doThrow(new RuntimeException("cognito down"))
                .when(cognitoUserService).createStoreAdmin(anyString(), anyString(), anyString(), anyBoolean());

        // when / then
        assertThrows(RegistrationException.class,
                () -> service(false).register("user@firma.pl", "Moja Firma", "10.0.0.1", PASSWORD));
        verify(storeDeletionService).deleteStore("prod-store-1", StoreDeletionService.Guard.ANY);
    }

    @Test
    void demoModeRollsBackWhenSeedingFails() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("a@b.pl")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class)))
                .thenThrow(new StoreSeedingException("s-1", new RuntimeException("s3 down")));

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true).register("a@b.pl", "Sklep Testowy", "10.0.0.1", PASSWORD));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService).deleteStore("s-1", StoreDeletionService.Guard.DEMO_ONLY);
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void mapsStoreCreationFailureToCreationFailed() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("a@b.pl")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class)))
                .thenThrow(new RuntimeException("dynamo down"));

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true).register("a@b.pl", "Sklep Testowy", "10.0.0.1", PASSWORD));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService, never()).deleteStore(anyString(), any());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void reportsCreationFailureWhenRollbackAlsoFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("demo-store-1"));
        doThrow(new RuntimeException("cognito down"))
                .when(cognitoUserService).createStoreAdmin(anyString(), anyString(), anyString(), anyBoolean());
        doThrow(new RuntimeException("dynamo down")).when(storeDeletionService).deleteStore(anyString(), any());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true).register("user@example.com", "Sklep Testowy", "1.1.1.1", PASSWORD));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
    }

    @Test
    void trimsStoreName() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.registered("Moja Firma", trial("user@firma.pl"))))
                .thenReturn(store("prod-store-1"));

        // when
        service(false).register("user@firma.pl", "  Moja Firma  ", "10.0.0.1", PASSWORD);

        // then
        verify(storeCreationService).createStore(CreateStoreRequest.registered("Moja Firma", trial("user@firma.pl")));
    }

    @Test
    void productionModeRequiresStoreName() {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false).register("user@firma.pl", "   ", "10.0.0.1", PASSWORD));
        assertEquals(RegistrationException.Reason.STORE_NAME_REQUIRED, e.getReason());
        verifyNoInteractions(storeCreationService, cognitoUserService, rateLimiter);
    }

    @ParameterizedTest(name = "demoMode={0}, store name \"{1}\"")
    @MethodSource("invalidStoreNames")
    void rejectsInvalidStoreName(boolean demoMode, String storeName) {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(demoMode).register("user@firma.pl", storeName, "10.0.0.1", PASSWORD));
        assertEquals(RegistrationException.Reason.INVALID_STORE_NAME, e.getReason());
        verifyNoInteractions(storeCreationService, cognitoUserService, rateLimiter);
    }

    static Stream<Arguments> invalidStoreNames() {
        return Stream.of(
                Arguments.of(true, "   "),
                Arguments.of(false, "x".repeat(61)),
                Arguments.of(false, "x"));
    }

    @Test
    void acceptsMinimumAndMaximumLengthStoreName() {
        // given
        String minName = "ab";
        String maxName = "x".repeat(60);
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.registered(minName, trial("user@firma.pl"))))
                .thenReturn(store("prod-store-1"));
        when(storeCreationService.createStore(CreateStoreRequest.registered(maxName, trial("user@firma.pl"))))
                .thenReturn(store("prod-store-2"));

        // when
        service(false).register("user@firma.pl", minName, "10.0.0.1", PASSWORD);
        service(false).register("user@firma.pl", maxName, "10.0.0.1", PASSWORD);

        // then
        verify(storeCreationService).createStore(CreateStoreRequest.registered(minName, trial("user@firma.pl")));
        verify(storeCreationService).createStore(CreateStoreRequest.registered(maxName, trial("user@firma.pl")));
    }

    @Test
    void invalidStoreNameDoesNotConsumeRateLimitToken() {
        // given
        RegistrationService service = new RegistrationService(cognitoUserService, storeSeeder, storeCreationService,
                storeDeletionService, new RegistrationRateLimiter(Clock.fixed(NOW, ZoneOffset.UTC), 3, 100),
                Clock.fixed(NOW, ZoneOffset.UTC), 14, TRIAL_DAYS, false);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.registered("Moja Firma", trial("user@firma.pl"))))
                .thenReturn(store("prod-store-1"));

        // when
        for (int i = 0; i < 3; i++) {
            RegistrationException e = assertThrows(RegistrationException.class,
                    () -> service.register("user@firma.pl", "x", "10.0.0.1", PASSWORD));
            assertEquals(RegistrationException.Reason.INVALID_STORE_NAME, e.getReason());
        }
        RegistrationResult result = service.register("user@firma.pl", "Moja Firma", "10.0.0.1", PASSWORD);

        // then
        assertEquals("prod-store-1", result.storeId());
    }

    @Test
    void mapsReasonsToMessageKeys() {
        // when / then
        assertEquals("registration.error.invalid-email",
                new RegistrationException(RegistrationException.Reason.INVALID_EMAIL).messageKey());
        assertEquals("registration.error.rate-limited",
                new RegistrationException(RegistrationException.Reason.RATE_LIMITED).messageKey());
        assertEquals("registration.error.invalid-store-name",
                new RegistrationException(RegistrationException.Reason.INVALID_STORE_NAME).messageKey());
        assertEquals("registration.error.captcha-failed",
                new RegistrationException(RegistrationException.Reason.CAPTCHA_FAILED).messageKey());
        assertEquals("registration.error.weak-password",
                new RegistrationException(RegistrationException.Reason.WEAK_PASSWORD).messageKey());
        assertEquals("registration.error.invalid-code",
                new RegistrationException(RegistrationException.Reason.INVALID_CODE).messageKey());
    }

    @Test
    void mapsReasonsToMessageKeysRegardlessOfDefaultLocale() {
        // given
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        // when / then
        try {
            assertEquals("registration.error.invalid-email",
                    new RegistrationException(RegistrationException.Reason.INVALID_EMAIL).messageKey());
        } finally {
            Locale.setDefault(original);
        }
    }
}
