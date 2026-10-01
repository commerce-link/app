package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.pricelist.PricelistEventScheduler;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCatalogDetailsServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";

    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PricelistEventScheduler pricelistEventScheduler;

    private ProductCatalogDetailsService service;

    @BeforeEach
    void setUp() {
        service = new ProductCatalogDetailsService(productCatalogRepository, productRepository, pricelistEventScheduler, 5);
        when(pricelistEventScheduler.snapshot(any(), any())).thenReturn(Optional.empty());
    }

    private static ProductCatalog submittedCatalog(String schedule) {
        ProductCatalog submitted = new ProductCatalog();
        submitted.setName("Main");
        submitted.setDeletionProtection(false);
        submitted.setPricelistSchedule(schedule);
        return submitted;
    }

    @Test
    void creatingCatalogSchedulesPricelistWithTheSubmittedCron() {
        // given
        when(productCatalogRepository.findById(STORE_ID, "new-cat")).thenReturn(null);
        ProductCatalog submitted = submittedCatalog("  0/30  9-17 * * ? * ");

        // when
        ProductCatalogDetailsService.UpdateResult result = service.save(STORE_ID, "new-cat", submitted);

        // then
        assertThat(result.hasErrors()).isFalse();
        verify(pricelistEventScheduler).schedule(STORE_ID, "new-cat", "0/30 9-17 * * ? *");
        assertThat(submitted.getPricelistSchedule()).isEqualTo("0/30 9-17 * * ? *");
        verify(productCatalogRepository).save(submitted);
    }

    @Test
    void changingTheCronOnAnExistingCatalogUpdatesTheSchedule() {
        // given
        ProductCatalog existing = submittedCatalog("0 5 * * ? *");
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(existing);

        // when
        service.save(STORE_ID, CATALOG_ID, submittedCatalog("0 6,14 * * ? *"));

        // then
        verify(pricelistEventScheduler).schedule(STORE_ID, CATALOG_ID, "0 6,14 * * ? *");
        assertThat(existing.getPricelistSchedule()).isEqualTo("0 6,14 * * ? *");
        verify(productCatalogRepository).save(existing);
    }

    @Test
    void savingAnExistingCatalogWithTheSameCronLeavesTheScheduleAlone() {
        // given
        ProductCatalog existing = submittedCatalog("0 5 * * ? *");
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(existing);

        // when
        service.save(STORE_ID, CATALOG_ID, submittedCatalog(" 0 5 * * ? * "));

        // then
        verify(pricelistEventScheduler, never()).schedule(any(), any(), any());
        verify(productCatalogRepository).save(existing);
    }

    @Test
    void clearingTheCronRestoresTheDefaultSchedule() {
        // given
        ProductCatalog existing = submittedCatalog("0 5 * * ? *");
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(existing);

        // when
        service.save(STORE_ID, CATALOG_ID, submittedCatalog(""));

        // then
        verify(pricelistEventScheduler).schedule(STORE_ID, CATALOG_ID, null);
        assertThat(existing.getPricelistSchedule()).isNull();
    }

    @Test
    void rejectsInvalidCronWithoutSavingOrScheduling() {
        // when
        ProductCatalogDetailsService.UpdateResult result = service.save(STORE_ID, CATALOG_ID, submittedCatalog("every 5 minutes"));

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code).containsExactly("catalog.pricelist.schedule.error.invalid");
        assertThat(result.errors().get(0).args()).containsExactly("EVERY 5 MINUTES");
        verify(productCatalogRepository, never()).save(any());
        verify(pricelistEventScheduler, never()).schedule(any(), any(), any());
    }

    @Test
    void rejectsCronBelowTheFloor() {
        // when
        ProductCatalogDetailsService.UpdateResult result = service.save(STORE_ID, CATALOG_ID, submittedCatalog("0/2 * * * ? *"));

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code).containsExactly("catalog.pricelist.schedule.error.too.frequent");
        assertThat(result.errors().get(0).args()).containsExactly("0/2 * * * ? *", 5);
        verify(productCatalogRepository, never()).save(any());
    }

    @Test
    void aFailedCatalogSaveRestoresThePreviousScheduleAndReportsTheFailure() {
        // given
        ProductCatalog existing = submittedCatalog("0 5 * * ? *");
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(existing);
        when(pricelistEventScheduler.snapshot(STORE_ID, CATALOG_ID)).thenReturn(Optional.of("cron(0 5 * * ? *)"));
        doThrow(new RuntimeException("dynamo down")).when(productCatalogRepository).save(existing);

        // when
        ProductCatalogDetailsService.UpdateResult result = service.save(STORE_ID, CATALOG_ID, submittedCatalog("0 6 * * ? *"));

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code).containsExactly("catalog.save.error.failed");
        var order = inOrder(pricelistEventScheduler, productCatalogRepository);
        order.verify(pricelistEventScheduler).schedule(STORE_ID, CATALOG_ID, "0 6 * * ? *");
        order.verify(productCatalogRepository).save(existing);
        order.verify(pricelistEventScheduler).restore(STORE_ID, CATALOG_ID, Optional.of("cron(0 5 * * ? *)"));
    }

    @Test
    void aFailedScheduleWriteNeverSavesAndRestoresNothingElse() {
        // given
        when(productCatalogRepository.findById(STORE_ID, "new-cat")).thenReturn(null);
        doThrow(new RuntimeException("eventbridge down")).when(pricelistEventScheduler).schedule(any(), any(), any());

        // when
        ProductCatalogDetailsService.UpdateResult result = service.save(STORE_ID, "new-cat", submittedCatalog("0 6 * * ? *"));

        // then
        assertThat(result.hasErrors()).isTrue();
        verify(productCatalogRepository, never()).save(any());
        verify(pricelistEventScheduler).restore(STORE_ID, "new-cat", Optional.empty());
    }

    @Test
    void aSuccessfulSaveRestoresNothing() {
        // given
        when(productCatalogRepository.findById(STORE_ID, "new-cat")).thenReturn(null);

        // when
        service.save(STORE_ID, "new-cat", submittedCatalog("0 6 * * ? *"));

        // then
        verify(pricelistEventScheduler, never()).restore(any(), any(), any());
    }

    @Test
    void deletingACatalogRemovesItsScheduleProductsAndItself() {
        // given
        ProductCatalog existing = submittedCatalog("0 5 * * ? *");
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(existing);
        when(productRepository.findAll(existing)).thenReturn(List.of());

        // when
        ProductCatalogDetailsService.UpdateResult result = service.delete(STORE_ID, CATALOG_ID);

        // then
        assertThat(result.hasErrors()).isFalse();
        var order = inOrder(pricelistEventScheduler, productRepository, productCatalogRepository);
        order.verify(pricelistEventScheduler).deleteSchedule(STORE_ID, CATALOG_ID);
        order.verify(productRepository).delete(anyList());
        order.verify(productCatalogRepository).delete(existing);
        verify(pricelistEventScheduler, never()).restore(any(), any(), any());
    }

    @Test
    void aFailedCatalogDeleteRecreatesTheSchedule() {
        // given
        ProductCatalog existing = submittedCatalog("0 5 * * ? *");
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(existing);
        when(pricelistEventScheduler.snapshot(STORE_ID, CATALOG_ID)).thenReturn(Optional.of("cron(0 5 * * ? *)"));
        doThrow(new RuntimeException("dynamo down")).when(productCatalogRepository).delete(existing);

        // when
        ProductCatalogDetailsService.UpdateResult result = service.delete(STORE_ID, CATALOG_ID);

        // then
        assertThat(result.errors()).extracting(ErrorMessage::code).containsExactly("catalog.delete.error.failed");
        verify(pricelistEventScheduler).restore(STORE_ID, CATALOG_ID, Optional.of("cron(0 5 * * ? *)"));
    }

    /**
     * RF-5: two POSTs of one new-catalog form both find no catalog and both create it; the second save loses the
     * conditional put. The catalog and its schedule are the first request's, so nothing is restored and the second
     * request is told the catalog exists rather than that the save failed.
     */
    @Test
    void aCatalogCreatedByAParallelRequestIsReportedAsCreatedAndItsScheduleIsKept() {
        // given
        when(productCatalogRepository.findById(STORE_ID, "new-cat")).thenReturn(null);
        doThrow(new ConditionalCheckFailedException("exists"))
                .when(productCatalogRepository).save(any());

        // when
        ProductCatalogDetailsService.UpdateResult result = service.save(STORE_ID, "new-cat", submittedCatalog(null));

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.createdMeanwhile()).isTrue();
        verify(pricelistEventScheduler, never()).restore(any(), any(), any());
    }

    /** A conflict on a catalog that existed before the save is still a failed save, with the schedule restored. */
    @Test
    void aConflictOnAnExistingCatalogIsStillAFailedSave() {
        // given
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(submittedCatalog("0 5 * * ? *"));
        doThrow(new ConditionalCheckFailedException("version changed"))
                .when(productCatalogRepository).save(any());

        // when
        ProductCatalogDetailsService.UpdateResult result = service.save(STORE_ID, CATALOG_ID, submittedCatalog("0 6 * * ? *"));

        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.createdMeanwhile()).isFalse();
        verify(pricelistEventScheduler).restore(any(), any(), any());
    }
}
