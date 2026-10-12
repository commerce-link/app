package pl.commercelink.orders;

import java.time.LocalDate;
import java.util.Set;

/**
 * What to read from the archive index: the statuses (a post-read filter), the order days from–to (both inclusive, in the
 * key condition), where to start (exclusive) and in which direction, and how many index entries one DynamoDB call reads
 * before the filter (pageLimit).
 */
public record ArchiveIndexRequest(Set<OrderStatus> statuses, LocalDate from, LocalDate to, ArchiveCursor start,
                                  boolean newestFirst, int pageLimit) {
}
