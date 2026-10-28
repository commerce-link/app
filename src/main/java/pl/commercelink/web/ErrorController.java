package pl.commercelink.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class ErrorController {
    // Spring Security forwards a denied request with its own method; a GET-only mapping turned a denied POST into 405
    // StoreAccessInterceptor redirects here, so the status cannot come from the denial itself
    @RequestMapping("/access-denied")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied() {
        return "error/403";
    }
}
