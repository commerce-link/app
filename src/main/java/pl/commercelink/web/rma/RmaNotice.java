package pl.commercelink.web.rma;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * An outcome of an RMA action shown inside the page body after a redirect. Tone is "is-ok", "is-warn" or "is-bad";
 * lines carry the detail (which selected items were skipped and why).
 */
public record RmaNotice(String tone, String text, List<String> lines, Scope scope) implements Serializable {

    public static final String OK = "is-ok";
    public static final String WARN = "is-warn";
    public static final String BAD = "is-bad";

    /** PAGE is shown above the page content, ITEMS next to the items list the action was taken on. */
    public enum Scope { PAGE, ITEMS }

    public RmaNotice withLine(String line) {
        List<String> all = new ArrayList<>(lines);
        all.add(line);
        return new RmaNotice(tone, text, List.copyOf(all), scope);
    }

    RmaNotice inScope(Scope newScope) {
        return new RmaNotice(tone, text, lines, newScope);
    }
}
