package pl.commercelink.warehouse.api;

import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;

public interface GoodsOutHandler {
    OperationResult<Document> issue(GoodsOutRequest request, boolean documentsGenerationEnabled);
}
