package pl.commercelink.registration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationControllerTest {

    @Mock private RegistrationService registrationService;
    @Mock private MessageSource messageSource;
    @Mock private HttpServletRequest request;
    private RegistrationController controller;

    @BeforeEach
    void setUp() {
        controller = new RegistrationController(registrationService, messageSource, false, 3);
    }

    @Test
    void registersAndShowsSuccessPage() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(registrationService.register("user@example.com", "Sklep Testowy", "1.1.1.1"))
                .thenReturn(new RegistrationResult("abc123def4", "Demo1!secret"));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", "Sklep Testowy", null, request, model, Locale.forLanguageTag("pl"));

        // then
        assertEquals("register-success", view);
        assertEquals("Demo1!secret", model.getAttribute("revealedPassword"));
        assertEquals("user@example.com", model.getAttribute("email"));
        assertEquals(false, model.getAttribute("demoMode"));
    }

    @Test
    void usesFirstForwardedForAddressAsClientIp() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");
        when(registrationService.register("user@example.com", "Sklep Testowy", "9.9.9.9"))
                .thenReturn(new RegistrationResult("abc123def4", null));

        // when
        String view = controller.register("user@example.com", "Sklep Testowy", null, request, new ExtendedModelMap(), Locale.forLanguageTag("pl"));

        // then
        assertEquals("register-success", view);
        verify(registrationService).register("user@example.com", "Sklep Testowy", "9.9.9.9");
    }

    @Test
    void silentlyRedirectsWhenHoneypotFilled() {
        // when
        String view = controller.register("user@example.com", "Sklep Testowy", "bot value", request, new ExtendedModelMap(), Locale.forLanguageTag("pl"));

        // then
        assertEquals("redirect:/register", view);
        verifyNoInteractions(registrationService);
    }

    @Test
    void showsErrorMessageOnRegistrationFailure() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(registrationService.register(any(), any(), any()))
                .thenThrow(new RegistrationException(RegistrationException.Reason.EMAIL_EXISTS));
        when(messageSource.getMessage(eq("registration.error.email-exists"), any(), any(Locale.class)))
                .thenReturn("Konto istnieje");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", "Sklep Testowy", null, request, model, Locale.forLanguageTag("pl"));

        // then
        assertEquals("register", view);
        assertEquals("Konto istnieje", model.getAttribute("errorMessage"));
        assertEquals("Sklep Testowy", model.getAttribute("storeName"));
        assertEquals(false, model.getAttribute("demoMode"));
    }

    @Test
    void blankStoreNameFallsBackToPlaceholder() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(messageSource.getMessage("registration.store-name.placeholder", null, Locale.ROOT)).thenReturn("Mój sklep");
        when(registrationService.register("user@firma.pl", "Mój sklep", "10.0.0.1"))
                .thenReturn(new RegistrationResult("prod-store-1", null));

        // when
        controller.register("user@firma.pl", "  ", null, request, new ExtendedModelMap(), Locale.ROOT);

        // then
        verify(registrationService).register("user@firma.pl", "Mój sklep", "10.0.0.1");
    }

    @Test
    void legacyDemoRegisterPathRedirectsToRegister() {
        // when
        String view = controller.legacyRedirect();

        // then
        assertEquals("redirect:/register", view);
    }

    @Test
    void registerPageExposesDemoModeFlagToTemplate() {
        // given
        RegistrationController demoController = new RegistrationController(registrationService, messageSource, true, 3);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = demoController.registerPage(model);

        // then
        assertEquals("register", view);
        assertEquals(true, model.getAttribute("demoMode"));
        assertEquals(3, model.getAttribute("ttlDays"));
    }

    @Test
    void postPathExposesDemoModeAndTtlDaysToTemplate() {
        // given
        RegistrationController demoController = new RegistrationController(registrationService, messageSource, true, 7);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(registrationService.register("user@example.com", "Sklep Testowy", "1.1.1.1"))
                .thenReturn(new RegistrationResult("abc123def4", null));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = demoController.register("user@example.com", "Sklep Testowy", null, request, model, Locale.forLanguageTag("pl"));

        // then
        assertEquals("register-success", view);
        assertEquals(true, model.getAttribute("demoMode"));
        assertEquals(7, model.getAttribute("ttlDays"));
    }
}
