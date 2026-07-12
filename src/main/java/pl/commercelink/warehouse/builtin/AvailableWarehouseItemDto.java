package pl.commercelink.warehouse.builtin;

public record AvailableWarehouseItemDto(
        String itemId,
        String category,
        String name,
        String mfn,
        String ean,
        String serialNo,
        int qty,
        String status
) {

    static AvailableWarehouseItemDto from(WarehouseItem item) {
        return new AvailableWarehouseItemDto(
                item.getItemId(),
                item.getCategory(),
                item.getName(),
                item.getManufacturerCode(),
                item.getEan(),
                item.getSerialNo(),
                item.getQty(),
                item.getStatus().name()
        );
    }
}
