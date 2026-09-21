package pl.commercelink.web.settings;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.commercelink.web.nav.CurrentUserRole;

@ControllerAdvice
public class SettingsPageAdvice {

    @ModelAttribute("settingsPage")
    public SettingsPage settingsPage(HttpServletRequest request) {
        return SettingsPage.forRequest(CurrentUserRole.resolve(), request.getRequestURI());
    }
}
