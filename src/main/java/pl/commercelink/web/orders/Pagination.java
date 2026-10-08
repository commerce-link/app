package pl.commercelink.web.orders;

import java.util.function.IntFunction;

/** "‹ Previous · 51–100 of 180 · Next ›" under a list; the hrefs already carry the rest of the list's query.
 *  An open-ended pagination ("51–100") is for lists that cannot be counted cheaply: it only knows whether a next page exists. */
public record Pagination(int page, int totalPages, String previousHref, String nextHref, int pageSize, int totalItems,
                         boolean openEnded) {

    public static Pagination of(int requestedPage, int totalItems, int pageSize, IntFunction<String> href) {
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        return new Pagination(page, totalPages,
                page > 1 ? href.apply(page - 1) : null,
                page < totalPages ? href.apply(page + 1) : null,
                pageSize, totalItems, false);
    }

    public static Pagination openEnded(int page, int pageSize, int shownOnPage, boolean hasNext, IntFunction<String> href) {
        int current = Math.max(1, page);
        return new Pagination(current, hasNext ? current + 1 : current,
                current > 1 ? href.apply(current - 1) : null,
                hasNext ? href.apply(current + 1) : null,
                pageSize, (current - 1) * pageSize + shownOnPage, true);
    }

    public boolean isNeeded() {
        return totalPages > 1;
    }

    public int fromIndex() {
        return Math.min((page - 1) * pageSize, totalItems);
    }

    public int toIndex() {
        return Math.min(page * pageSize, totalItems);
    }
}
