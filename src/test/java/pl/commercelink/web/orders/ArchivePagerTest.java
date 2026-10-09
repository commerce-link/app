package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchivePagerTest {

    @Test
    void firstPageWithMoreHasOnlyNext() {
        // when
        ArchivePager pager = new ArchivePager(0, 25, null, "/next");

        // then
        assertThat(pager.isNeeded()).isTrue();
        assertThat(pager.openEnded()).isTrue();
        assertThat(pager.fromIndex()).isZero();
        assertThat(pager.toIndex()).isEqualTo(25);
        assertThat(pager.totalItems()).isEqualTo(25);
    }

    @Test
    void singlePageNeedsNoPager() {
        // when / then
        assertThat(new ArchivePager(0, 7, null, null).isNeeded()).isFalse();
    }
}
