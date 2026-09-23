package pl.commercelink.inventory.supplier;

import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;

import java.util.ArrayList;
import java.util.List;

class FeedRowBatcher {

    static final int CHUNK_SIZE = 500;

    private final FeedRowProcessor feedRowProcessor;
    private final int taxonomyPenalty;
    private final FeedParseStats stats;
    private final List<ParsedRow> chunk = new ArrayList<>(CHUNK_SIZE);
    private final List<InventoryItem> accepted = new ArrayList<>();

    FeedRowBatcher(FeedRowProcessor feedRowProcessor, int taxonomyPenalty, FeedParseStats stats) {
        this.feedRowProcessor = feedRowProcessor;
        this.taxonomyPenalty = taxonomyPenalty;
        this.stats = stats;
    }

    void accept(ParsedRow parsed) {
        chunk.add(parsed);
        if (chunk.size() >= CHUNK_SIZE) {
            flush();
        }
    }

    void markInvalid() {
        stats.markInvalid();
    }

    List<InventoryItem> finish() {
        flush();
        stats.log();
        return accepted;
    }

    private void flush() {
        if (chunk.isEmpty()) {
            return;
        }
        accepted.addAll(feedRowProcessor.process(chunk, taxonomyPenalty, stats));
        chunk.clear();
    }
}
