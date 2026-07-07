package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.executeUpdate;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.scanAndProcess;

@ChangeUnit(id = "V005-backfill-order-item-positions", order = "005", author = "commercelink")
@RequiredArgsConstructor
public class V005_BackfillOrderItemPositions {

    private static final String TABLE_NAME = "OrderItems";
    private static final String UPDATE_EXPRESSION = "SET #p = if_not_exists(#p, :position)";
    private static final Map<String, String> EXPRESSION_ATTRIBUTE_NAMES = Map.of("#p", "position");


    private static final List<String> LEGACY_CATEGORY_ORDER = List.of(
            "CPU", "Cooler", "GPU", "Motherboard", "PSU", "Storage", "Memory", "Case", "Fan", "ModdingPC", "Other",
            "Services",
            "Laptops", "Desktops", "Workstations", "Servers", "AllInOnePCs", "GraphicsTablets", "Software",
            "Smartphones", "StationaryPhones", "Tablets", "SmartphoneCases", "ScreenProtectors", "Chargers",
            "Powerbanks", "MobileHeadphones",
            "Printers", "LaserPrinters", "InkPrinters", "PhotoPrinters", "LargeFormatPrinters", "LabelPrinters",
            "Printers3D", "Scanners", "MultifunctionPrinters",
            "Displays", "Keyboards", "Mice", "KeyboardsAndMice", "Headphones", "Microphones", "Webcams",
            "Speakers", "MousePads",
            "GamingChairs", "OfficeChairs", "GamingDesks", "OfficeDesks", "StandingDesks", "MonitorMounts",
            "Footrests");

    private final OrderItemsRepository orderItemsRepository;
    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void backfillPositions() {
        Set<String> orderIds = new LinkedHashSet<>();
        scanAndProcess(dynamoDB, TABLE_NAME, List.of("orderId"),
                key -> orderIds.add(key.get("orderId").getS()));

        for (String orderId : orderIds) {
            List<OrderItem> displayOrderedItems = orderItemsRepository.findByOrderId(orderId).stream()
                    .sorted(Comparator.comparingInt(V005_BackfillOrderItemPositions::displaySequence)
                            .thenComparing(OrderItem::getItemId))
                    .toList();

            for (int position = 0; position < displayOrderedItems.size(); position++) {
                backfillPosition(displayOrderedItems.get(position), position);
            }
        }
    }

    private void backfillPosition(OrderItem orderItem, int position) {
        Map<String, AttributeValue> key = Map.of(
                "orderId", new AttributeValue().withS(orderItem.getOrderId()),
                "itemId", new AttributeValue().withS(orderItem.getItemId()));

        executeUpdate(dynamoDB, TABLE_NAME, key, UPDATE_EXPRESSION, EXPRESSION_ATTRIBUTE_NAMES,
                Map.of(":position", new AttributeValue().withN(String.valueOf(position))));
    }

    private static int displaySequence(OrderItem orderItem) {
        if (orderItem.getCategory() == null) {
            return Integer.MAX_VALUE;
        }
        int index = LEGACY_CATEGORY_ORDER.indexOf(orderItem.getCategory());
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    @RollbackExecution
    public void rollback() {
    }
}
