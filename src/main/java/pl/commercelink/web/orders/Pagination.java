package pl.commercelink.web.orders;

import java.util.function.IntFunction;

/** "‹ Previous · Page 2 of 4 · Next ›" under a list; the hrefs already carry the rest of the list's query. */
public record Pagination(int page, int totalPages, String previousHref, String nextHref, int pageSize, int totalItems) {

    public static Pagination of(int requestedPage, int totalItems, int pageSize, IntFunction<String> href) {
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        return new Pagination(page, totalPages,
                page > 1 ? href.apply(page - 1) : null,
                page < totalPages ? href.apply(page + 1) : null,
                pageSize, totalItems);
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
