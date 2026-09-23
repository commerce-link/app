package pl.commercelink.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorController {

    // Method-agnostic: the access-denied page is reached by a FORWARD that preserves the original
    // request method (e.g. a CSRF-denied POST). A GET-only mapping would answer that forward with
    // 405 and mask the 403 the AccessDeniedHandler already set.
    @RequestMapping("/access-denied")
    public String accessDenied() {
        return "error/403";
    }
}
