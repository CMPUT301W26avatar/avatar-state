package com.example.lotteryapp.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;

import java.util.List;
import java.util.Objects;

/**
 * Fragment that displays a filterable list of entrants for a given event.
 * Supports four views — invited (pending), declined, cancelled, and accepted — via
 * the {@code type} argument. Used as a tab page inside {@link com.example.lotteryapp.activities.InvitesDashboardActivity}.
 * Organizers can cancel a pending invitation directly from the invited tab.
 */
public class InviteListFragment extends Fragment {

    public static final String ARG_EVENT_ID = "eventId";
    public static final String ARG_TYPE = "type";

    public static final String TYPE_INVITED = "invited"; // lottery selected -> invited to enroll
    public static final String TYPE_DECLINED = "declined"; // user declined
    public static final String TYPE_ACCEPTED = "accepted"; // user accepted -> enrolled
    public static final String TYPE_CANCELLED = "cancelled"; // organizer cancelled

    private String eventId;
    private String type;

    private LinearLayout container;
    private TextView tvEmpty;

    /**
     * creates a new fragment instance configured with an event id and list type
     *      list types are: INVITED, DECLINED, ACCEPTED, CANCELLED
     */
    public static InviteListFragment newInstance(String eventId, String type) {
        InviteListFragment fragment = new InviteListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * initializes fragment state by reading arguments for event id and type
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId = getArguments().getString(ARG_EVENT_ID);
            type = getArguments().getString(ARG_TYPE);
        }
    }

    /**
     * inflates the invite list layout for this fragment
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_invite_list, parent, false);
    }

    /**
     * binds views and triggers loading of entrants after view creation
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        container = view.findViewById(R.id.list_container);
        tvEmpty = view.findViewById(R.id.tv_empty_state);
        loadEntrants();
    }

    /**
     * loads entrants from firebase storage based on the selected invitation type
     */
    private void loadEntrants() {
        EventPoolStorage storage = ServiceLocator.getEventPoolStorage();

        if (TYPE_INVITED.equals(type)) {
            storage.getInvitedEntrants(
                    eventId,
                    this::renderEntrants,
                    e -> handleError("Failed to load invited entrants", e)
            );
        } else if (TYPE_DECLINED.equals(type)) {
            storage.getDeclinedEntrants(
                    eventId,
                    this::renderEntrants,
                    e -> handleError("Failed to load declined entrants", e)
            );
        } else if (TYPE_CANCELLED.equals(type)) {
            storage.getCancelledEntrants(
                    eventId,
                    this::renderEntrants,
                    e -> handleError("Failed to load cancelled entrants", e)
            );
        } else {
            storage.getEnrolledEntrants(
                    eventId,
                    this::renderEntrants,
                    e -> handleError("Failed to load accepted entrants", e)
            );
        }
    }

    /**
     * renders entrant rows into the container and handles empty state display
     */
    private void renderEntrants(List<Entrant> entrants) {
        // fragment may be detached during async callback
        if (getContext() == null) return;

        container.removeAllViews(); // clear container before populating to remove stale loads

        // show empty state if there are no entrants
        if (entrants == null || entrants.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        // else, we have data to display so we hide the empty textView
        tvEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        EventPoolStorage storage = ServiceLocator.getEventPoolStorage();

        for (Entrant entrant : entrants) {
            // inflate an item View for each entrant
            View row = inflater.inflate(R.layout.item_entrant, container, false);

            // entrant details
            TextView entrantName = row.findViewById(R.id.tv_entrant_name);
            TextView entrantEmail = row.findViewById(R.id.tv_entrant_email);

            // bind cancel button
            View btnCancel = row.findViewById(R.id.btn_cancel_invite);

            // fill entrant details
            ServiceLocator.getUserStorage().getUserProfile(
                    entrant.getEntrantId(),
                    user -> {
                        String name = user.getName();
                        String email = user.getEmail();

                        entrantName.setText(Objects.requireNonNullElse(name, "UNKNOWN NAME"));
                        entrantEmail.setText(Objects.requireNonNullElse(email, "UNKNOWN EMAIL"));
                    },
                    e -> {
                        entrantName.setText("UNKNOWN NAME");
                        entrantEmail.setText("UNKNOWN EMAIL");
                    }
            );

            // enforce cancelling only for pending Invites (INVITED)
            if (TYPE_INVITED.equals(type)) {
                btnCancel.setVisibility(View.VISIBLE);

                btnCancel.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Cancel invitation")
                        .setMessage("Remove this entrant from invited list?")
                        .setNegativeButton("No", null)
                        .setPositiveButton("Yes", (dialog, which) -> {

                            btnCancel.setEnabled(false);

                            storage.cancelInvitation(
                                    eventId,
                                    entrant.getEntrantId(),
                                    unused -> {
                                        Toast.makeText(requireContext(), "Invitation cancelled", Toast.LENGTH_SHORT).show();

                                        if (requireActivity() instanceof com.example.lotteryapp.activities.InvitesDashboardActivity) {
                                            ((com.example.lotteryapp.activities.InvitesDashboardActivity) requireActivity()).refreshDashboard();
                                        }
                                    },
                                    e -> {
                                        btnCancel.setEnabled(true);
                                        Toast.makeText(requireContext(), "Failed to cancel invitation", Toast.LENGTH_SHORT).show();
                                    }
                            );
                        })
                        .show());

            } else {
                // hide button for accepted / declined tabs
                btnCancel.setVisibility(View.GONE);
            }

            container.addView(row);
        }
    }

    /**
     * logs an error and refreshes the screen to show the container as empty
     */
    private void handleError(String message, Exception e) {
        Log.e("InviteListFragment", message, e);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(message);
        }
    }
}