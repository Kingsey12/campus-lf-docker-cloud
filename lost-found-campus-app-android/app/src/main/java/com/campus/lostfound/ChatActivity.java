package com.campus.lostfound;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.campus.lostfound.adapter.ChatAdapter;
import com.campus.lostfound.data.StorageManager;
import com.campus.lostfound.model.ChatMessage;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_CONTACT_NAME = "extra_contact_name";
    public static final String EXTRA_ITEM_TYPE = "extra_item_type";

    private StorageManager storageManager;
    private ChatAdapter chatAdapter;
    private String itemId;
    private String contactName;
    private String itemType;

    private ListenerRegistration chatListener;
    private RecyclerView recyclerMessages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_chat);

        storageManager = new StorageManager(this);

        itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        contactName = getIntent().getStringExtra(EXTRA_CONTACT_NAME);
        itemType = getIntent().getStringExtra(EXTRA_ITEM_TYPE);

        if (itemId == null || contactName == null || itemType == null) {
            Toast.makeText(this, R.string.chat_load_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup toolbar back navigation
        MaterialToolbar toolbar = findViewById(R.id.toolbar_chat);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        TextView textTitle = findViewById(R.id.text_chat_title);
        recyclerMessages = findViewById(R.id.recycler_messages);
        EditText inputMessage = findViewById(R.id.input_chat_message);
        Button buttonSend = findViewById(R.id.button_send);
        View messageInputBar = findViewById(R.id.message_input_bar);
        int baseBottomMargin = 20;
        ViewCompat.setOnApplyWindowInsetsListener(messageInputBar, (view, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int bottomInset = Math.max(imeBottom, systemBottom);
            android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) view
                    .getLayoutParams();
            params.bottomMargin = baseBottomMargin + bottomInset;
            view.setLayoutParams(params);
            recyclerMessages.setPadding(recyclerMessages.getPaddingLeft(),
                    recyclerMessages.getPaddingTop(), recyclerMessages.getPaddingRight(),
                    112 + bottomInset);
            return insets;
        });
        ViewCompat.requestApplyInsets(messageInputBar);
        inputMessage.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                view.postDelayed(() -> {
                    view.requestRectangleOnScreen(new android.graphics.Rect(0, 0,
                            view.getWidth(), view.getHeight()), true);
                    if (chatAdapter.getItemCount() > 0) {
                        recyclerMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                }, 200);
            }
        });

        textTitle.setText(getString(R.string.chat_with_template, contactName));

        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter();
        recyclerMessages.setAdapter(chatAdapter);

        chatListener = storageManager.observeChat(itemId, new StorageManager.ChatCallback() {
            @Override
            public void onChanged(List<ChatMessage> messages) {
                renderChat(messages);
                if (messages.isEmpty()) {
                    addMessage("owner", getString(R.string.initial_owner_message, itemType.toLowerCase()));
                }
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(ChatActivity.this, R.string.cloud_load_error, Toast.LENGTH_SHORT).show();
            }
        });

        buttonSend.setOnClickListener(v -> {
            String text = inputMessage.getText().toString().trim();
            if (text.isEmpty()) {
                return;
            }

            addMessage("you", text);
            inputMessage.setText("");
        });
    }

    private void addMessage(String sender, String text) {
        storageManager.addCloudMessage(itemId, new ChatMessage(sender, text, System.currentTimeMillis()), error -> {
            if (error != null) {
                runOnUiThread(() -> Toast.makeText(this, R.string.cloud_save_error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void renderChat(List<ChatMessage> list) {
        chatAdapter.submitList(list);
        if (!list.isEmpty()) {
            recyclerMessages.scrollToPosition(list.size() - 1);
        }
    }

    @Override
    protected void onDestroy() {
        if (chatListener != null) {
            chatListener.remove();
        }
        super.onDestroy();
    }
}
