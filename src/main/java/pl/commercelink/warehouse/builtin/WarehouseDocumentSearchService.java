package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WarehouseDocumentSearchService {

    private final WarehouseDocumentRepository warehouseDocumentRepository;
    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    /**
     * How many documents match, or empty for an EAN: that would read the items of every document in the range,
     * so the list then only knows whether a next page exists.
     */
    OptionalInt count(WarehouseDocumentCriteria criteria, String ean) {
        return isBlank(ean) ? OptionalInt.of(warehouseDocumentRepository.count(criteria)) : OptionalInt.empty();
    }

    /**
     * One page of documents, newest first, plus one more when a next page exists. An EAN is matched against every
     * document's items (normalised like stored items); the read stops at the page + 1.
     */
    List<WarehouseDocument> search(WarehouseDocumentCriteria criteria, String ean, int page, int pageSize) {
        if (isBlank(ean)) {
            return warehouseDocumentRepository.search(criteria, page, pageSize);
        }
        String unifiedEan = UnifiedProductIdentifiers.unifyEan(ean.trim());

        List<WarehouseDocument> result = new ArrayList<>(pageSize + 1);
        int skip = Math.max(page - 1, 0) * pageSize;
        int matched = 0;
        for (WarehouseDocument document : warehouseDocumentRepository.findAllMatching(criteria)) {
            if (!warehouseDocumentItemRepository.containsProduct(document.getDocumentId(), unifiedEan)) {
                continue;
            }
            if (matched++ >= skip) {
                result.add(document);
                if (result.size() > pageSize) {
                    break;
                }
            }
        }
        return result;
    }
}
