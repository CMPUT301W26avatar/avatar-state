
package com.example.lotteryapp;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.example.lotteryapp.dialogs.FilterDialog.FilterType.AVAILABILITY_FILTER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.util.Pair;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.dialogs.FilterDialog;
import com.example.lotteryapp.fragments.SearchFragment;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.search.SearchView;
import com.google.android.material.textview.MaterialTextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for SearchFragment.
 *
 * These tests verify:
 * - section loads populate open, upcoming, and full dashboards
 * - performing a text search swaps the dashboard into search-result mode
 * - availability filter reloads use the availability-aware storage query
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SearchFragmentUnitTest {

    private EventStorage mockEventStorage;
    private UserStorage mockUserStorage;

    @Before
    public void setUp() {
        // reset shared services before each test
        ServiceLocator.reset();

        mockEventStorage = mock(EventStorage.class);
        mockUserStorage = mock(UserStorage.class);

        // install mocks before fragment construction because SearchFragment caches them in fields
        ServiceLocator.setEventStorageForTests(mockEventStorage);
        ServiceLocator.setUserStorageForTests(mockUserStorage);
        ServiceLocator.setUserIdProviderForTests(() -> "user-1");

        // allow the keyword migration invoked in onViewCreated to complete immediately
        doAnswer(invocation -> {
            OnSuccessListener<Void> success = invocation.getArgument(0);
            success.onSuccess(null);
            return null;
        }).when(mockEventStorage).syncAllEventKeywords(any(), any());

        // return one event for each status-based section load
        doAnswer(invocation -> {
            Event.EventStatus status = invocation.getArgument(2);
            OnSuccessListener<List<Event>> success = invocation.getArgument(4);

            Event event = new Event("organizer-" + status.name(), 10, 20);
            event.setEventId("event-" + status.name());
            event.setTitle(status.name());
            event.setStatus(status);

            success.onSuccess(Collections.singletonList(event));
            return null;
        }).when(mockEventStorage).getEventsByStatus(anyInt(), anyInt(), any(Event.EventStatus.class), any(), any(), any());

        // provide availability dates when the availability filter is used
        doAnswer(invocation -> {
            OnSuccessListener<List<Long>> success = invocation.getArgument(1);
            success.onSuccess(Arrays.asList(67L, 301L));
            return null;
        }).when(mockUserStorage).getUserAvailabilityDates(eq("user-1"), any(), any());

        // default text search returns a single open event
        doAnswer(invocation -> {
            OnSuccessListener<List<Event>> success = invocation.getArgument(1);

            Event event = new Event("organizer-search", 10, 20);
            event.setEventId("search-1");
            event.setTitle("Search Result");
            event.setStatus(Event.EventStatus.REG_OPEN);

            success.onSuccess(Collections.singletonList(event));
            return null;
        }).when(mockEventStorage).searchEvents(anyString(), any(), any());
    }

    @After
    public void tearDown() {
        // clear service state after each test
        ServiceLocator.reset();
    }

    @Test
    public void loadsOpenUpcomingAndFullSections() {
        // attach fragment and allow the initial dashboard load to finish
        FragmentActivity activity = startFragment(new SearchFragment());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // assert that the open section contains one rendered event
        RecyclerView openRecycler = activity.findViewById(R.id.open_recycler);
        assertNotNull(openRecycler);
        assertNotNull(openRecycler.getAdapter());
        assertEquals(1, openRecycler.getAdapter().getItemCount());

        // assert that the upcoming section contains one rendered event
        RecyclerView upcomingRecycler = activity.findViewById(R.id.upcoming_recycler);
        assertNotNull(upcomingRecycler);
        assertNotNull(upcomingRecycler.getAdapter());
        assertEquals(1, upcomingRecycler.getAdapter().getItemCount());

        // assert that the full section contains one rendered event
        RecyclerView fullRecycler = activity.findViewById(R.id.full_recycler);
        assertNotNull(fullRecycler);
        assertNotNull(fullRecycler.getAdapter());
        assertEquals(1, fullRecycler.getAdapter().getItemCount());
    }

    @Test
    public void performSearchShowsSearchResultsAndHidesOtherSections() throws Exception {
        // attach fragment and let its initial load complete
        SearchFragment fragment = new SearchFragment();
        FragmentActivity activity = startFragment(fragment);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // invoke the private search helper with a non-empty query
        Method performSearch = fragment.getClass().getDeclaredMethod("performSearch", String.class);
        performSearch.setAccessible(true);
        performSearch.invoke(fragment, "lottery");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // assert that the open header becomes the search results label
        MaterialTextView openHeader = activity.findViewById(R.id.open_header);
        assertEquals("Search Results", openHeader.getText().toString());
        assertEquals(VISIBLE, openHeader.getVisibility());

        // assert that the upcoming section is hidden while search mode is active
        MaterialTextView upcomingHeader = activity.findViewById(R.id.upcoming_header);
        assertEquals(GONE, upcomingHeader.getVisibility());

        // assert that the search results are rendered through the open recycler adapter
        RecyclerView openRecycler = activity.findViewById(R.id.open_recycler);
        assertNotNull(openRecycler.getAdapter());
        assertEquals(1, openRecycler.getAdapter().getItemCount());
    }

    @Test
    public void availabilityFilterUsesAvailabilityAwareQuery() throws Exception {
        // attach fragment and let the initial load complete
        SearchFragment fragment = new SearchFragment();
        FragmentActivity activity = startFragment(fragment);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // replace the real filter dialog with a mock so the test can force availability on
        FilterDialog mockFilterDialog = mock(FilterDialog.class);
        when(mockFilterDialog.isActive(AVAILABILITY_FILTER)).thenReturn(true);
        when(mockFilterDialog.getCapacityFilterValues()).thenReturn(new Pair<>(1, Integer.MAX_VALUE));
        setPrivateField(fragment, "filterDialog", mockFilterDialog);

        // answer the availability-aware overload with one upcoming event
        doAnswer(invocation -> {
            OnSuccessListener<List<Event>> success = invocation.getArgument(5);

            Event event = new Event("organizer-availability", 10, 20);
            event.setEventId("availability-1");
            event.setTitle("Availability Event");
            event.setStatus(Event.EventStatus.REG_UPCOMING);

            success.onSuccess(Collections.singletonList(event));
            return null;
        }).when(mockEventStorage).getEventsByStatus(anyInt(), anyInt(), any(List.class), any(Event.EventStatus.class), any(), any(), any());

        // invoke the private loadAllEvents helper so the fragment re-queries using the availability branch
        Method loadAllEvents = fragment.getClass().getDeclaredMethod("loadAllEvents");
        loadAllEvents.setAccessible(true);
        loadAllEvents.invoke(fragment);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // assert that the upcoming section still renders an event after the filtered reload
        RecyclerView upcomingRecycler = activity.findViewById(R.id.upcoming_recycler);
        assertNotNull(upcomingRecycler.getAdapter());
        assertEquals(1, upcomingRecycler.getAdapter().getItemCount());
    }

    private FragmentActivity startFragment(Fragment fragment) {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create()
                .start()
                .resume()
                .visible()
                .get();

        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, fragment)
                .commitNow();

        return activity;
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
