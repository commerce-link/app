package pl.commercelink.warehouse.builtin;

import java.util.List;

public record EanHistory(String productName, List<EanHistoryRow> rows) {}
