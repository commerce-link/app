package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.pricelist.PricelistEventScheduler;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingSchedule;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class ProductCatalogDetailsService {

    private static final List<ErrorMessage> SAVE_FAILED = List.of(ErrorMessage.of("catalog.save.error.failed"));
    private static final List<ErrorMessage> DELETE_FAILED = List.of(ErrorMessage.of("catalog.delete.error.failed"));

    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final PricelistEventScheduler pricelistEventScheduler;
    private final int minIntervalMinutes;

    public ProductCatalogDetailsService(ProductCatalogRepository productCatalogRepository,
                                        ProductRepository productRepository,
                                        PricelistEventScheduler pricelistEventScheduler,
                                        @Value("${scheduling.min-interval-minutes}") int minIntervalMinutes) {
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.pricelistEventScheduler = pricelistEventScheduler;
        this.minIntervalMinutes = minIntervalMinutes;
    }

    public int minIntervalMinutes() {
        return minIntervalMinutes;
    }

    public UpdateResult save(String storeId, String catalogId, ProductCatalog submitted) {
        String schedule = PollingSchedule.normalizeOrNull(submitted.getPricelistSchedule());
        if (schedule != null) {
            try {
                PollingSchedule.parse(schedule, minIntervalMinutes);
            } catch (InvalidScheduleException e) {
                return UpdateResult.errors(List.of(e.getReason() == InvalidScheduleException.Reason.TOO_FREQUENT
                        ? ErrorMessage.of("catalog.pricelist.schedule.error.too.frequent", schedule, minIntervalMinutes)
                        : ErrorMessage.of("catalog.pricelist.schedule.error.invalid", schedule)));
            }
        }

        Deque<Runnable> compensations = new ArrayDeque<>();
        try {
            ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
            boolean created = catalog == null;
            if (created) {
                catalog = submitted;
            }
            if (created || !Objects.equals(schedule, catalog.getPricelistSchedule())) {
                rememberSchedule(storeId, catalogId, compensations);
                pricelistEventScheduler.schedule(storeId, catalogId, schedule);
            }
            catalog.setName(submitted.getName());
            catalog.setDeletionProtection(submitted.isDeletionProtection());
            catalog.setPricelistSchedule(schedule);
            try {
                productCatalogRepository.save(catalog);
            } catch (ConditionalCheckFailedException e) {
                if (!created) {
                    throw e;
                }
                // A new catalog is saved with no version, which the mapper writes only when no item has the key yet:
                // another request with the same form created it first. That catalog and its schedule (one name per
                // catalog id) are the ones to keep, so nothing is restored.
                log.info("Catalog {} of store {} was created by a parallel request", catalogId, storeId);
                return UpdateResult.createdByAnotherRequest();
            }
        } catch (RuntimeException e) {
            log.error("Saving catalog {} of store {} failed, restoring the previous schedule", catalogId, storeId, e);
            compensate(compensations);
            return UpdateResult.errors(SAVE_FAILED);
        }
        return UpdateResult.ok();
    }

    public UpdateResult delete(String storeId, String catalogId) {
        Deque<Runnable> compensations = new ArrayDeque<>();
        try {
            ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
            rememberSchedule(storeId, catalogId, compensations);
            pricelistEventScheduler.deleteSchedule(storeId, catalogId);
            productRepository.delete(productRepository.findAll(catalog));
            productCatalogRepository.delete(catalog);
        } catch (RuntimeException e) {
            log.error("Deleting catalog {} of store {} failed, restoring the previous schedule", catalogId, storeId, e);
            compensate(compensations);
            return UpdateResult.errors(DELETE_FAILED);
        }
        return UpdateResult.ok();
    }

    private void rememberSchedule(String storeId, String catalogId, Deque<Runnable> compensations) {
        Optional<String> before = pricelistEventScheduler.snapshot(storeId, catalogId);
        compensations.push(() -> pricelistEventScheduler.restore(storeId, catalogId, before));
    }

    private void compensate(Deque<Runnable> compensations) {
        while (!compensations.isEmpty()) {
            try {
                compensations.pop().run();
            } catch (RuntimeException e) {
                log.error("Compensation step failed", e);
            }
        }
    }

    /**
     * @param createdMeanwhile the catalog did not exist when the save read it, and another request created it before
     *                         this one could: the same form sent twice. Not an error -- the catalog is there.
     */
    public record UpdateResult(List<ErrorMessage> errors, boolean createdMeanwhile) {

        public UpdateResult(List<ErrorMessage> errors) {
            this(errors, false);
        }

        static UpdateResult errors(List<ErrorMessage> errors) {
            return new UpdateResult(errors);
        }

        static UpdateResult ok() {
            return new UpdateResult(List.of());
        }

        static UpdateResult createdByAnotherRequest() {
            return new UpdateResult(List.of(), true);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
