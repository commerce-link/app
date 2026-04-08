package pl.commercelink.warehouse.api;

import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;

public interface GoodsInHandler {
    OperationResult<Document> receive(GoodsInRequest goodsInRequest, boolean documentsGenerationEnabled);
}
