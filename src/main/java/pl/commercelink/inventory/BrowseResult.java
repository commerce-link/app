package pl.commercelink.inventory;

import java.util.List;

/** One page of a browse query; {@code truncated} when a text query hit {@link BrowseCriteria#MAX_TEXT_MATCHES}. */
public record BrowseResult(List<BrowseRow> rows, int total, boolean truncated) {
}
