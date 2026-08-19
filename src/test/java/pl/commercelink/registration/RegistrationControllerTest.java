package pl.commercelink.registration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationControllerTest {

    private static final String PASSWORD = "Tajne1!haslo";

    @Mock private RegistrationService registrationService;
    @Mock private RegistrationAutoLoginService autoLoginService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private CaptchaVerifier captchaVerifier;
    @Mock private MessageSource messageSource;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RegistrationController controller;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("1.1.1.1");
        response = new MockHttpServletResponse();
        controller = controller(false);
    }

    private RegistrationController controller(boolean demoMode) {
        return new RegistrationController(registrationService, autoLoginService, emailVerificationService,
                captchaVerifier, messageSource, demoMode, 3, "", "/dashboard");
    }

    private void captchaPasses() {
        when(captchaVerifier.verify(any(), anyString())).thenReturn(true);
    }

    private void pending(String email, String storeName) {
        request.getSession(true).setAttribute(RegistrationController.PENDING_REGISTRATION,
                new PendingRegistration(email, storeName));
    }

    private Object sessionPending() {
        return request.getSession(true).getAttribute(RegistrationController.PENDING_REGISTRATION);
    }

    @Test
    void storesPendingRegistrationAndRedirectsToPasswordStep() {
        // given
        captchaPasses();
        when(registrationService.validateCandidate("User@Example.com", "Sklep Testowy"))
                .thenReturn("user@example.com");

        // when
        String view = controller.register("User@Example.com", "Sklep Testowy", null, "on", "token",
                request, new ExtendedModelMap(), Locale.ROOT);

        // then
        assertEquals("redirect:/register/password", view);
        assertEquals(new PendingRegistration("user@example.com", "Sklep Testowy"), sessionPending());
        verify(registrationService, never()).register(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsRegistrationWhenCaptchaFails() {
        // given
        when(captchaVerifier.verify(any(), anyString())).thenReturn(false);
        when(messageSource.getMessage(eq("registration.error.captcha-failed"), any(), any(Locale.class)))
                .thenReturn("Potwierdź, że nie jesteś robotem.");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", "Sklep Testowy", null, "on", null,
                request, model, Locale.ROOT);

        // then
        assertEquals("register", view);
        assertEquals("Potwierdź, że nie jesteś robotem.", model.getAttribute("errorMessage"));
        assertNull(sessionPending());
        verifyNoInteractions(registrationService);
    }

    @Test
    void silentlyRedirectsWhenHoneypotFilled() {
        // when
        String view = controller.register("user@example.com", "Sklep Testowy", "bot value", "on", "token",
                request, new ExtendedModelMap(), Locale.ROOT);

        // then
        assertEquals("redirect:/register", view);
        verifyNoInteractions(registrationService, captchaVerifier);
    }

    @Test
    void showsErrorWhenTermsNotAccepted() {
        // given
        captchaPasses();
        when(messageSource.getMessage(eq("registration.error.terms-consent-required"), any(), any(Locale.class)))
                .thenReturn("Zaakceptuj regulamin.");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", "Sklep Testowy", null, null, "token",
                request, model, Locale.ROOT);

        // then
        assertEquals("register", view);
        assertEquals("Zaakceptuj regulamin.", model.getAttribute("errorMessage"));
        verify(registrationService, never()).validateCandidate(anyString(), anyString());
    }

    @Test
    void showsErrorWhenEmailAlreadyTaken() {
        // given
        captchaPasses();
        when(registrationService.validateCandidate("user@example.com", "Sklep Testowy"))
                .thenThrow(new RegistrationException(RegistrationException.Reason.EMAIL_EXISTS));
        when(messageSource.getMessage(eq("registration.error.email-exists"), any(), any(Locale.class)))
                .thenReturn("Konto istnieje.");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.register("user@example.com", "Sklep Testowy", null, "on", "token",
                request, model, Locale.ROOT);

        // then
        assertEquals("register", view);
        assertEquals("Konto istnieje.", model.getAttribute("errorMessage"));
        assertNull(sessionPending());
    }

    @Test
    void demoModeIgnoresSubmittedStoreNameAndUsesDefault() {
        // given
        captchaPasses();
        when(messageSource.getMessage("registration.store-name.default", null, Locale.ROOT)).thenReturn("Mój sklep");
        when(registrationService.validateCandidate("user@firma.pl", "Mój sklep")).thenReturn("user@firma.pl");

        // when
        controller(true).register("user@firma.pl", "Sklep Testowy", null, "on", "token",
                request, new ExtendedModelMap(), Locale.ROOT);

        // then
        assertEquals(new PendingRegistration("user@firma.pl", "Mój sklep"), sessionPending());
    }

    @Test
    void usesFirstForwardedForAddressAsClientIp() {
        // given
        request.addHeader("X-Forwarded-For", "9.9.9.9, 10.0.0.1");
        when(captchaVerifier.verify(any(), eq("9.9.9.9"))).thenReturn(true);
        when(registrationService.validateCandidate(anyString(), anyString())).thenReturn("user@example.com");

        // when
        controller.register("user@example.com", "Sklep Testowy", null, "on", "token",
                request, new ExtendedModelMap(), Locale.ROOT);

        // then
        verify(captchaVerifier).verify("token", "9.9.9.9");
    }

    @Test
    void passwordPageRedirectsToRegisterWithoutPendingRegistration() {
        // when
        String view = controller.passwordPage(request, new ExtendedModelMap());

        // then
        assertEquals("redirect:/register", view);
    }

    @Test
    void passwordPageShowsFormForPendingRegistration() {
        // given
        pending("user@example.com", "Sklep Testowy");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.passwordPage(request, model);

        // then
        assertEquals("register-password", view);
        assertEquals("user@example.com", model.getAttribute("email"));
    }

    @Test
    void rejectsWeakPasswordAndKeepsPendingRegistration() {
        // given
        pending("user@example.com", "Sklep Testowy");
        when(messageSource.getMessage(eq("registration.error.weak-password"), any(), any(Locale.class)))
                .thenReturn("Hasło za słabe.");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.setPassword("krotkie", request, response, model, Locale.ROOT);

        // then
        assertEquals("register-password", view);
        assertEquals("Hasło za słabe.", model.getAttribute("errorMessage"));
        assertNotNull(sessionPending());
        verifyNoInteractions(registrationService, autoLoginService);
    }

    @Test
    void createsAccountAndGoesStraightToPanelInDemoMode() {
        // given
        pending("user@example.com", "Sklep Testowy");
        when(registrationService.register("user@example.com", "Sklep Testowy", "1.1.1.1", PASSWORD))
                .thenReturn(new RegistrationResult("demo-store-1"));
        when(autoLoginService.login("user@example.com", PASSWORD, "demo-store-1", request, response)).thenReturn(true);
        when(registrationService.isEmailVerifiedOnCreation()).thenReturn(true);

        // when
        String view = controller.setPassword(PASSWORD, request, response, new ExtendedModelMap(), Locale.ROOT);

        // then
        assertEquals("redirect:/dashboard", view);
        assertNull(sessionPending());
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void sendsVerificationCodeAndGoesToVerifyScreenInProductionMode() {
        // given
        pending("user@firma.pl", "Moja Firma");
        when(registrationService.register("user@firma.pl", "Moja Firma", "1.1.1.1", PASSWORD))
                .thenReturn(new RegistrationResult("prod-store-1"));
        when(autoLoginService.login("user@firma.pl", PASSWORD, "prod-store-1", request, response)).thenReturn(true);
        when(registrationService.isEmailVerifiedOnCreation()).thenReturn(false);

        // when
        String view = controller.setPassword(PASSWORD, request, response, new ExtendedModelMap(), Locale.ROOT);

        // then
        assertEquals("redirect:/register/verify-email", view);
        verify(emailVerificationService).sendCodeQuietly("user@firma.pl");
    }

    @Test
    void secondPasswordSubmitDoesNotCreateSecondStore() {
        // given
        pending("user@example.com", "Sklep Testowy");
        when(registrationService.register("user@example.com", "Sklep Testowy", "1.1.1.1", PASSWORD))
                .thenReturn(new RegistrationResult("demo-store-1"));
        when(autoLoginService.login(anyString(), anyString(), anyString(), any(), any())).thenReturn(true);
        when(registrationService.isEmailVerifiedOnCreation()).thenReturn(true);

        // when
        controller.setPassword(PASSWORD, request, response, new ExtendedModelMap(), Locale.ROOT);
        String secondView = controller.setPassword(PASSWORD, request, response, new ExtendedModelMap(), Locale.ROOT);

        // then
        assertEquals("redirect:/register", secondView);
        verify(registrationService, times(1)).register(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void restoresPendingRegistrationWhenCreationFails() {
        // given
        pending("user@example.com", "Sklep Testowy");
        when(registrationService.register("user@example.com", "Sklep Testowy", "1.1.1.1", PASSWORD))
                .thenThrow(new RegistrationException(RegistrationException.Reason.CREATION_FAILED));
        when(messageSource.getMessage(eq("registration.error.creation-failed"), any(), any(Locale.class)))
                .thenReturn("Nie udało się utworzyć konta.");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.setPassword(PASSWORD, request, response, model, Locale.ROOT);

        // then
        assertEquals("register-password", view);
        assertEquals("Nie udało się utworzyć konta.", model.getAttribute("errorMessage"));
        assertNotNull(sessionPending());
    }

    @Test
    void fallsBackToSuccessPageWhenAutoLoginFails() {
        // given
        pending("user@example.com", "Sklep Testowy");
        when(registrationService.register("user@example.com", "Sklep Testowy", "1.1.1.1", PASSWORD))
                .thenReturn(new RegistrationResult("demo-store-1"));
        when(autoLoginService.login("user@example.com", PASSWORD, "demo-store-1", request, response)).thenReturn(false);

        // when
        String view = controller.setPassword(PASSWORD, request, response, new ExtendedModelMap(), Locale.ROOT);

        // then
        assertEquals("register-success", view);
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void legacyDemoRegisterPathRedirectsToRegister() {
        // when
        String view = controller.legacyRedirect();

        // then
        assertEquals("redirect:/register", view);
    }

    @Test
    void registerPageExposesDemoModeAndCaptchaKeyToTemplate() {
        // given
        when(captchaVerifier.siteKey()).thenReturn("site-key");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller(true).registerPage(model);

        // then
        assertEquals("register", view);
        assertEquals(true, model.getAttribute("demoMode"));
        assertEquals(3, model.getAttribute("ttlDays"));
        assertEquals("site-key", model.getAttribute("captchaSiteKey"));
    }
}
