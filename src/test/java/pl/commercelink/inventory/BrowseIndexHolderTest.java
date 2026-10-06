package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrowseIndexHolderTest {

    private final GlobalMatchedInventory global = mock(GlobalMatchedInventory.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor manual = queued::add;

    @Test
    void firstCallBuildsSynchronously() {
        // given
        when(global.version()).thenReturn(3L);
        when(global.all()).thenReturn(List.of());
        BrowseIndexHolder holder = new BrowseIndexHolder(global, manual);

        // when
        BrowseIndex index = holder.current();

        // then
        assertThat(index.version()).isEqualTo(3);
        assertThat(queued).isEmpty();
    }

    @Test
    void newVersionKeepsServingTheOldIndexUntilTheBackgroundRebuildFinishes() {
        // given
        when(global.version()).thenReturn(3L);
        when(global.all()).thenReturn(List.of());
        BrowseIndexHolder holder = new BrowseIndexHolder(global, manual);
        holder.current();
        when(global.version()).thenReturn(4L);

        // when
        BrowseIndex during = holder.current();
        BrowseIndex again = holder.current();
        queued.forEach(Runnable::run);
        BrowseIndex after = holder.current();

        // then
        assertThat(during.version()).isEqualTo(3);
        assertThat(again.version()).isEqualTo(3);
        assertThat(queued).hasSize(1);
        assertThat(after.version()).isEqualTo(4);
    }
}
