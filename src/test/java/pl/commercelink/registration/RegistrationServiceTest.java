package pl.commercelink.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.time.ZoneOffset;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-08T10:00:00Z");

    @Mock private CognitoUserService cognitoUserService;
    @Mock private StoreSeeder storeSeeder;
    @Mock private StoreCreationService storeCreationService;
    @Mock private StoreDeletionService storeDeletionService;
    @Mock private RegistrationRateLimiter rateLimiter;

    private RegistrationService service(boolean demoMode, boolean revealPassword) {
        return new RegistrationService(cognitoUserService, storeSeeder, storeCreationService, storeDeletionService,
                rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC), 14, revealPassword, demoMode);
    }

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void createsStoreAndInvitesUserByEmail() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("demo-store-1"));
        ArgumentCaptor<CreateStoreRequest> requestCaptor = ArgumentCaptor.forClass(CreateStoreRequest.class);

        // when
        RegistrationResult result = service(true, false).register("User@Example.com ", "Sklep Testowy", "1.1.1.1");

        // then
        verify(storeCreationService).createStore(requestCaptor.capture());
        assertEquals("Sklep Testowy", requestCaptor.getValue().name());
        assertSame(storeSeeder, requestCaptor.getValue().seeder());
        assertEquals("user@example.com", requestCaptor.getValue().demoMetadata().getOwnerEmail());
        assertEquals(NOW.toString(), requestCaptor.getValue().demoMetadata().getCreatedAt());
        assertEquals(NOW.plusSeconds(14 * 24 * 3600).toString(), requestCaptor.getValue().demoMetadata().getExpiresAt());
        verify(cognitoUserService).createStoreAdmin("user@example.com", "demo-store-1");
        assertNull(result.revealedPassword());
        assertEquals("demo-store-1", result.storeId());
    }

    @Test
    void returnsGeneratedPasswordInRevealMode() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("demo-store-1"));

        // when
        RegistrationResult result = service(true, true).register("user@example.com", "Sklep Testowy", "1.1.1.1");

        // then
        verify(cognitoUserService).createStoreAdmin(eq("user@example.com"), eq("demo-store-1"), eq(result.revealedPassword()));
        assertNotNull(result.revealedPassword());
        assertTrue(result.revealedPassword().length() >= 12);
    }

    @Test
    void rejectsInvalidEmail() {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true, false).register("not-an-email", "Sklep Testowy", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.INVALID_EMAIL, e.getReason());
        verifyNoInteractions(storeSeeder, cognitoUserService, storeCreationService);
    }

    @Test
    void rejectsWhenRateLimited() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(false);

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true, false).register("user@example.com", "Sklep Testowy", "1.1.1.1"));
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
                () -> service(true, false).register("user@example.com", "Sklep Testowy", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.EMAIL_EXISTS, e.getReason());
        verifyNoInteractions(storeCreationService);
    }

    @Test
    void rollsBackStoreWhenUserCreationFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("demo-store-1"));
        doThrow(new RuntimeException("cognito down")).when(cognitoUserService).createStoreAdmin(anyString(), anyString());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true, false).register("user@example.com", "Sklep Testowy", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService).deleteStore("demo-store-1", StoreDeletionService.Guard.DEMO_ONLY);
    }

    @Test
    void revealPasswordModeRollsBackWhenUserCreationFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("s-1"));
        doThrow(new RuntimeException("password set failed"))
                .when(cognitoUserService).createStoreAdmin(anyString(), anyString(), anyString());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true, true).register("user@example.com", "Sklep Testowy", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService).deleteStore("s-1", StoreDeletionService.Guard.DEMO_ONLY);
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
                () -> service(true, false).register("a@b.pl", "Sklep Testowy", "10.0.0.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService).deleteStore("s-1", StoreDeletionService.Guard.DEMO_ONLY);
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void demoModeMapsStoreCreationFailureToCreationFailed() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("a@b.pl")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class)))
                .thenThrow(new RuntimeException("dynamo down"));

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true, false).register("a@b.pl", "Sklep Testowy", "10.0.0.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService, never()).deleteStore(anyString(), any());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void reportsCreationFailureWhenRollbackAlsoFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("demo-store-1"));
        doThrow(new RuntimeException("cognito down")).when(cognitoUserService).createStoreAdmin(anyString(), anyString());
        doThrow(new RuntimeException("dynamo down")).when(storeDeletionService).deleteStore(anyString(), any());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true, false).register("user@example.com", "Sklep Testowy", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
    }

    @Test
    void demoModeUsesProvidedStoreName() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(any(CreateStoreRequest.class))).thenReturn(store("demo-store-1"));

        // when
        service(true, false).register("user@firma.pl", "Moja Firma", "10.0.0.1");

        // then
        verify(storeCreationService).createStore(argThat(req ->
                req.name().equals("Moja Firma") && req.demoMetadata() != null));
    }

    @Test
    void productionModeCreatesBareStoreWithUserProvidedName() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.bare("Moja Firma", null))).thenReturn(store("prod-store-1"));

        // when
        RegistrationResult result = service(false, false).register("user@firma.pl", "Moja Firma", "10.0.0.1");

        // then
        assertEquals("prod-store-1", result.storeId());
        assertNull(result.revealedPassword());
        verify(storeCreationService).createStore(CreateStoreRequest.bare("Moja Firma", null));
        verify(cognitoUserService).createStoreAdmin("user@firma.pl", "prod-store-1");
        verifyNoInteractions(storeSeeder);
    }

    @Test
    void trimsStoreName() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.bare("Moja Firma", null))).thenReturn(store("prod-store-1"));

        // when
        service(false, false).register("user@firma.pl", "  Moja Firma  ", "10.0.0.1");

        // then
        verify(storeCreationService).createStore(CreateStoreRequest.bare("Moja Firma", null));
    }

    @Test
    void productionModeRequiresStoreName() {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false, false).register("user@firma.pl", "   ", "10.0.0.1"));
        assertEquals(RegistrationException.Reason.STORE_NAME_REQUIRED, e.getReason());
        verifyNoInteractions(storeCreationService, cognitoUserService, rateLimiter);
    }

    @Test
    void demoModeRejectsBlankStoreNameAsInvalid() {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(true, false).register("user@firma.pl", "   ", "10.0.0.1"));
        assertEquals(RegistrationException.Reason.INVALID_STORE_NAME, e.getReason());
        verifyNoInteractions(storeCreationService, cognitoUserService, rateLimiter);
    }

    @Test
    void rejectsTooLongStoreName() {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false, false).register("user@firma.pl", "x".repeat(61), "10.0.0.1"));
        assertEquals(RegistrationException.Reason.INVALID_STORE_NAME, e.getReason());
        verifyNoInteractions(storeCreationService, cognitoUserService, rateLimiter);
    }

    @Test
    void rejectsSingleCharacterStoreName() {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false, false).register("user@firma.pl", "x", "10.0.0.1"));
        assertEquals(RegistrationException.Reason.INVALID_STORE_NAME, e.getReason());
        verifyNoInteractions(storeCreationService, cognitoUserService, rateLimiter);
    }

    @Test
    void acceptsMinimumAndMaximumLengthStoreName() {
        // given
        String minName = "ab";
        String maxName = "x".repeat(60);
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.bare(minName, null))).thenReturn(store("prod-store-1"));
        when(storeCreationService.createStore(CreateStoreRequest.bare(maxName, null))).thenReturn(store("prod-store-2"));

        // when
        service(false, false).register("user@firma.pl", minName, "10.0.0.1");
        service(false, false).register("user@firma.pl", maxName, "10.0.0.1");

        // then
        verify(storeCreationService).createStore(CreateStoreRequest.bare(minName, null));
        verify(storeCreationService).createStore(CreateStoreRequest.bare(maxName, null));
    }

    @Test
    void invalidStoreNameDoesNotConsumeRateLimitToken() {
        // given
        RegistrationService service = new RegistrationService(cognitoUserService, storeSeeder, storeCreationService,
                storeDeletionService, new RegistrationRateLimiter(Clock.fixed(NOW, ZoneOffset.UTC), 3, 100),
                Clock.fixed(NOW, ZoneOffset.UTC), 14, false, false);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.bare("Moja Firma", null))).thenReturn(store("prod-store-1"));

        // when
        for (int i = 0; i < 3; i++) {
            RegistrationException e = assertThrows(RegistrationException.class,
                    () -> service.register("user@firma.pl", "x", "10.0.0.1"));
            assertEquals(RegistrationException.Reason.INVALID_STORE_NAME, e.getReason());
        }
        RegistrationResult result = service.register("user@firma.pl", "Moja Firma", "10.0.0.1");

        // then
        assertEquals("prod-store-1", result.storeId());
    }

    @Test
    void productionModeIgnoresRevealPassword() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.bare("Moja Firma", null))).thenReturn(store("prod-store-1"));

        // when
        RegistrationResult result = service(false, true).register("user@firma.pl", "Moja Firma", "10.0.0.1");

        // then
        assertNull(result.revealedPassword());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void productionModeMapsStoreCreationFailureToCreationFailed() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.bare("Moja Firma", null)))
                .thenThrow(new RuntimeException("dynamo down"));

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false, false).register("user@firma.pl", "Moja Firma", "10.0.0.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService, never()).deleteStore(anyString(), any());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void productionModeRollsBackWithAnyGuard() {
        // given
        when(rateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@firma.pl")).thenReturn(false);
        when(storeCreationService.createStore(CreateStoreRequest.bare("Moja Firma", null))).thenReturn(store("prod-store-1"));
        doThrow(new RuntimeException("cognito down")).when(cognitoUserService).createStoreAdmin(anyString(), anyString());

        // when / then
        assertThrows(RegistrationException.class, () -> service(false, false).register("user@firma.pl", "Moja Firma", "10.0.0.1"));
        verify(storeDeletionService).deleteStore("prod-store-1", StoreDeletionService.Guard.ANY);
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
