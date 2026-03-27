package com.example.lotteryapp.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ProfanityFilter;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Activity to display and post comments on an event.
 */
public class CommentsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "eventId";

    private String eventId;
    private String currentUserId;
    private EventStorage eventStorage;
    private UserStorage userStorage;

    private RecyclerView rvComments;
    private CommentAdapter adapter;
    private EditText editComment;
    private Button btnPost;
    private ImageButton btnViewReports;
    private Event currentEvent;
    private boolean isUserAdmin = false;
    private boolean isShowingReports = false;

    private List<Map<String, Object>> allComments = new ArrayList<>();
    private Set<String> reportedCommentIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comments);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        currentUserId = ServiceLocator.uid();
        eventStorage = ServiceLocator.getEventStorage();
        userStorage = ServiceLocator.getUserStorage();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvComments = findViewById(R.id.rv_comments);
        editComment = findViewById(R.id.edit_comment);
        btnPost = findViewById(R.id.btn_post_comment);
        btnViewReports = findViewById(R.id.btn_view_reports);

        rvComments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommentAdapter(new ArrayList<>());
        rvComments.setAdapter(adapter);

        btnPost.setOnClickListener(v -> submitComment());
        btnViewReports.setOnClickListener(v -> toggleReportsView());

        checkAdminStatus();
        loadEventAndComments();
    }

    private void checkAdminStatus() {
        if (currentUserId == null) return;
        ServiceLocator.getAdminStorage().isAdmin(currentUserId,
                isAdmin -> {
                    isUserAdmin = isAdmin;
                    updateReportsButtonVisibility();
                },
                e -> Log.e("CommentsActivity", "Failed to check admin status", e)
        );
    }

    private void updateReportsButtonVisibility() {
        boolean isOrganizer = currentEvent != null && currentUserId != null && currentUserId.equals(currentEvent.getOrganizerId());
        if (isUserAdmin || isOrganizer) {
            btnViewReports.setVisibility(View.VISIBLE);
        }
        else {
            btnViewReports.setVisibility(View.GONE);
        }
    }

    private void loadEventAndComments() {
        if (eventId == null) {
            Toast.makeText(this, "Error: Missing event ID", Toast.LENGTH_SHORT).show();
            return;
        }
        eventStorage.getEvent(eventId, event -> {
            this.currentEvent = event;
            updateReportsButtonVisibility();
            loadComments();
        }, e -> {
            Log.e("CommentsActivity", "Failed to load event", e);
            loadComments();
        });
    }

    /**
     * Loads the comments for the current event from storage and updates the UI.
     */
    private void loadComments() {
        eventStorage.getEventComments(
                eventId,
                comments -> {
                    this.allComments = comments;
                    if (isShowingReports) {
                        loadReportsAndFilter();
                    }
                    else {
                        adapter.setComments(comments);
                        if (!comments.isEmpty()) {
                            rvComments.scrollToPosition(0); // Show the newest comment at the top
                        }
                    }
                },
                e -> {
                    Toast.makeText(this, "Failed to load comments", Toast.LENGTH_SHORT).show();
                    Log.e("CommentsActivity", "Error loading comments", e);
                }
        );
    }

    /**
     * Loads the reports for the current event from storage and filters the comments to show only the reported ones.
     */
    private void loadReportsAndFilter() {
        eventStorage.getEventReportedComments(eventId,
                reports -> {
                    reportedCommentIds.clear();
                    for (Map<String, Object> report : reports) {
                        reportedCommentIds.add((String) report.get("commentId"));
                    }
                    
                    List<Map<String, Object>> reportedComments = new ArrayList<>();
                    for (Map<String, Object> comment : allComments) {
                        if (reportedCommentIds.contains((String) comment.get("commentId"))) {
                            reportedComments.add(comment);
                        }
                    }
                    
                    adapter.setComments(reportedComments);
                    if (reportedComments.isEmpty()) {
                        Toast.makeText(this, "No reported comments found", Toast.LENGTH_SHORT).show();
                    }
                },
                e -> Log.e("CommentsActivity", "Failed to load reports", e)
        );
    }

    /**
     * Toggles the visibility of the reports view.
     */
    private void toggleReportsView() {
        isShowingReports = !isShowingReports;
        if (isShowingReports) {
            btnViewReports.setImageResource(R.drawable.ic_close); // Reuse close icon or toggle state
            getSupportActionBar().setTitle("Reported Comments");
            loadReportsAndFilter();
        }
        else {
            btnViewReports.setImageResource(R.drawable.ic_flag);
            getSupportActionBar().setTitle("Comments");
            adapter.setComments(allComments);
        }
    }

    /**
     * Submits a new comment to the event.
     */
    private void submitComment() {
        String message = editComment.getText().toString().trim();
        if (message.isEmpty()) return;

        if (ProfanityFilter.containsProfanity(message)) {
            editComment.setError("Inappropriate language is not allowed");
            return;
        }

        btnPost.setEnabled(false);
        userStorage.getUserProfile(
                currentUserId,
                user -> {
                    String authorName = user.getName() != null ? user.getName() : "Unknown User";
                    eventStorage.addEventComment(
                            eventId,
                            currentUserId,
                            authorName,
                            message,
                            unused -> {
                                editComment.setText("");
                                btnPost.setEnabled(true);
                                loadComments();
                                Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show();
                            },
                            e -> {
                                btnPost.setEnabled(true);
                                Toast.makeText(this, "Failed to post comment", Toast.LENGTH_SHORT).show();
                            }
                    );
                },
                e -> {
                    btnPost.setEnabled(true);
                    Toast.makeText(this, "Failed to load user profile", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void showCommentOptions(Map<String, Object> comment) {
        String commentId = (String) comment.get("commentId");
        
        boolean isOrganizer = currentEvent != null && currentUserId != null && currentUserId.equals(currentEvent.getOrganizerId());
        boolean canDelete = isUserAdmin || isOrganizer;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Comment Options");

        List<String> options = new ArrayList<>();
        options.add("Report Comment");
        if (canDelete) {
            options.add("Delete Comment");
        }

        builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
            String selected = options.get(which);
            if (selected.equals("Report Comment")) {
                reportComment(commentId);
            }
            else if (selected.equals("Delete Comment")) {
                deleteComment(commentId);
            }
        });
        builder.show();
    }

    private void reportComment(String commentId) {
        if (eventId == null || commentId == null || currentUserId == null) {
            Toast.makeText(this, "Failed to report: Missing IDs", Toast.LENGTH_SHORT).show();
            return;
        }
        eventStorage.reportComment(eventId, commentId, currentUserId,
                unused -> Toast.makeText(this, "Comment reported", Toast.LENGTH_SHORT).show(),
                e -> {
                    Log.e("CommentsActivity", "Report failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Failed to report comment: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void deleteComment(String commentId) {
        eventStorage.deleteEventComment(eventId, commentId,
                unused -> {
                    Toast.makeText(this, "Comment deleted", Toast.LENGTH_SHORT).show();
                    loadComments();
                },
                e -> {
                    Log.e("CommentsActivity", "Delete failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Failed to delete comment", Toast.LENGTH_SHORT).show();
                }
        );
    }

    /**
     * Adapter for the comments RecyclerView.
     */
    private class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {
        private List<Map<String, Object>> comments;

        public CommentAdapter(List<Map<String, Object>> comments) {
            this.comments = comments;
        }

        public void setComments(List<Map<String, Object>> comments) {
            this.comments = comments;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> comment = comments.get(position);
            String message = (String) comment.get("message");
            String authorName = (String) comment.get("authorName");
            String uid = (String) comment.get("uid");
            Long createdAt = (Long) comment.get("createdAtMs");

            holder.tvMessage.setText(message);
            holder.tvAuthor.setText(authorName);
            holder.tvTime.setText(EventDetailsActivity.getTimeAgo(createdAt != null ? createdAt : 0));
            
            if (currentEvent != null && uid != null && uid.equals(currentEvent.getOrganizerId())) {
                holder.tvOrganizerBadge.setVisibility(View.VISIBLE);
            }
            else {
                holder.tvOrganizerBadge.setVisibility(View.GONE);
            }

            holder.itemView.setOnLongClickListener(v -> {
                showCommentOptions(comment);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return comments.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage, tvAuthor, tvTime, tvOrganizerBadge;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.line1);
                tvAuthor = itemView.findViewById(R.id.line2_left);
                tvTime = itemView.findViewById(R.id.line2_right);
                tvOrganizerBadge = itemView.findViewById(R.id.tv_organizer_badge);
            }
        }
    }
}