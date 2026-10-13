package pl.commercelink.web.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoresListQueryTest {

    @Test
    void defaultsAreAllStoresNewestFirst() {
        // when
        StoresListQuery query = StoresListQuery.of(null, null, null, null, null);

        // then
        assertThat(query.segment()).isEqualTo(StoresListQuery.Segment.ALL);
        assertThat(query.sort()).isEqualTo(StoresListQuery.Sort.CREATED);
        assertThat(query.ascending()).isFalse();
        assertThat(query.q()).isNull();
        assertThat(query.delete()).isNull();
        assertThat(query.href()).isEqualTo("/dashboard/stores");
    }

    @Test
    void unknownValuesFallBackToDefaults() {
        // when
        StoresListQuery query = StoresListQuery.of("bogus", "   ", "price", "sideways", " ");

        // then
        assertThat(query).isEqualTo(StoresListQuery.defaults());
    }

    @Test
    void oldAscendingLinkStillSortsByCreationOldestFirst() {
        // when
        StoresListQuery query = StoresListQuery.of(null, null, null, "asc", null);

        // then
        assertThat(query.sort()).isEqualTo(StoresListQuery.Sort.CREATED);
        assertThat(query.ascending()).isTrue();
        assertThat(query.href()).isEqualTo("/dashboard/stores?dir=asc");
    }

    @Test
    void hrefCarriesOnlyNonDefaultParametersEncoded() {
        // when
        StoresListQuery query = StoresListQuery.of("trial", " Łódź & co ", "name", "desc", "abc");

        // then
        assertThat(query.href()).isEqualTo("/dashboard/stores?status=trial&q=%C5%81%C3%B3d%C5%BA+%26+co&sort=name&dir=desc");
        assertThat(query.hrefWithDelete("x 1")).isEqualTo(
                "/dashboard/stores?status=trial&q=%C5%81%C3%B3d%C5%BA+%26+co&sort=name&dir=desc&delete=x+1");
    }

    @Test
    void sortingTheActiveColumnFlipsDirectionAndANewColumnStartsAtItsDefault() {
        // given
        StoresListQuery byCreatedDesc = StoresListQuery.defaults();

        // when
        StoresListQuery flipped = byCreatedDesc.withSort(StoresListQuery.Sort.CREATED);
        StoresListQuery byName = byCreatedDesc.withSort(StoresListQuery.Sort.NAME);

        // then
        assertThat(flipped.ascending()).isTrue();
        assertThat(byName.sort()).isEqualTo(StoresListQuery.Sort.NAME);
        assertThat(byName.ascending()).isTrue();
        assertThat(byName.ariaSort(StoresListQuery.Sort.NAME)).isEqualTo("ascending");
        assertThat(byName.ariaSort(StoresListQuery.Sort.CREATED)).isEqualTo("none");
        assertThat(byCreatedDesc.createdSortHref()).isEqualTo("/dashboard/stores?dir=asc");
        assertThat(byCreatedDesc.nameSortHref()).isEqualTo("/dashboard/stores?sort=name");
        assertThat(byCreatedDesc.createdAriaSort()).isEqualTo("descending");
        assertThat(byCreatedDesc.nameAriaSort()).isEqualTo("none");
    }

    @Test
    void segmentAndSearchLinksDropTheDeleteDialog() {
        // given
        StoresListQuery query = StoresListQuery.of("inactive", "max", null, null, "abc");

        // when / then
        assertThat(query.withSegment(StoresListQuery.Segment.TRIAL).href()).isEqualTo("/dashboard/stores?status=trial&q=max");
        assertThat(query.withQ(null).href()).isEqualTo("/dashboard/stores?status=inactive");
        assertThat(query.cleared().href()).isEqualTo("/dashboard/stores");
    }

    @Test
    void safeBackKeepsListAddresses() {
        // then
        assertThat(StoresListQuery.safeBack("/dashboard/stores")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/stores?status=trial&q=max")).isEqualTo("/dashboard/stores?status=trial&q=max");
    }

    @Test
    void safeBackRejectsForeignAndUnsafeTargets() {
        // then
        assertThat(StoresListQuery.safeBack(null)).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("https://evil.example")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("//evil.example/dashboard/stores")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/orders")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/storesX")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/stores?q=a\r\nSet-Cookie:x")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/stores?q={x}")).isEqualTo("/dashboard/stores");
    }

    @Test
    void safeBackRejectsCharactersTheListNeverSendsUnencoded() {
        // then
        assertThat(StoresListQuery.safeBack("/dashboard/stores?q=a b")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/stores?q=a\tb")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/stores?q=Łódź")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/stores?q=a\u202Eb")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/stores?q=a#frag")).isEqualTo("/dashboard/stores");
        assertThat(StoresListQuery.safeBack("/dashboard/stores?q=%C5%81%C3%B3d%C5%BA+b")).isEqualTo("/dashboard/stores?q=%C5%81%C3%B3d%C5%BA+b");
        assertThat(StoresListQuery.withDeleteParam("/dashboard/stores?q=a#frag", "id1")).isEqualTo("/dashboard/stores?delete=id1");
    }

    @Test
    void withDeleteParamAppendsToTheBackAddress() {
        // then
        assertThat(StoresListQuery.withDeleteParam("/dashboard/stores", "a b")).isEqualTo("/dashboard/stores?delete=a+b");
        assertThat(StoresListQuery.withDeleteParam("/dashboard/stores?status=trial", "id1"))
                .isEqualTo("/dashboard/stores?status=trial&delete=id1");
        assertThat(StoresListQuery.withDeleteParam("https://evil", "id1")).isEqualTo("/dashboard/stores?delete=id1");
    }
}
