package pl.commercelink.products.information;

import pl.commercelink.pim.api.PIMEntryAddedEvent;
import pl.commercelink.pim.api.PIMEntryDeletedEvent;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryRequest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

class EmptyPimCatalog implements PimCatalog {

    @Override
    public List<PimEntry> findAll() {
        return List.of();
    }

    @Override
    public Optional<PimEntry> findByPimId(String pimId) {
        return Optional.empty();
    }

    @Override
    public Optional<PimEntry> findByGtin(String gtin) {
        return Optional.empty();
    }

    @Override
    public Optional<PimEntry> findByMpn(String mpn) {
        return Optional.empty();
    }

    @Override
    public Optional<PimEntry> findByGtinOrMpn(String gtin, String mpn) {
        return Optional.empty();
    }

    @Override
    public void submit(PimEntryRequest request) {
    }

    @Override
    public void refresh() {
    }

    @Override
    public void onEntryAdded(Consumer<PIMEntryAddedEvent> listener) {
    }

    @Override
    public void onEntryDeleted(Consumer<PIMEntryDeletedEvent> listener) {
    }
}
