package pl.commercelink.web.stores;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.TrialPeriod;
import pl.commercelink.stores.TrialStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoresListPageFactoryTest {

    @Mock private StoreTrialService storeTrialService;
    @Mock private StoreActivity storeActivity;

    private StoresListPageFactory factory;

    @BeforeEach
    void setUp() {
        factory = new StoresListPageFactory(storeTrialService, storeActivity);
        when(storeTrialService.status(any())).thenReturn(Optional.empty());
        when(storeActivity.status(any())).thenReturn(Optional.empty());
    }

    private static Store store(String id, String name, String createdAt) {
        Store store = new Store();
        store.setStoreId(id);
        store.setName(name);
        store.setCreatedAt(createdAt);
        return store;
    }

    private Store onTrial(String id, String name, long daysLeft) {
        Store store = store(id, name, "2026-10-01T10:00:00Z");
        store.setTrial(new TrialPeriod("owner@" + id + ".pl", "2026-10-01T10:00:00Z", "2026-10-15T10:00:00Z"));
        when(storeTrialService.status(store)).thenReturn(Optional.of(new TrialStatus(LocalDate.parse("2026-10-15"), daysLeft, false)));
        return store;
    }

    private Store trialEnded(String id) {
        Store store = store(id, "Ended " + id, "2026-09-01T10:00:00Z");
        store.setTrial(new TrialPeriod("owner@" + id + ".pl", "2026-09-01T10:00:00Z", "2026-09-15T10:00:00Z"));
        when(storeActivity.status(store)).thenReturn(Optional.of(new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
                LocalDate.parse("2026-09-15"), LocalDate.parse("2026-09-29"), 3)));
        return store;
    }

    private Store switchedOff(String id) {
        Store store = store(id, "Off " + id, "2026-05-01T10:00:00Z");
        when(storeActivity.status(store)).thenReturn(Optional.of(new DeactivationStatus(DeactivationReason.MANUAL,
                LocalDate.parse("2026-10-03"), null, 0)));
        return store;
    }

    private static Store demo(String id) {
        Store store = store(id, "Demo " + id, "2026-10-10T10:00:00Z");
        store.setDemo(new DemoStoreMetadata("demo@" + id + ".pl", "2026-10-10T10:00:00Z", "2026-10-17T10:00:00Z"));
        return store;
    }

    @Test
    void mapsEveryAccountStateToPillNoteAndActions() {
        // given
        Store full = store("full000001", "Full", "2026-01-01T10:00:00Z");
        Store trial = onTrial("trial00001", "Trial", 9);
        Store ending = onTrial("ending0001", "Ending", 3);
        Store ended = trialEnded("ended00001");
        Store off = switchedOff("off0000001");
        Store demo = demo("demo000001");

        // when
        List<StoreRow> rows = factory.build(List.of(full, trial, ending, ended, off, demo), StoresListQuery.defaults()).rows();

        // then
        StoreRow fullRow = row(rows, "full000001");
        assertThat(fullRow.state()).isEqualTo(StoreState.FULL);
        assertThat(fullRow.noteDate()).isNull();
        assertThat(fullRow.canDeactivate()).isTrue();
        assertThat(fullRow.canConvert()).isFalse();
        assertThat(fullRow.canActivate()).isFalse();

        StoreRow trialRow = row(rows, "trial00001");
        assertThat(trialRow.state()).isEqualTo(StoreState.TRIAL);
        assertThat(trialRow.noteDate()).isEqualTo("15.10.2026");
        assertThat(trialRow.noteDays()).isEqualTo(9);
        assertThat(trialRow.canConvert()).isTrue();
        assertThat(trialRow.canDeactivate()).isTrue();

        assertThat(row(rows, "ending0001").state()).isEqualTo(StoreState.TRIAL_ENDING);

        StoreRow endedRow = row(rows, "ended00001");
        assertThat(endedRow.state()).isEqualTo(StoreState.TRIAL_ENDED);
        assertThat(endedRow.noteDate()).isEqualTo("29.09.2026");
        assertThat(endedRow.canConvert()).isTrue();
        assertThat(endedRow.canDeactivate()).isFalse();
        assertThat(endedRow.canActivate()).isFalse();
        assertThat(endedRow.activationBlocked()).isTrue();

        StoreRow offRow = row(rows, "off0000001");
        assertThat(offRow.state()).isEqualTo(StoreState.SWITCHED_OFF);
        assertThat(offRow.noteDate()).isEqualTo("03.10.2026");
        assertThat(offRow.canActivate()).isTrue();
        assertThat(offRow.activationBlocked()).isFalse();

        StoreRow demoRow = row(rows, "demo000001");
        assertThat(demoRow.state()).isEqualTo(StoreState.DEMO);
        assertThat(demoRow.noteDate()).isEqualTo("17.10.2026");
        assertThat(demoRow.demo()).isTrue();
    }

    @Test
    void segmentsAreDisjointAndCountedAfterSearch() {
        // given
        List<Store> stores = List.of(store("full000001", "Alfa", null), onTrial("trial00001", "Alfa trial", 9),
                trialEnded("ended00001"), switchedOff("off0000001"), store("full000002", "Beta", null));

        // when
        StoresListPage page = factory.build(stores, StoresListQuery.of(null, "alfa", null, null, null));

        // then
        assertThat(page.segments()).extracting(StoresListPage.SegmentLink::labelKey, StoresListPage.SegmentLink::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("stores.list.segment.all", 2L),
                        org.assertj.core.groups.Tuple.tuple("stores.list.segment.full", 1L),
                        org.assertj.core.groups.Tuple.tuple("stores.list.segment.trial", 1L),
                        org.assertj.core.groups.Tuple.tuple("stores.list.segment.inactive", 0L));
        assertThat(page.segments().get(2).href()).isEqualTo("/dashboard/stores?status=trial&q=alfa");
        assertThat(page.rows()).extracting(StoreRow::storeId).containsExactlyInAnyOrder("full000001", "trial00001");
    }

    @Test
    void inactiveDemoStoreCountsAsInactive() {
        // given
        Store demo = demo("demo000001");
        when(storeActivity.status(demo)).thenReturn(Optional.of(new DeactivationStatus(DeactivationReason.MANUAL, null, null, 0)));

        // when
        StoresListPage page = factory.build(List.of(demo), StoresListQuery.of("inactive", null, null, null, null));

        // then
        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0).state()).isEqualTo(StoreState.SWITCHED_OFF);
        assertThat(page.rows().get(0).noteDate()).isNull();
    }

    @Test
    void searchIgnoresCaseAndPolishLetters() {
        // given
        Store byName = store("aaaa000001", "Sklep Łódź", null);
        Store byCompany = store("bbbb000001", "Shop", null);
        BillingDetails billing = new BillingDetails();
        billing.setCompanyName("Marcin Wójcik");
        billing.setEmail("biuro@wojcik.pl");
        byCompany.setBillingDetails(billing);
        Store byId = store("cccc123456", "Other", null);
        Store byTrialEmail = onTrial("dddd000001", "Trial", 9);

        // when / then
        assertThat(ids(factory.build(List.of(byName, byCompany, byId, byTrialEmail), StoresListQuery.of(null, "LODZ", null, null, null))))
                .containsExactly("aaaa000001");
        assertThat(ids(factory.build(List.of(byName, byCompany, byId, byTrialEmail), StoresListQuery.of(null, "wojcik", null, null, null))))
                .containsExactly("bbbb000001");
        assertThat(ids(factory.build(List.of(byName, byCompany, byId, byTrialEmail), StoresListQuery.of(null, "C123", null, null, null))))
                .containsExactly("cccc123456");
        assertThat(ids(factory.build(List.of(byName, byCompany, byId, byTrialEmail), StoresListQuery.of(null, "owner@dddd", null, null, null))))
                .containsExactly("dddd000001");
    }

    @Test
    void sortsByCreationNewestFirstWithMissingDatesLastInBothDirections() {
        // given
        Store old = store("old0000001", "B", "2026-01-01T10:00:00Z");
        Store fresh = store("new0000001", "A", "2026-07-01T10:00:00Z");
        Store legacy = store("leg0000001", "C", null);
        Store broken = store("brk0000001", "D", "not-a-date");

        // when
        List<String> desc = ids(factory.build(List.of(old, legacy, fresh, broken), StoresListQuery.defaults()));
        List<String> asc = ids(factory.build(List.of(old, legacy, fresh, broken), StoresListQuery.of(null, null, null, "asc", null)));

        // then
        assertThat(desc.subList(0, 2)).containsExactly("new0000001", "old0000001");
        assertThat(asc.subList(0, 2)).containsExactly("old0000001", "new0000001");
        assertThat(desc.subList(2, 4)).containsExactlyInAnyOrder("leg0000001", "brk0000001");
        assertThat(asc.subList(2, 4)).containsExactlyInAnyOrder("leg0000001", "brk0000001");
    }

    @Test
    void sortsByNameWithPolishCollation() {
        // given
        Store z = store("z000000001", "Zebra", null);
        Store l = store("l000000001", "Łoś", null);
        Store a = store("a000000001", "audio", null);

        // when
        List<String> asc = ids(factory.build(List.of(z, l, a), StoresListQuery.of(null, null, "name", null, null)));

        // then
        assertThat(asc).containsExactly("a000000001", "l000000001", "z000000001");
    }

    @Test
    void ownerPrefersCompanyAndTrialOwnerEmail() {
        // given
        Store trial = onTrial("trial00001", "T", 9);
        BillingDetails billing = new BillingDetails();
        billing.setCompanyName("Firma sp. z o.o.");
        billing.setEmail("faktury@firma.pl");
        trial.setBillingDetails(billing);
        Store emailOnly = store("mail000001", "M", null);
        BillingDetails mailBilling = new BillingDetails();
        mailBilling.setEmail("kontakt@mail.pl");
        emailOnly.setBillingDetails(mailBilling);
        Store nobody = store("none000001", "N", null);

        // when
        List<StoreRow> rows = factory.build(List.of(trial, emailOnly, nobody), StoresListQuery.defaults()).rows();

        // then
        assertThat(row(rows, "trial00001").ownerName()).isEqualTo("Firma sp. z o.o.");
        assertThat(row(rows, "trial00001").ownerEmail()).isEqualTo("owner@trial00001.pl");
        assertThat(row(rows, "mail000001").ownerName()).isEqualTo("kontakt@mail.pl");
        assertThat(row(rows, "mail000001").ownerEmail()).isNull();
        assertThat(row(rows, "none000001").ownerName()).isNull();
    }

    @Test
    void rowFallbacksForMissingData() {
        // given
        Store bare = store("bare0000001", null, null);
        Store badTrial = store("badt000001", "Bad trial", "2026-01-01T10:00:00Z");
        badTrial.setTrial(new TrialPeriod("x@y.pl", "garbage", "garbage"));

        // when
        List<StoreRow> rows = factory.build(List.of(bare, badTrial), StoresListQuery.defaults()).rows();

        // then
        StoreRow bareRow = row(rows, "bare0000001");
        assertThat(bareRow.name()).isEqualTo("bare0000");
        assertThat(bareRow.shortId()).isEqualTo("bare0000");
        assertThat(bareRow.created()).isNull();
        StoreRow badRow = row(rows, "badt000001");
        assertThat(badRow.state()).isEqualTo(StoreState.TRIAL);
        assertThat(badRow.noteDate()).isNull();
        assertThat(badRow.created()).isEqualTo("01.01.2026");
    }

    @Test
    void createdDateUsesWarsawDay() {
        // given
        Store lateEvening = store("late000001", "Late", "2026-10-12T22:30:00Z");

        // when
        StoreRow row = factory.build(List.of(lateEvening), StoresListQuery.defaults()).rows().get(0);

        // then
        assertThat(row.created()).isEqualTo("13.10.2026");
    }

    @Test
    void deleteDialogUsesTheRowOwnerEmailChainAndShortIdForNamelessStore() {
        // given
        Store nameless = store("nameless001", " ", "2026-10-01T10:00:00Z");
        nameless.setTrial(new TrialPeriod(" ", "2026-10-01T10:00:00Z", "2026-10-15T10:00:00Z"));
        BillingDetails billing = new BillingDetails();
        billing.setEmail("faktury@firma.pl");
        nameless.setBillingDetails(billing);

        // when
        StoresListPage page = factory.build(List.of(nameless), StoresListQuery.of(null, null, null, null, "nameless001"));

        // then
        assertThat(page.deleteDialog().ownerEmail()).isEqualTo("faktury@firma.pl");
        assertThat(page.deleteDialog().name()).isEqualTo("nameless");
        assertThat(StoresListPageFactory.displayName(nameless)).isEqualTo("nameless");
    }

    @Test
    void deleteDialogOfFullStoreWithoutAnyEmailHasNoOwnerEmail() {
        // given
        Store full = store("full0000001", "Full", null);

        // when
        StoresListPage page = factory.build(List.of(full), StoresListQuery.of(null, null, null, null, "full0000001"));

        // then
        assertThat(page.deleteDialog().ownerEmail()).isNull();
    }

    @Test
    void deleteDialogOnlyForExistingNonDemoStore() {
        // given
        Store trial = onTrial("trial00001", "Trial shop", 9);
        Store demo = demo("demo000001");
        List<Store> stores = List.of(trial, demo);

        // when
        StoresListPage forTrial = factory.build(stores, StoresListQuery.of("full", null, null, null, "trial00001"));
        StoresListPage forDemo = factory.build(stores, StoresListQuery.of(null, null, null, null, "demo000001"));
        StoresListPage forMissing = factory.build(stores, StoresListQuery.of(null, null, null, null, "nope"));

        // then
        assertThat(forTrial.deleteDialog()).isEqualTo(new StoresListPage.DeleteDialog("trial00001", "Trial shop", true,
                "owner@trial00001.pl", "/dashboard/store/trial00001/delete"));
        assertThat(forTrial.back()).isEqualTo("/dashboard/stores?status=full");
        assertThat(forDemo.deleteDialog()).isNull();
        assertThat(forMissing.deleteDialog()).isNull();
        assertThat(row(forDemo.rows(), "trial00001").deleteDialogHref()).isEqualTo("/dashboard/stores?delete=trial00001");
    }

    @Test
    void emptyStatesAndHiddenDemoSegment() {
        // given
        Store full = store("full000001", "Alfa", null);

        // when
        StoresListPage noMatch = factory.build(List.of(full), StoresListQuery.of("trial", "zabawki", null, null, null));
        StoresListPage emptySegment = factory.build(List.of(full), StoresListQuery.of("trial", null, null, null, null));
        StoresListPage demoAsked = factory.build(List.of(full), StoresListQuery.of("demo", null, null, null, null));
        StoresListPage none = factory.build(List.of(), StoresListQuery.defaults());
        StoresListPage some = factory.build(List.of(full), StoresListQuery.defaults());

        // then
        assertThat(noMatch.emptyState()).isEqualTo(new StoresListPage.EmptyState("stores.list.empty.search", "zabawki",
                "stores.list.empty.showAll", "/dashboard/stores"));
        assertThat(emptySegment.emptyState()).isEqualTo(new StoresListPage.EmptyState("stores.list.empty.segment", null,
                "stores.list.empty.showAll", "/dashboard/stores"));
        assertThat(none.emptyState()).isEqualTo(new StoresListPage.EmptyState("stores.list.empty.none", null,
                "stores.list.create", "/dashboard/store/create"));
        assertThat(some.emptyState()).isNull();
        assertThat(some.segments()).extracting(StoresListPage.SegmentLink::labelKey).doesNotContain("stores.list.segment.demo");
        assertThat(demoAsked.segments()).extracting(StoresListPage.SegmentLink::labelKey).contains("stores.list.segment.demo");
    }

    @Test
    void demoSegmentShownWhenDemoStoresExist() {
        // when
        StoresListPage page = factory.build(List.of(demo("demo000001")), StoresListQuery.defaults());

        // then
        assertThat(page.segments()).extracting(StoresListPage.SegmentLink::labelKey).last().isEqualTo("stores.list.segment.demo");
    }

    private static StoreRow row(List<StoreRow> rows, String id) {
        return rows.stream().filter(r -> r.storeId().equals(id)).findFirst().orElseThrow();
    }

    private static List<String> ids(StoresListPage page) {
        return page.rows().stream().map(StoreRow::storeId).toList();
    }
}
