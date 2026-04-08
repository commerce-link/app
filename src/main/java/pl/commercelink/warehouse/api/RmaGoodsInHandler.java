package pl.commercelink.warehouse.api;

import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;

public interface RmaGoodsInHandler {
    OperationResult<Document> receive(RmaGoodsInRequest request, boolean documentsGenerationEnabled);
}
