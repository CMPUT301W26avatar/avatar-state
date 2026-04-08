package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

/**
 * Full-screen Terms of Service activity shown to users until they acknowledge the app policy
 *      Accept -> Boolean for has accepted stored in firebase
 *      Decline -> Sent back to LoginActivity
 *          accept is only shown when the user scrolls all the way to the bottom of the scroll view.
 */
public class TermsOfServiceActivity extends AppCompatActivity {

    public static final String EXTRA_READ_ONLY = "extra_read_only";

    private UserStorage ustore;
    private MaterialToolbar toolbar;
    private MaterialTextView tvTermsBody;
    private MaterialButton btnDecline;
    private MaterialButton btnAccept;
    private boolean isReadOnly = false;

    /**
     * Intitializes UI components for this activity.
     *      sets click listeners for accept and decline buttons
     *      sets scroll position listeners for enabling the accept button
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_of_service);
        NestedScrollView scrollView = findViewById(R.id.scroll_terms);

        isReadOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);

        ustore = ServiceLocator.getUserStorage();

        toolbar = findViewById(R.id.toolbar_tos);
        tvTermsBody = findViewById(R.id.tv_terms_body);
        btnDecline = findViewById(R.id.btn_decline_tos);
        btnAccept = findViewById(R.id.btn_accept_tos);

        if (isReadOnly) {
            btnAccept.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            toolbar.setNavigationOnClickListener(v -> finish());
        } else {
            btnAccept.setEnabled(false);
            btnAccept.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();

            toolbar.setNavigationOnClickListener(v -> {
                // Do nothing so users must explicitly accept or decline.
            });

            scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
                View content = scrollView.getChildAt(0);
                if (content == null) return;

                int diff = content.getBottom() - (scrollView.getHeight() + scrollView.getScrollY());

                // enable the accept button only if the user has reached the bottom of the scroll View
                if (diff <= 0) {
                    // Reached bottom
                    if (!btnAccept.isEnabled()) {
                        btnAccept.setEnabled(true);
                        btnAccept.setAlpha(1f);
                    }
                }
            });

            btnDecline.setOnClickListener(v -> {
                Toast.makeText(this, "You must accept the Terms of Service to continue.", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });

            btnAccept.setOnClickListener(v -> acceptTermsOfService());
        }

        tvTermsBody.setText(buildTermsOfServiceText());
    }

    /**
     * Verify valid user, and if so, store the TOS acceptance boolean in firebase for the user
     */
    private void acceptTermsOfService() {
        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(this, "Unable to verify user account.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnAccept.setEnabled(false);
        btnDecline.setEnabled(false);

        ustore.setHasAcceptedTOS(
                uid,
                true,
                unused -> {
                    setResult(RESULT_OK);
                    finish();
                },
                e -> {
                    btnAccept.setEnabled(true);
                    btnDecline.setEnabled(true);
                    Toast.makeText(this, "Failed to save Terms of Service acceptance.", Toast.LENGTH_SHORT).show();
                }
        );
    }

    /**
     * Helper for building the big long terms of service text to show inside of the scroll view
     */
    private String buildTermsOfServiceText() {
        return "Terms of Service\n\n" +

                "1. Acceptance of Terms\n" +
                "By using this application, you agree to these Terms of Service. " +
                "If you do not agree, you must not use the app.\n\n" +

                "2. Platform Overview\n" +
                "This app allows users to discover and join events, participate in waitlists " +
                "and lottery-based selections, and interact with organizers. Organizers may " +
                "create and manage in-person or online events.\n\n" +

                "3. User Responsibilities\n" +
                "Users agree to provide accurate information, use the app lawfully, avoid " +
                "creating multiple accounts for unfair advantage, and not manipulate or abuse " +
                "the lottery or waitlist system.\n\n" +

                "4. Organizer Responsibilities\n" +
                "Organizers agree to provide accurate event details, conduct lotteries fairly, " +
                "and honor selected entrants. Organizers are solely responsible for event " +
                "execution, safety, delivery, and participant communication.\n\n" +

                "5. Lottery and Waitlist Rules\n" +
                "Joining a waitlist does not guarantee selection. Entrants may be selected " +
                "randomly or according to defined event rules. Organizers may cancel events " +
                "or redraw selections if needed.\n\n" +

                "6. Payments and Transactions\n" +
                "Unless explicitly stated otherwise, the app does not process or guarantee " +
                "payments. Any financial transactions occurring outside the platform are at " +
                "the user's own risk.\n\n" +

                "7. Community Conduct and Content Policy\n" +
                "Users must maintain respectful and appropriate behavior at all times. " +
                "The following are strictly prohibited in comments, event descriptions, " +
                "usernames, messages, or any user-generated content:\n\n" +
                "• Hate speech, slurs, or discriminatory language\n" +
                "• Harassment, threats, or abusive behavior\n" +
                "• Offensive, obscene, or sexually explicit content\n" +
                "• Content promoting violence or illegal activity\n" +
                "• Spam, impersonation, or misleading information\n\n" +
                "The platform enforces a zero-tolerance policy for hate speech and slurs. " +
                "The platform may remove content, restrict features, suspend users, or " +
                "permanently ban accounts for violations.\n\n" +

                "8. Gambling and Raffle Notice\n" +
                "This platform is not a gambling service. The app does not guarantee compliance " +
                "with gambling, raffle, sweepstakes, or prize-draw laws. Organizers are solely " +
                "responsible for ensuring their events comply with all applicable laws and " +
                "regulations in their jurisdiction. The platform is not liable for unlawful " +
                "or non-compliant organizer activity.\n\n" +

                "9. Liability Disclaimer\n" +
                "The platform is not responsible for event cancellations, delays, organizer " +
                "conduct, participant behavior, injuries, damages, losses, disputes, or the " +
                "outcome of lotteries or raffles. Participation is at the user's own risk.\n\n" +
                "10. Account and Access\n" +
                "The platform may suspend or terminate accounts, remove content, or restrict " +
                "access at any time for misuse or violation of these Terms.\n\n" +

                "11. Privacy Notice\n" +
                "Basic user data is stored to operate the platform and improve functionality. " +
                "By using the app, you acknowledge that such data may be stored and processed " +
                "for platform operation.\n\n" +

                "12. Changes to Terms\n" +
                "These Terms may be updated at any time. Continued use of the app after " +
                "updates constitutes acceptance of the revised Terms.\n\n" +

                "13. Agreement\n" +
                "By clicking Accept, you acknowledge that you have read and agree to these Terms of Service.";
    }
}