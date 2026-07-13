package pl.commercelink.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.web.dtos.OrderWithItemsDto;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AiController extends BaseController {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "/dashboard/ai/orders/{status}/items", produces = MediaType.APPLICATION_JSON_VALUE)
    public String ordersWithItems(@PathVariable String status,
                                  @RequestParam(required = false) List<FulfilmentStatus> itemStatus) throws JsonProcessingException {
        OrderStatus orderStatus = Arrays.stream(OrderStatus.values())
                .filter(s -> s.name().equalsIgnoreCase(status))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order status: " + status));
        if (orderStatus.isOneOf(OrderStatus.Completed, OrderStatus.Cancelled)) {
            throw new IllegalArgumentException("Order status not allowed: " + status);
        }
        boolean filterByItemStatus = itemStatus != null && !itemStatus.isEmpty();
        List<OrderWithItemsDto> orders = ordersRepository.findAllByStoreIdAndStatus(getStoreId(), orderStatus).stream()
                .sorted(Comparator.comparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(order -> OrderWithItemsDto.from(order, filterByItemStatus
                        ? orderItemsRepository.findByOrderIdAndStatuses(order.getOrderId(), itemStatus)
                        : orderItemsRepository.findByOrderId(order.getOrderId())))
                .filter(dto -> !filterByItemStatus || !dto.items().isEmpty())
                .collect(Collectors.toList());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(orders);
    }
}
