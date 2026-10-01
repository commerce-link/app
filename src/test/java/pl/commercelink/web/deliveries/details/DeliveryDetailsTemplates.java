package pl.commercelink.web.deliveries.details;

import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the delivery details page from a model, the way the controller serves it (Polish messages, real layout). */
final class DeliveryDetailsTemplates {

    private DeliveryDetailsTemplates() {
    }

    static String render(DeliveryPageData data, DeliveryViewer viewer) {
        return render(DeliveryPageModelFactory.build(data, viewer), Map.of());
    }

    static String render(DeliveryPageModel page, Map<String, Object> extra) {
        String html = renderPage(page, extra);
        int start = html.indexOf("<section class=\"cl-page\"");
        return html.substring(start, html.indexOf("</main>", start));
    }

    /** The whole document, layout included, for assertions about what the layout adds around the page. */
    static String renderPage(DeliveryPageModel page, Map<String, Object> extra) {
        Map<String, Object> variables = new HashMap<>(extra);
        variables.put("navigation", null);
        variables.put("page", page);
        return SettingsTemplateRenderer.render("deliveries/details", variables);
    }

    static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    /** Names of every field a form would post: inputs, selects and textareas, with the name once per element. */
    static Map<String, Integer> fieldNameCounts(String html) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = Pattern.compile("<(?:input|select|textarea)[^>]*\\sname=\"([^\"]+)\"").matcher(html);
        while (matcher.find()) {
            counts.merge(matcher.group(1), 1, Integer::sum);
        }
        return counts;
    }
}
