package pl.commercelink.registration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.Locale;

@Controller
public class EmailVerificationController {

    static final String VERIFY_EMAIL_PATH = "/register/verify-email";

    private final EmailVerificationService emailVerificationService;
    private final MessageSource messageSource;
    private final String homeUrl;

    public EmailVerificationController(EmailVerificationService emailVerificationService,
                                       MessageSource messageSource,
                                       @Value("${application.home}") String homeUrl) {
        this.emailVerificationService = emailVerificationService;
        this.messageSource = messageSource;
        this.homeUrl = homeUrl;
    }

    @GetMapping(VERIFY_EMAIL_PATH)
    public String verifyPage(HttpServletRequest request, Model model) {
        if (emailVerificationService.isVerified(request)) {
            return "redirect:" + homeUrl;
        }
        addEmail(model);
        return "register-verify-email";
    }

    @PostMapping(VERIFY_EMAIL_PATH)
    public String verify(@RequestParam(required = false) String code,
                         HttpServletRequest request,
                         Model model,
                         Locale locale) {
        try {
            emailVerificationService.verify(code, request);
            return "redirect:" + homeUrl;
        } catch (RegistrationException e) {
            addEmail(model);
            model.addAttribute("errorMessage", messageSource.getMessage(e.messageKey(), null, locale));
            return "register-verify-email";
        }
    }

    @PostMapping(VERIFY_EMAIL_PATH + "/resend")
    public String resend(HttpServletRequest request, Model model, Locale locale) {
        if (emailVerificationService.isVerified(request)) {
            return "redirect:" + homeUrl;
        }
        addEmail(model);
        try {
            emailVerificationService.sendCode();
            model.addAttribute("infoMessage",
                    messageSource.getMessage("registration.verify.resent", null, locale));
        } catch (RuntimeException e) {
            System.err.println("[Registration] Verification code resend failed: " + e.getMessage());
            model.addAttribute("errorMessage",
                    messageSource.getMessage("registration.verify.resend-failed", null, locale));
        }
        return "register-verify-email";
    }

    private void addEmail(Model model) {
        model.addAttribute("email", CustomSecurityContext.getLoggedInUser()
                .map(CustomUser::getName)
                .orElse(null));
    }
}
