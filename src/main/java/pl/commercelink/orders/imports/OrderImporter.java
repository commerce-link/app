package pl.commercelink.orders.imports;

import pl.commercelink.orders.Order;
import pl.commercelink.web.dtos.ClientDataDto;

public interface OrderImporter {
    boolean supports(String storeId, ClientDataDto dto);
    Order _import(String storeId, ClientDataDto dto);
}
