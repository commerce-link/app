package pl.commercelink.web.orders;

/**
 * "‹ Previous · 26–50 z 644 · Next ›" under the archive. The archive pages by cursor and a search continues wherever its
 * batch stopped, so the first row is any number, not a multiple of the page size as in {@link Pagination}; the method
 * names are the ones fragments/pagination :: pages reads. total is null where counting would mean reading every whole
 * order (a text or e-mail search): the range then stays open ("26–50").
 */
public record ArchivePager(int fromIndex, int toIndex, String previousHref, String nextHref, Long total) {

    public boolean isNeeded() {
        return previousHref != null || nextHref != null;
    }

    public boolean openEnded() {
        return total == null;
    }

    /** The count is a second read: an order reopened in between must not leave "26–50 z 40". */
    public int totalItems() {
        return total == null ? toIndex : (int) Math.max(total, toIndex);
    }
}
