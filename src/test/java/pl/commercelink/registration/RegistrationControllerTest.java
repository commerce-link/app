package pl.commercelink.registration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    @InjectMocks private RegistrationController controller;

    @Test
    void registersAndShowsSuccessPage() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(registrationService.register("user@example.com", "1.1.1.1"))
                .thenReturn(new RegistrationResult("abc123def4", "Demo1!secret"));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", null, request, model, Locale.forLanguageTag("pl"));

        // then
        assertEquals("register-success", view);
        assertEquals("Demo1!secret", model.getAttribute("revealedPassword"));
        assertEquals("user@example.com", model.getAttribute("email"));
    }

    @Test
    void usesFirstForwardedForAddressAsClientIp() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");
        when(registrationService.register("user@example.com", "9.9.9.9"))
                .thenReturn(new RegistrationResult("abc123def4", null));

        // when
        String view = controller.register("user@example.com", null, request, new ExtendedModelMap(), Locale.forLanguageTag("pl"));

        // then
        assertEquals("register-success", view);
        verify(registrationService).register("user@example.com", "9.9.9.9");
    }

    @Test
    void silentlyRedirectsWhenHoneypotFilled() {
        // when
        String view = controller.register("user@example.com", "bot value", request, new ExtendedModelMap(), Locale.forLanguageTag("pl"));

        // then
        assertEquals("redirect:/register", view);
        verifyNoInteractions(registrationService);
    }

    @Test
    void showsErrorMessageOnRegistrationFailure() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(registrationService.register(any(), any()))
                .thenThrow(new RegistrationException(RegistrationException.Reason.EMAIL_EXISTS));
        when(messageSource.getMessage(eq("registration.error.email-exists"), any(), any(Locale.class)))
                .thenReturn("Konto istnieje");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", null, request, model, Locale.forLanguageTag("pl"));

        // then
        assertEquals("register", view);
        assertEquals("Konto istnieje", model.getAttribute("errorMessage"));
    }

    @Test
    void legacyDemoRegisterPathRedirectsToRegister() {
        // when
        String view = controller.legacyRedirect();

        // then
        assertEquals("redirect:/register", view);
    }
}
