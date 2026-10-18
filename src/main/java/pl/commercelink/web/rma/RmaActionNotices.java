package pl.commercelink.web.rma;

import org.springframework.context.MessageSource;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Builds the outcome of a bulk item action: how many items it processed and which selected ones it skipped and why. */
public class RmaActionNotices {

    private final MessageSource messageSource;

    public RmaActionNotices(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public RmaNotice done(String doneKey, List<RMAItem> selected, Collection<String> processedIds, Locale locale) {
        String text = messageSource.getMessage(doneKey, new Object[]{processedIds.size()}, locale);
        List<RMAItem> skipped = selected.stream()
                .filter(item -> !processedIds.contains(item.getRmaItemId()))
                .toList();
        List<String> lines = List.of();
        if (!skipped.isEmpty()) {
            String items = skipped.stream()
                    .map(item -> item.getName() + " — " + reason(item, locale))
                    .collect(Collectors.joining("; "));
            lines = List.of(messageSource.getMessage("rma.detail.skipped", new Object[]{skipped.size(), items}, locale));
        }
        String tone = processedIds.isEmpty() ? RmaNotice.WARN : RmaNotice.OK;
        return new RmaNotice(tone, text, lines, RmaNotice.Scope.ITEMS);
    }

    private String reason(RMAItem item, Locale locale) {
        // as on the page: an item stored without a status is New
        RMAItemStatus status = item.getStatus() == null ? RMAItemStatus.New : item.getStatus();
        return messageSource.getMessage("rma.detail.skip." + status.name(), null, locale);
    }
}
