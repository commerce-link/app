package pl.commercelink.demo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
@ConditionalOnProperty(name = "app.demo.registration.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoRegistrationController {

    private final DemoRegistrationService demoRegistrationService;
    private final MessageSource messageSource;

    @GetMapping("/demo/register")
    public String registerPage() {
        return "demo-register";
    }

    @PostMapping("/demo/register")
    public String register(@RequestParam String email,
                           @RequestParam(name = "company", required = false) String honeypot,
                           HttpServletRequest request,
                           Model model,
                           Locale locale) {
        if (isNotBlank(honeypot)) {
            return "redirect:/demo/register";
        }
        try {
            DemoRegistrationResult result = demoRegistrationService.register(email, clientIp(request));
            model.addAttribute("email", email);
            model.addAttribute("revealedPassword", result.revealedPassword());
            return "demo-register-success";
        } catch (DemoRegistrationException e) {
            model.addAttribute("email", email);
            model.addAttribute("errorMessage", messageSource.getMessage(e.messageKey(), null, locale));
            return "demo-register";
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return isNotBlank(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
