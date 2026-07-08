package pl.commercelink.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.DemoStoreMetadata;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-08T10:00:00Z");

    @Mock private DemoUserService demoUserService;
    @Mock private DemoStoreSeeder demoStoreSeeder;
    @Mock private DemoStoreDeletionService demoStoreDeletionService;
    @Mock private DemoRegistrationRateLimiter rateLimiter;

    private DemoRegistrationService service(boolean revealPassword) {
        return new DemoRegistrationService(demoUserService, demoStoreSeeder, demoStoreDeletionService,
                rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC), 14, revealPassword);
    }

    @Test
    void createsStoreAndInvitesUserByEmail() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(demoUserService.userExists("user@example.com")).thenReturn(false);
        ArgumentCaptor<DemoStoreMetadata> metadataCaptor = ArgumentCaptor.forClass(DemoStoreMetadata.class);

        // when
        DemoRegistrationResult result = service(false).register("User@Example.com ", "1.1.1.1");

        // then
        verify(demoStoreSeeder).seedStore(eq(result.storeId()), eq("Sklep demo — user@example.com"), metadataCaptor.capture());
        verify(demoUserService).createDemoAdmin("user@example.com", result.storeId());
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
        when(demoUserService.userExists("user@example.com")).thenReturn(false);

        // when
        DemoRegistrationResult result = service(true).register("user@example.com", "1.1.1.1");

        // then
        verify(demoUserService).createDemoAdmin(eq("user@example.com"), eq(result.storeId()), eq(result.revealedPassword()));
        assertNotNull(result.revealedPassword());
        assertTrue(result.revealedPassword().length() >= 12);
    }

    @Test
    void rejectsInvalidEmail() {
        // when / then
        DemoRegistrationException e = assertThrows(DemoRegistrationException.class,
                () -> service(false).register("not-an-email", "1.1.1.1"));
        assertEquals(DemoRegistrationException.Reason.INVALID_EMAIL, e.getReason());
        verifyNoInteractions(demoStoreSeeder, demoUserService);
    }

    @Test
    void rejectsWhenRateLimited() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(false);

        // when / then
        DemoRegistrationException e = assertThrows(DemoRegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(DemoRegistrationException.Reason.RATE_LIMITED, e.getReason());
        verifyNoInteractions(demoStoreSeeder);
    }

    @Test
    void rejectsExistingEmail() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(demoUserService.userExists("user@example.com")).thenReturn(true);

        // when / then
        DemoRegistrationException e = assertThrows(DemoRegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(DemoRegistrationException.Reason.EMAIL_EXISTS, e.getReason());
        verifyNoInteractions(demoStoreSeeder);
    }

    @Test
    void rollsBackStoreWhenUserCreationFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(demoUserService.userExists("user@example.com")).thenReturn(false);
        doThrow(new RuntimeException("cognito down")).when(demoUserService).createDemoAdmin(anyString(), anyString());

        // when / then
        DemoRegistrationException e = assertThrows(DemoRegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(DemoRegistrationException.Reason.CREATION_FAILED, e.getReason());
        ArgumentCaptor<String> storeIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(demoStoreDeletionService).deleteDemoStore(storeIdCaptor.capture());
        verify(demoStoreSeeder).seedStore(eq(storeIdCaptor.getValue()), anyString(), any());
    }

    @Test
    void rollsBackStoreWhenSeedingFails() {
        // given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
        when(demoUserService.userExists("user@example.com")).thenReturn(false);
        doThrow(new RuntimeException("s3 down")).when(demoStoreSeeder).seedStore(anyString(), anyString(), any());

        // when / then
        DemoRegistrationException e = assertThrows(DemoRegistrationException.class,
                () -> service(false).register("user@example.com", "1.1.1.1"));
        assertEquals(DemoRegistrationException.Reason.CREATION_FAILED, e.getReason());
        verify(demoStoreDeletionService).deleteDemoStore(anyString());
        verify(demoUserService, never()).createDemoAdmin(anyString(), anyString());
        verify(demoUserService, never()).createDemoAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void mapsReasonsToMessageKeys() {
        // when / then
        assertEquals("demo.register.error.invalid-email",
                new DemoRegistrationException(DemoRegistrationException.Reason.INVALID_EMAIL).messageKey());
        assertEquals("demo.register.error.rate-limited",
                new DemoRegistrationException(DemoRegistrationException.Reason.RATE_LIMITED).messageKey());
    }
}
