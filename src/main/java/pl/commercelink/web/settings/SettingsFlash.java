package pl.commercelink.web.settings;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Outcome of an action shown on the settings page it returns to, inside the page body (fragments/settings-form ::
 * savedAlert) rather than in the full-width banner of the layout.
 */
public final class SettingsFlash {

    public static final String SAVED_MESSAGE = "settingsSavedMessage";

    private SettingsFlash() {
    }

    public static void onRedirect(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(SAVED_MESSAGE, message);
    }

    /**
     * A form saved without reloading answers 200 and the script navigates to the list itself, so the message is stored
     * for that page here instead of through a redirect.
     */
    public static void forNextPage(HttpServletRequest request, HttpServletResponse response, String path, String message) {
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put(SAVED_MESSAGE, message);
        RequestContextUtils.saveOutputFlashMap(path, request, response);
    }
}
