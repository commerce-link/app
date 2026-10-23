package pl.commercelink.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorController {
    // Spring Security forwards a denied request with its own method; a GET-only mapping turned a denied POST into 405
    @RequestMapping("/access-denied")
    public String accessDenied() {
        return "error/403";
    }
}
