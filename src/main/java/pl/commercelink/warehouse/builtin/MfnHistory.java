package pl.commercelink.warehouse.builtin;

import java.util.List;

public record MfnHistory(String productName, List<MfnHistoryRow> rows) {}
