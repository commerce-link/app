package pl.commercelink.web.settings;

import org.springframework.context.MessageSource;
import pl.commercelink.marketplace.links.OfferMatchResult;
import pl.commercelink.starter.csv.CSVReady;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Every offer of one group of the latest matching run as a CSV file: the page lists at most
 * {@link OfferMatchingView#ROW_LIMIT} rows per group, while a group can hold thousands of offers (the ones to review, or
 * a store's offers without a PIM card, kept for preparing the cards). Same order as the page: active offers first.
 * Semicolon-separated UTF-8 with a BOM, like the reports' exports, so a spreadsheet opens it with Polish characters.
 */
public final class OfferMatchingCsv {

    public static final Set<String> GROUPS = Set.of("linked", "notInCatalog", "noPimEntry", "toReview");

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String KEY = "store.marketplaces.offer.matching.csv.";
    private static final Map<String, Function<OfferMatchResult, List<OfferMatchResult.Row>>> ROWS = Map.of(
            "linked", OfferMatchResult::linked,
            "notInCatalog", OfferMatchResult::notInCatalog,
            "noPimEntry", OfferMatchResult::noPimEntry,
            "toReview", OfferMatchResult::toReview);

    private OfferMatchingCsv() {
    }

    /** Empty for a name that is not one of the {@link #GROUPS}. */
    public static Optional<byte[]> of(OfferMatchResult result, String group, MessageSource messages, Locale locale) {
        Function<OfferMatchResult, List<OfferMatchResult.Row>> rowsOf = ROWS.get(group);
        if (rowsOf == null) {
            return Optional.empty();
        }
        List<OfferMatchResult.Row> rows = Optional.ofNullable(rowsOf.apply(result)).orElse(List.of()).stream()
                .sorted(Comparator.comparing((OfferMatchResult.Row row) -> Boolean.FALSE.equals(row.active())))
                .toList();
        String yes = messages.getMessage(KEY + "active.yes", null, locale);
        String no = messages.getMessage(KEY + "active.no", null, locale);
        List<CSVReady> lines = rows.stream()
                .map(row -> (CSVReady) () -> new String[]{
                        nullToEmpty(row.sellerOfferKey()),
                        nullToEmpty(row.title()),
                        row.eans() == null ? "" : String.join(", ", row.eans()),
                        Boolean.FALSE.equals(row.active()) ? no : yes,
                        nullToEmpty(row.pimId()),
                        reason(row.reason(), messages, locale)})
                .toList();
        String[] headers = {
                messages.getMessage(KEY + "column.key", null, locale),
                messages.getMessage(KEY + "column.title", null, locale),
                messages.getMessage(KEY + "column.eans", null, locale),
                messages.getMessage(KEY + "column.active", null, locale),
                messages.getMessage(KEY + "column.product", null, locale),
                messages.getMessage(KEY + "column.reason", null, locale)};
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(UTF8_BOM);
            out.write(new CSVWriter().writeAllRowsToBytes(lines, headers));
            return Optional.of(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // the page's sentence for a reason; an offer linked by an earlier run says so, an unknown code stays as it is
    private static String reason(String reason, MessageSource messages, Locale locale) {
        if (reason == null) {
            return "";
        }
        if (OfferMatchResult.ALREADY_LINKED.equals(reason)) {
            return messages.getMessage("store.marketplaces.offer.matching.linkedBefore", null, locale);
        }
        return messages.getMessage("store.marketplaces.offer.matching.reason." + reason, null, reason, locale);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
