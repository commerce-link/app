package pl.commercelink.registration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
@ConditionalOnProperty(name = "app.registration.enabled", havingValue = "true")
public class RegistrationController {

    static final String PENDING_REGISTRATION = "pendingRegistration";
    private static final String PASSWORD_PATH = "/register/password";

    private final RegistrationService registrationService;
    private final RegistrationAutoLoginService autoLoginService;
    private final EmailVerificationService emailVerificationService;
    private final CaptchaVerifier captchaVerifier;
    private final MessageSource messageSource;
    private final boolean demoMode;
    private final int ttlDays;
    private final String termsUrl;
    private final String homeUrl;

    public RegistrationController(RegistrationService registrationService,
                                  RegistrationAutoLoginService autoLoginService,
                                  EmailVerificationService emailVerificationService,
                                  CaptchaVerifier captchaVerifier,
                                  MessageSource messageSource,
                                  @Value("${app.registration.demo:false}") boolean demoMode,
                                  @Value("${app.registration.ttl-days}") int ttlDays,
                                  @Value("${app.terms-url:}") String termsUrl,
                                  @Value("${application.home}") String homeUrl) {
        this.registrationService = registrationService;
        this.autoLoginService = autoLoginService;
        this.emailVerificationService = emailVerificationService;
        this.captchaVerifier = captchaVerifier;
        this.messageSource = messageSource;
        this.demoMode = demoMode;
        this.ttlDays = ttlDays;
        this.termsUrl = termsUrl;
        this.homeUrl = homeUrl;
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        addFormAttributes(model);
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam(required = false) String storeName,
                           @RequestParam(name = "company", required = false) String honeypot,
                           @RequestParam(name = "termsConsent", required = false) String termsConsent,
                           @RequestParam(name = "cf-turnstile-response", required = false) String captchaResponse,
                           HttpServletRequest request,
                           Model model,
                           Locale locale) {
        if (isNotBlank(honeypot)) {
            return "redirect:/register";
        }
        addFormAttributes(model);
        model.addAttribute("email", email);
        model.addAttribute("storeName", storeName);

        if (!captchaVerifier.verify(captchaResponse, clientIp(request))) {
            model.addAttribute("errorMessage",
                    messageSource.getMessage("registration.error.captcha-failed", null, locale));
            return "register";
        }
        if (termsConsent == null) {
            model.addAttribute("errorMessage",
                    messageSource.getMessage("registration.error.terms-consent-required", null, locale));
            return "register";
        }

        String resolvedName = demoMode
                ? messageSource.getMessage("registration.store-name.default", null, locale)
                : storeName;
        try {
            String normalizedEmail = registrationService.validateCandidate(email, resolvedName);
            request.getSession(true)
                    .setAttribute(PENDING_REGISTRATION, new PendingRegistration(normalizedEmail, resolvedName));
            return "redirect:" + PASSWORD_PATH;
        } catch (RegistrationException e) {
            model.addAttribute("errorMessage", messageSource.getMessage(e.messageKey(), null, locale));
            return "register";
        }
    }

    @GetMapping(PASSWORD_PATH)
    public String passwordPage(HttpServletRequest request, Model model) {
        PendingRegistration pending = pendingRegistration(request);
        if (pending == null) {
            return "redirect:/register";
        }
        addFormAttributes(model);
        model.addAttribute("email", pending.email());
        return "register-password";
    }

    @PostMapping(PASSWORD_PATH)
    public String setPassword(@RequestParam(required = false) String password,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              Model model,
                              Locale locale) {
        PendingRegistration pending = pendingRegistration(request);
        if (pending == null) {
            return "redirect:/register";
        }
        addFormAttributes(model);
        model.addAttribute("email", pending.email());

        if (!PasswordPolicy.isValid(password)) {
            model.addAttribute("errorMessage",
                    messageSource.getMessage("registration.error.weak-password", null, locale));
            return "register-password";
        }

        request.getSession().removeAttribute(PENDING_REGISTRATION);
        RegistrationResult result;
        try {
            result = registrationService.register(pending.email(), pending.storeName(), clientIp(request), password);
        } catch (RegistrationException e) {
            request.getSession().setAttribute(PENDING_REGISTRATION, pending);
            model.addAttribute("errorMessage", messageSource.getMessage(e.messageKey(), null, locale));
            return "register-password";
        }

        if (!autoLoginService.login(pending.email(), password, result.storeId(), request, response)) {
            return "register-success";
        }
        if (!registrationService.isEmailVerifiedOnCreation()) {
            emailVerificationService.sendCodeQuietly(pending.email());
            return "redirect:" + EmailVerificationController.VERIFY_EMAIL_PATH;
        }
        return "redirect:" + homeUrl;
    }

    @GetMapping("/demo/register")
    public String legacyRedirect() {
        return "redirect:/register";
    }

    private PendingRegistration pendingRegistration(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (PendingRegistration) session.getAttribute(PENDING_REGISTRATION);
    }

    private void addFormAttributes(Model model) {
        model.addAttribute("demoMode", demoMode);
        model.addAttribute("ttlDays", ttlDays);
        model.addAttribute("termsUrl", termsUrl.isBlank() ? null : termsUrl);
        model.addAttribute("captchaSiteKey", captchaVerifier.siteKey());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return isNotBlank(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
