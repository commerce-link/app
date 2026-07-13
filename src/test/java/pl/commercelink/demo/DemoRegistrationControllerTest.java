package pl.commercelink.demo;

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
class DemoRegistrationControllerTest {

    @Mock private DemoRegistrationService demoRegistrationService;
    @Mock private MessageSource messageSource;
    @Mock private HttpServletRequest request;
    @InjectMocks private DemoRegistrationController controller;

    @Test
    void registersAndShowsSuccessPage() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(demoRegistrationService.register("user@example.com", "1.1.1.1"))
                .thenReturn(new DemoRegistrationResult("abc123def4", "Demo1!secret"));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", null, request, model, Locale.forLanguageTag("pl"));

        // then
        assertEquals("demo-register-success", view);
        assertEquals("Demo1!secret", model.getAttribute("revealedPassword"));
        assertEquals("user@example.com", model.getAttribute("email"));
    }

    @Test
    void usesFirstForwardedForAddressAsClientIp() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");
        when(demoRegistrationService.register("user@example.com", "9.9.9.9"))
                .thenReturn(new DemoRegistrationResult("abc123def4", null));

        // when
        String view = controller.register("user@example.com", null, request, new ExtendedModelMap(), Locale.forLanguageTag("pl"));

        // then
        assertEquals("demo-register-success", view);
        verify(demoRegistrationService).register("user@example.com", "9.9.9.9");
    }

    @Test
    void silentlyRedirectsWhenHoneypotFilled() {
        // when
        String view = controller.register("user@example.com", "bot value", request, new ExtendedModelMap(), Locale.forLanguageTag("pl"));

        // then
        assertEquals("redirect:/demo/register", view);
        verifyNoInteractions(demoRegistrationService);
    }

    @Test
    void showsErrorMessageOnRegistrationFailure() {
        // given
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(demoRegistrationService.register(any(), any()))
                .thenThrow(new DemoRegistrationException(DemoRegistrationException.Reason.EMAIL_EXISTS));
        when(messageSource.getMessage(eq("demo.register.error.email-exists"), any(), any(Locale.class)))
                .thenReturn("Konto istnieje");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", null, request, model, Locale.forLanguageTag("pl"));

        // then
        assertEquals("demo-register", view);
        assertEquals("Konto istnieje", model.getAttribute("errorMessage"));
    }
}
