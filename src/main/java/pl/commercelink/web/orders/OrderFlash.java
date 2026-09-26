package pl.commercelink.web.orders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Outcomes of order actions, shown in the page (orders/parts :: notice) instead of the layout's full-width Bulma banner.
 * Refusals keep going through errorMessage, which the layout shows for every screen (design-system debt §7).
 */
public final class OrderFlash {

    public static final String ATTRIBUTE = "orderNotice";

    private OrderFlash() {
    }

    public static void saved(RedirectAttributes redirectAttributes, String text) {
        redirectAttributes.addFlashAttribute(ATTRIBUTE, new OrderNotice(OrderLabels.OK, text, null, null));
    }

    public static void warning(RedirectAttributes redirectAttributes, String text) {
        redirectAttributes.addFlashAttribute(ATTRIBUTE, new OrderNotice(OrderLabels.WARN, text, null, null));
    }

    public static void savedWithLink(RedirectAttributes redirectAttributes, String text, String href, String linkText) {
        redirectAttributes.addFlashAttribute(ATTRIBUTE, new OrderNotice(OrderLabels.OK, text, href, linkText));
    }

    /** A dialog saved without reloading answers 200 and the script navigates; the notice waits for that page. */
    public static void forNextPage(HttpServletRequest request, HttpServletResponse response, String path, OrderNotice notice) {
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put(ATTRIBUTE, notice);
        RequestContextUtils.saveOutputFlashMap(path, request, response);
    }
}
