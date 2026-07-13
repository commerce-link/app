package pl.commercelink.registration;

import jakarta.servlet.http.HttpServletRequest;
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

    private final RegistrationService registrationService;
    private final MessageSource messageSource;
    private final boolean demoMode;

    public RegistrationController(RegistrationService registrationService,
                                  MessageSource messageSource,
                                  @Value("${app.registration.demo}") boolean demoMode) {
        this.registrationService = registrationService;
        this.messageSource = messageSource;
        this.demoMode = demoMode;
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("demoMode", demoMode);
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam(required = false) String storeName,
                           @RequestParam(name = "company", required = false) String honeypot,
                           HttpServletRequest request,
                           Model model,
                           Locale locale) {
        if (isNotBlank(honeypot)) {
            return "redirect:/register";
        }
        model.addAttribute("demoMode", demoMode);
        String resolvedName = isNotBlank(storeName) ? storeName
                : messageSource.getMessage("registration.store-name.placeholder", null, locale);
        try {
            RegistrationResult result = registrationService.register(email, resolvedName, clientIp(request));
            model.addAttribute("email", email);
            model.addAttribute("revealedPassword", result.revealedPassword());
            return "register-success";
        } catch (RegistrationException e) {
            model.addAttribute("email", email);
            model.addAttribute("errorMessage", messageSource.getMessage(e.messageKey(), null, locale));
            return "register";
        }
    }

    @GetMapping("/demo/register")
    public String legacyRedirect() {
        return "redirect:/register";
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return isNotBlank(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
