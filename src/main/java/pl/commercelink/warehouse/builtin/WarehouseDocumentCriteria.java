package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** What the documents list narrows by; every part is optional except the store. */
public record WarehouseDocumentCriteria(String storeId, DocumentType type, Set<DocumentReason> reasons,
                                        LocalDateTime from, LocalDateTime to, String numberFragment) {

    public WarehouseDocumentCriteria {
        // insertion order keeps the generated placeholders (:reason0, :reason1) stable
        reasons = reasons == null ? Set.of() : new LinkedHashSet<>(reasons);
        numberFragment = numberFragment == null || numberFragment.isBlank() ? null : numberFragment;
    }
}
