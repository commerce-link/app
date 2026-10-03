package pl.commercelink.web.offers;

import pl.commercelink.web.orders.Pagination;

import java.util.List;

/** Everything the offers list template renders, texts resolved (spec §3). Only the active segment's rows are filled. */
public record OfferListPage(OfferListQuery query, List<SegmentLink> segments, List<Option> validityOptions,
                            String validitySummary, DateMenu dates, List<Chip> chips, String resultsLine,
                            List<OfferRow> offers, List<TemplateRow> templates, List<BasketRow> baskets,
                            Pagination pagination, EmptyState emptyState, int activeFilterCount) {

    public boolean isEmpty() {
        return offers.isEmpty() && templates.isEmpty() && baskets.isEmpty();
    }

    public record SegmentLink(String label, String href, boolean active) { }
    public record Option(String value, String label, boolean selected) { }
    public record DateMenu(String value, String from, String to) { }
    public record Chip(String label, String clearHref, String clearLabel) { }
    public record EmptyState(String text, String actionLabel, String actionHref) { }

    public record OfferRow(String href, String name, String shortId, String fullId, String itemsText, String client,
                           String email, String created, String author, String validityLabel, String validityTone,
                           String validityNote, boolean validityNoteWarn, String gross, String net, String clientUrl,
                           String copyHref, String copyWithContactHref, String deleteHref, String deleteTitle,
                           String deleteMessage, String menuLabel, String copyLabel) { }

    public record TemplateRow(String href, String name, String shortId, String fullId, String itemsText, String created,
                              String author, String gross, String createOfferHref, String deleteHref, String deleteTitle,
                              String deleteMessage, String menuLabel) { }

    public record BasketRow(String href, String shortId, String fullId, String client, String created, String itemsText,
                            String gross) { }
}
