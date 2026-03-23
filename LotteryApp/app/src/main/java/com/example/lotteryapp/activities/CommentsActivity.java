package com.example.lotteryapp.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ProfanityFilter;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        rvComments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommentAdapter(new ArrayList<>());
        rvComments.setAdapter(adapter);

        btnPost.setOnClickListener(v -> submitComment());

        loadComments();
    }

    /**
     * Loads the comments for the current event from storage and updates the UI.
     */
    private void loadComments() {
        eventStorage.getEventComments(
                eventId,
                comments -> {
                    adapter.setComments(comments);
                    if (!comments.isEmpty()) {
                        // This scrolls to the top of the comments list
                        rvComments.scrollToPosition(0);
                    }
                },
                e -> {
                    Toast.makeText(this, "Failed to load comments", Toast.LENGTH_SHORT).show();
                    Log.e("CommentsActivity", "Error loading comments", e);
                }
        );
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
            Long createdAt = (Long) comment.get("createdAtMs");

            holder.tvMessage.setText(message);
            holder.tvAuthor.setText(authorName);
            holder.tvTime.setText(EventDetailsActivity.getTimeAgo(createdAt != null ? createdAt : 0));
        }

        @Override
        public int getItemCount() {
            return comments.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage, tvAuthor, tvTime;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.line1);
                tvAuthor = itemView.findViewById(R.id.line2_left);
                tvTime = itemView.findViewById(R.id.line2_right);
            }
        }
    }
}