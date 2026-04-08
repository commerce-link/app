package pl.commercelink.starter.security;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {
    @GetMapping("/logout-success")
    public String logoutSuccess() {
        return "logout-success";
    }

    @GetMapping("/login")
    public String redirectToCognito() {
        return "redirect:/oauth2/authorization/cognito";
    }
}