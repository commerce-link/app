package pl.commercelink.registration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationControllerTest {

    private static final Locale LOCALE = Locale.forLanguageTag("pl");

    @Mock private EmailVerificationService emailVerificationService;
    @Mock private MessageSource messageSource;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private EmailVerificationController controller;

    @BeforeEach
    void setUp() {
        controller = new EmailVerificationController(emailVerificationService, messageSource, "/dashboard");
    }

    @Test
    void verifyPageSendsCodeToUnverifiedUser() {
        // given
        when(emailVerificationService.isVerified(request)).thenReturn(false);
        when(emailVerificationService.sendCodeOnce(request)).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.verifyPage(request, model, LOCALE);

        // then
        assertEquals("register-verify-email", view);
        verify(emailVerificationService).sendCodeOnce(request);
        assertNull(model.getAttribute("errorMessage"));
    }

    @Test
    void verifyPageShowsErrorWhenCodeCannotBeSent() {
        // given
        when(emailVerificationService.isVerified(request)).thenReturn(false);
        when(emailVerificationService.sendCodeOnce(request)).thenReturn(false);
        when(messageSource.getMessage("registration.verify.resend-failed", null, LOCALE)).thenReturn("Nie udało się");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.verifyPage(request, model, LOCALE);

        // then
        assertEquals("register-verify-email", view);
        assertEquals("Nie udało się", model.getAttribute("errorMessage"));
    }

    @Test
    void verifyPageRedirectsVerifiedUserWithoutSendingCode() {
        // given
        when(emailVerificationService.isVerified(request)).thenReturn(true);

        // when
        String view = controller.verifyPage(request, new ExtendedModelMap(), LOCALE);

        // then
        assertEquals("redirect:/dashboard", view);
        verify(emailVerificationService, never()).sendCodeOnce(any());
    }
}
