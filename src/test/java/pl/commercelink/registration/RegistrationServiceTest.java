package pl.commercelink.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.demo.DemoStoreSeeder;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.users.CognitoUserService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-08T10:00:00Z");

    @Mock private CognitoUserService cognitoUserService;
    @Mock private DemoStoreSeeder demoStoreSeeder;
    @Mock private StoreDeletionService storeDeletionService;
    @Mock private RegistrationRateLimiter rateLimiter;

    private RegistrationService service(boolean revealPassword) {
        return new RegistrationService(cognitoUserService, demoStoreSeeder, storeDeletionService,
                rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC), 14, revealPassword);
    }

    @Test
    void createsStoreAndInvitesUserByEmail() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        ArgumentCaptor<DemoStoreMetadata> metadataCaptor = ArgumentCaptor.forClass(DemoStoreMetadata.class);

        // when
        RegistrationResult result = service(false).register("User@Example.com ", "1.1.1.1");

        // then
        verify(demoStoreSeeder).seedStore(eq(result.storeId()), eq("Sklep demo — user@example.com"), metadataCaptor.capture());
        verify(cognitoUserService).createStoreAdmin("user@example.com", result.storeId());
        assertNull(result.revealedPassword());
        assertEquals("user@example.com", metadataCaptor.getValue().getOwnerEmail());
        assertEquals(NOW.toString(), metadataCaptor.getValue().getCreatedAt());
        assertEquals(NOW.plusSeconds(14 * 24 * 3600).toString(), metadataCaptor.getValue().getExpiresAt());
        assertEquals(10, result.storeId().length());
    }

    @Test
    void returnsGeneratedPasswordInRevealMode() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);

        // when
        RegistrationResult result = service(true).register("user@example.com", "1.1.1.1");

        // then
        verify(cognitoUserService).createStoreAdmin(eq("user@example.com"), eq(result.storeId()), eq(result.revealedPassword()));
        assertNotNull(result.revealedPassword());
        assertTrue(result.revealedPassword().length() >= 12);
    }

    @Test
    void rejectsInvalidEmail() {
        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false).register("not-an-email", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.INVALID_EMAIL, e.getReason());
        verifyNoInteractions(demoStoreSeeder, cognitoUserService);
    }

    @Test
    void rejectsWhenRateLimited() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(false);

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.RATE_LIMITED, e.getReason());
        verifyNoInteractions(demoStoreSeeder);
    }

    @Test
    void rejectsExistingEmail() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(true);

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.EMAIL_EXISTS, e.getReason());
        verifyNoInteractions(demoStoreSeeder);
    }

    @Test
    void rollsBackStoreWhenUserCreationFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        doThrow(new RuntimeException("cognito down")).when(cognitoUserService).createStoreAdmin(anyString(), anyString());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        ArgumentCaptor<String> storeIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(storeDeletionService).deleteDemoStore(storeIdCaptor.capture());
        verify(demoStoreSeeder).seedStore(eq(storeIdCaptor.getValue()), anyString(), any());
    }

    @Test
    void rollsBackStoreWhenSeedingFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        doThrow(new RuntimeException("s3 down")).when(demoStoreSeeder).seedStore(anyString(), anyString(), any());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(storeDeletionService).deleteDemoStore(anyString());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString());
        verify(cognitoUserService, never()).createStoreAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void reportsCreationFailureWhenRollbackAlsoFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(cognitoUserService.userExists("user@example.com")).thenReturn(false);
        doThrow(new RuntimeException("cognito down")).when(cognitoUserService).createStoreAdmin(anyString(), anyString());
        doThrow(new RuntimeException("dynamo down")).when(storeDeletionService).deleteDemoStore(anyString());

        // when / then
        RegistrationException e = assertThrows(RegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(RegistrationException.Reason.CREATION_FAILED, e.getReason());
    }

    @Test
    void mapsReasonsToMessageKeys() {
        // when / then
        assertEquals("registration.error.invalid-email",
                new RegistrationException(RegistrationException.Reason.INVALID_EMAIL).messageKey());
        assertEquals("registration.error.rate-limited",
                new RegistrationException(RegistrationException.Reason.RATE_LIMITED).messageKey());
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
