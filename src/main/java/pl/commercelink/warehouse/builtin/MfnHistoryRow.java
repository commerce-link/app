package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentType;

import java.time.LocalDateTime;

public record MfnHistoryRow(String documentId, String documentNo, DocumentType documentType, LocalDateTime createdAt,
                            int qty, int stockChange, int stockAfter) {}
