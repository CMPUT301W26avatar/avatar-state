package com.example.lotteryapp.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;

import java.util.List;
import java.util.Objects;

public class InviteListFragment extends Fragment {

    public static final String ARG_EVENT_ID = "eventId";
    public static final String ARG_TYPE = "type";

    public static final String TYPE_INVITED = "invited";
    public static final String TYPE_DECLINED = "declined";
    public static final String TYPE_ACCEPTED = "accepted";

    private String eventId;
    private String type;

    private LinearLayout container;
    private TextView tvEmpty;

    public static InviteListFragment newInstance(String eventId, String type) {
        InviteListFragment fragment = new InviteListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId = getArguments().getString(ARG_EVENT_ID);
            type = getArguments().getString(ARG_TYPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_invite_list, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        container = view.findViewById(R.id.list_container);
        tvEmpty = view.findViewById(R.id.tv_empty_state);
        loadEntrants();
    }

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
        } else {
            storage.getEnrolledEntrants(
                    eventId,
                    this::renderEntrants,
                    e -> handleError("Failed to load accepted entrants", e)
            );
        }
    }

    private void renderEntrants(List<Entrant> entrants) {
        if (getContext() == null) return;

        container.removeAllViews();

        if (entrants == null || entrants.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (Entrant entrant : entrants) {
            View row = inflater.inflate(R.layout.item_entrant, container, false);

            TextView entrantName = row.findViewById(R.id.tv_entrant_name);
            TextView entrantEmail = row.findViewById(R.id.tv_entrant_email);

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

            container.addView(row);
        }
    }

    private void handleError(String message, Exception e) {
        Log.e("InviteListFragment", message, e);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(message);
        }
    }
}