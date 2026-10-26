package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.products.information.StorePimAliases;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellingPriceHistoryServiceTest {

    @Mock
    private SellingPriceHistoryRepository repository;
    @Mock
    private StorePimAliases aliases;
    @Captor
    private ArgumentCaptor<Collection<SellingPriceHistory>> saved;

    @Test
    void historyIsWrittenUnderCloneAndSource() throws java.io.IOException {
        // given
        when(repository.load("store-1", "cat")).thenReturn(new HashMap<>());
        when(aliases.sourceOf("store-1", "clone")).thenReturn(Optional.of("source"));

        // when
        new SellingPriceHistoryService(repository, aliases).update("store-1", "cat", List.of(row("clone", 500)));

        // then
        Map<String, Long> lowest = lowestByPimId();
        assertEquals(500L, lowest.get("clone"));
        assertEquals(500L, lowest.get("source"));
    }

    @Test
    void afterDeletingTheCloneTheSourceHistoryIsComplete() throws java.io.IOException {
        // given: yesterday under the source, today only the clone was priced
        Map<String, SellingPriceHistory> histories = new HashMap<>();
        histories.put("source", new SellingPriceHistory("source", Map.of(LocalDate.now().minusDays(1), 600L)));
        when(repository.load("store-1", "cat")).thenReturn(histories);
        when(aliases.sourceOf("store-1", "clone")).thenReturn(Optional.of("source"));

        // when
        new SellingPriceHistoryService(repository, aliases).update("store-1", "cat", List.of(row("clone", 500)));

        // then
        verify(repository).save(eq("store-1"), eq("cat"), saved.capture());
        SellingPriceHistory source = saved.getValue().stream().filter(h -> h.getPimId().equals("source")).findFirst().orElseThrow();
        assertEquals(Map.of(LocalDate.now().minusDays(1), 600L, LocalDate.now(), 500L), source.getDailyLowestPrices());
    }

    @Test
    void plainProductsAreUnchanged() throws java.io.IOException {
        // given
        when(repository.load("store-1", "cat")).thenReturn(new HashMap<>());
        when(aliases.sourceOf("store-1", "plain")).thenReturn(Optional.empty());

        // when
        new SellingPriceHistoryService(repository, aliases).update("store-1", "cat", List.of(row("plain", 500)));

        // then
        assertEquals(Map.of("plain", 500L), lowestByPimId());
    }

    private Map<String, Long> lowestByPimId() throws java.io.IOException {
        verify(repository).save(eq("store-1"), eq("cat"), saved.capture());
        Map<String, Long> result = new HashMap<>();
        saved.getValue().forEach(h -> result.put(h.getPimId(), h.getLowestPrice30d()));
        return result;
    }

    private AvailabilityAndPrice row(String pimId, long price) {
        return new AvailabilityAndPrice(pimId, "ean", "mfn", "brand", "label", "name", "cat", price, 1, 1, price, false);
    }
}
