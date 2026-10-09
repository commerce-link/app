package pl.commercelink.web.orders;

/**
 * "‹ Previous · 26–50 · Next ›" under the archive. The archive pages by cursor and a search continues wherever its batch
 * stopped, so the first row is any number, not a multiple of the page size as in {@link Pagination}; the method names
 * are the ones fragments/pagination :: pages reads.
 */
public record ArchivePager(int fromIndex, int toIndex, String previousHref, String nextHref) {

    public boolean isNeeded() {
        return previousHref != null || nextHref != null;
    }

    public boolean openEnded() {
        return true;
    }

    public int totalItems() {
        return toIndex;
    }
}
