package pl.commercelink.web.rma;

import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Outcomes of RMA actions, shown in the page body instead of the layout's full-width banner. Refusals keep going
 * through errorMessage; {@link #adoptLegacy} moves those (and the remaining successMessage flashes) into the page too.
 */
public final class RmaFlash {

    public static final String ATTRIBUTE = "rmaNotice";

    private RmaFlash() {
    }

    public static void items(RedirectAttributes redirectAttributes, RmaNotice notice) {
        redirectAttributes.addFlashAttribute(ATTRIBUTE, notice.inScope(RmaNotice.Scope.ITEMS));
    }

    public static void page(RedirectAttributes redirectAttributes, RmaNotice notice) {
        redirectAttributes.addFlashAttribute(ATTRIBUTE, notice.inScope(RmaNotice.Scope.PAGE));
    }

    /** Turns a legacy successMessage / errorMessage in the model into a page notice, unless one is already there. */
    public static void adoptLegacy(Model model) {
        Map<String, Object> attributes = model.asMap();
        Object success = attributes.remove("successMessage");
        Object error = attributes.remove("errorMessage");
        if (attributes.containsKey(ATTRIBUTE)) {
            return;
        }
        if (error != null) {
            model.addAttribute(ATTRIBUTE, new RmaNotice(RmaNotice.BAD, error.toString(), List.of(), RmaNotice.Scope.PAGE));
        } else if (success != null) {
            model.addAttribute(ATTRIBUTE, new RmaNotice(RmaNotice.OK, success.toString(), List.of(), RmaNotice.Scope.PAGE));
        }
    }
}
