package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WarehouseDocumentSearchService {

    private final WarehouseDocumentRepository warehouseDocumentRepository;
    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    List<WarehouseDocument> search(String storeId, DocumentType type, LocalDateTime dateFrom, LocalDateTime dateTo,
                                   String warehouseId, String ean, String mfn, int page, int pageSize) {
        if (isBlank(ean) && isBlank(mfn)) {
            return warehouseDocumentRepository.search(storeId, type, dateFrom, dateTo, warehouseId, page, pageSize);
        }

        List<WarehouseDocument> documents = warehouseDocumentRepository.findAllMatching(storeId, type, dateFrom, dateTo, warehouseId);

        List<WarehouseDocument> result = new ArrayList<>(pageSize);
        int fromIndex = Math.max((page - 1) * pageSize, 0);
        int index = 0;
        for (WarehouseDocument document : documents) {
            if (!warehouseDocumentItemRepository.documentContainsProduct(document.getDocumentId(), ean, mfn)) {
                continue;
            }
            if (index >= fromIndex) {
                result.add(document);
            }
            index++;
            if (result.size() > pageSize) {
                break;
            }
        }
        return result;
    }
}
