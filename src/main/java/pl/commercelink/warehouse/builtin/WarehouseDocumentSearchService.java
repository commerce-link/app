package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WarehouseDocumentSearchService {

    private final WarehouseDocumentRepository warehouseDocumentRepository;
    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    /**
     * One page of documents, newest first, plus one more when a next page exists. A product code is matched against
     * every document's items (EAN or manufacturer code, normalised like stored items); the read stops at the page + 1.
     */
    List<WarehouseDocument> search(WarehouseDocumentCriteria criteria, String productCode, int page, int pageSize) {
        if (isBlank(productCode)) {
            return warehouseDocumentRepository.search(criteria, page, pageSize);
        }
        String code = productCode.trim();
        String ean = UnifiedProductIdentifiers.unifyEan(code);
        String mfn = UnifiedProductIdentifiers.unifyMfn(code);

        List<WarehouseDocument> result = new ArrayList<>(pageSize + 1);
        int skip = Math.max(page - 1, 0) * pageSize;
        int matched = 0;
        for (WarehouseDocument document : warehouseDocumentRepository.findAllMatching(criteria)) {
            if (!warehouseDocumentItemRepository.containsProduct(document.getDocumentId(), ean, mfn)) {
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
