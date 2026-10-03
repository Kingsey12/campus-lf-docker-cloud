package com.campus.lostfound;

import android.content.Intent;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.campus.lostfound.adapter.ItemAdapter;
import com.campus.lostfound.data.StorageManager;
import com.campus.lostfound.model.Item;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final List<Item> allItems = new ArrayList<>();

    private StorageManager storageManager;
    private ItemAdapter itemAdapter;

    private EditText searchInput;
    private Spinner filterCategory;
    private Spinner filterType;
    private RecyclerView recyclerItems;
    private FrameLayout sectionContainer;
    private FloatingActionButton fabAddItem;
    private LinearLayout discoverControls;
    private TextView sectionTitleHeader;
    private ListenerRegistration itemsListener;
    private MaterialButton authButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storageManager = new StorageManager(this);

        searchInput = findViewById(R.id.search_input);
        filterCategory = findViewById(R.id.filter_category);
        filterType = findViewById(R.id.filter_type);
        recyclerItems = findViewById(R.id.recycler_items);
        fabAddItem = findViewById(R.id.fab_add_item);
        sectionContainer = findViewById(R.id.section_container);
        discoverControls = findViewById(R.id.discover_controls);
        sectionTitleHeader = findViewById(R.id.section_title_header);
        authButton = findViewById(R.id.auth_button);
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);

        authButton.setOnClickListener(v -> {
            if (storageManager.isGuest()) {
                startActivity(new Intent(MainActivity.this, AuthActivity.class));
            } else {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            }
        });

        setupSpinners();

        recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        itemAdapter = new ItemAdapter(new ItemAdapter.OnItemActionListener() {
            @Override
            public void onContact(Item item) {
                openContact(item.getContact());
            }

            @Override
            public void onChat(Item item) {
                if (storageManager.isGuest()) {
                    Toast.makeText(MainActivity.this, R.string.login_required_for_chat, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(MainActivity.this, AuthActivity.class));
                    return;
                }
                Intent chatIntent = new Intent(MainActivity.this, ChatActivity.class);
                chatIntent.putExtra(ChatActivity.EXTRA_ITEM_ID, item.getId());
                chatIntent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, item.getContactName());
                chatIntent.putExtra(ChatActivity.EXTRA_ITEM_TYPE, item.getType());
                startActivity(chatIntent);
            }

            @Override
            public void onDelete(Item item) {
                confirmDelete(item);
            }
        });
        recyclerItems.setAdapter(itemAdapter);

        fabAddItem.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddItemActivity.class)));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        filterCategory.setOnItemSelectedListener(SimpleItemSelectedListener.create(position -> applyFilters()));
        filterType.setOnItemSelectedListener(SimpleItemSelectedListener.create(position -> applyFilters()));

        bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_chats) {
                showChats();
            } else if (item.getItemId() == R.id.nav_posts) {
                showPosts();
            } else {
                showBrowse();
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        storageManager.ensureSignedIn(new StorageManager.AuthCallback() {
            @Override
            public void onReady(String userId) {
                itemAdapter.setCurrentUserId(userId);
                authButton.setText(storageManager.isGuest() ? R.string.sign_in : R.string.nav_account);
                if (storageManager.isGuest()) {
                    fabAddItem.hide();
                } else {
                    fabAddItem.show();
                }
                if (itemsListener == null) {
                    itemsListener = storageManager.observeItems(new StorageManager.ItemsCallback() {
                        @Override
                        public void onSuccess(List<Item> items) {
                            allItems.clear();
                            allItems.addAll(items);
                            applyFilters();
                        }

                        @Override
                        public void onError(Exception error) {
                            allItems.clear();
                            allItems.addAll(storageManager.getItems());
                            applyFilters();
                            Toast.makeText(MainActivity.this, R.string.cloud_load_error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(MainActivity.this, R.string.auth_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (itemsListener != null) {
            itemsListener.remove();
            itemsListener = null;
        }
        super.onDestroy();
    }

    private void showBrowse() {
        recyclerItems.setVisibility(RecyclerView.VISIBLE);
        sectionContainer.setVisibility(FrameLayout.GONE);
        discoverControls.setVisibility(LinearLayout.VISIBLE);
        sectionTitleHeader.setVisibility(TextView.GONE);
        if (storageManager.isGuest()) {
            fabAddItem.hide();
        } else {
            fabAddItem.show();
        }
    }

    private void showChats() {
        recyclerItems.setVisibility(RecyclerView.GONE);
        fabAddItem.hide();
        showSectionHeader(R.string.section_chats_title);
        sectionContainer.setVisibility(FrameLayout.VISIBLE);
        LinearLayout content = createSectionLayout();

        Map<String, List<com.campus.lostfound.model.ChatMessage>> chats = storageManager.getChats();
        boolean hasChats = false;
        for (Item item : allItems) {
            List<com.campus.lostfound.model.ChatMessage> messages = chats.get(item.getId());
            if (messages == null || messages.isEmpty()) {
                continue;
            }
            hasChats = true;
            MaterialCardView card = createSectionCard();
            LinearLayout row = createRow();
            TextView title = createPrimaryText(item.getContactName());
            TextView subtitle = createSecondaryText(item.getTitle());
            row.addView(title);
            row.addView(subtitle);
            card.addView(row);
            card.setOnClickListener(v -> openChat(item));
            content.addView(card);
        }
        if (!hasChats) {
            addEmptyState(content, R.drawable.ic_empty_messages, R.string.empty_chats_title,
                    R.string.empty_chats);
        }
        sectionContainer.removeAllViews();
        sectionContainer.addView(content);
    }

    private void showPosts() {
        recyclerItems.setVisibility(RecyclerView.GONE);
        fabAddItem.hide();
        showSectionHeader(R.string.section_posts_title);
        sectionContainer.setVisibility(FrameLayout.VISIBLE);
        LinearLayout content = createSectionLayout();
        if (allItems.isEmpty()) {
            addEmptyState(content, R.drawable.ic_empty_listing, R.string.empty_posts_title,
                    R.string.empty_posts);
        } else {
            List<Item> posts = new ArrayList<>();
            String userId = storageManager.getCurrentUserId();
            for (Item item : allItems) {
                if (userId != null && userId.equals(item.getCreatorId())) {
                    posts.add(item);
                }
            }
            if (posts.isEmpty()) {
                addEmptyState(content, R.drawable.ic_empty_listing, R.string.empty_posts_title,
                        R.string.empty_posts);
                sectionContainer.removeAllViews();
                sectionContainer.addView(content);
                return;
            }
            posts.sort(Comparator.comparingLong(Item::getCreatedAt).reversed());
            for (Item item : posts) {
                MaterialCardView card = createSectionCard();
                LinearLayout row = createRow();
                row.addView(createPrimaryText(item.getTitle()));
                row.addView(createSecondaryText(item.getType() + " · " + item.getLocation()));
                card.addView(row);
                card.setOnClickListener(v -> showBrowseAndScrollTo(item));
                content.addView(card);
            }
        }
        sectionContainer.removeAllViews();
        sectionContainer.addView(content);
    }

    private void showAccount() {
        recyclerItems.setVisibility(RecyclerView.GONE);
        fabAddItem.hide();
        showSectionHeader(R.string.section_account_title);
        sectionContainer.setVisibility(FrameLayout.VISIBLE);
        LinearLayout content = createSectionLayout();
        MaterialCardView profile = createSectionCard();
        LinearLayout row = createRow();
        row.addView(createPrimaryText(getString(R.string.account_local_title)));
        row.addView(createSecondaryText(getString(R.string.account_local_description)));
        profile.addView(row);
        content.addView(profile);
        addStatText(content, R.string.account_posts_count, allItems.size());
        addStatText(content, R.string.account_chats_count, storageManager.getChats().size());
        MaterialButton addPost = createPrimaryButton(getString(R.string.account_add_post));
        addPost.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddItemActivity.class)));
        content.addView(addPost);
        sectionContainer.removeAllViews();
        sectionContainer.addView(content);
    }

    private void showSectionHeader(int titleRes) {
        discoverControls.setVisibility(LinearLayout.GONE);
        sectionTitleHeader.setVisibility(TextView.VISIBLE);
        sectionTitleHeader.setText(titleRes);
    }

    private LinearLayout createSectionLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 22, 20, 16);
        return layout;
    }

    private MaterialCardView createSectionCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(getColor(R.color.surfaceVariant));
        card.setStrokeColor(getColor(R.color.outline_bright));
        card.setStrokeWidth(1);
        card.setRadius(14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 10);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 14, 16, 14);
        return row;
    }

    private TextView createPrimaryText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.text_primary));
        view.setTextSize(16);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private TextView createSecondaryText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setTextSize(13);
        view.setPadding(0, 4, 0, 0);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private void addStatText(LinearLayout parent, int textRes, int value) {
        parent.addView(createSecondaryText(getString(textRes, value)));
    }

    private void addEmptyState(LinearLayout parent, int iconRes, int titleRes, int descriptionRes) {
        LinearLayout emptyState = new LinearLayout(this);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(android.view.Gravity.CENTER);
        emptyState.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        emptyState.setLayoutParams(emptyParams);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(72, 72);
        iconParams.setMargins(0, 0, 0, 18);
        icon.setLayoutParams(iconParams);
        emptyState.addView(icon);

        TextView title = createPrimaryText(getString(titleRes));
        title.setTextSize(19);
        title.setGravity(android.view.Gravity.CENTER);
        emptyState.addView(title);

        TextView description = createSecondaryText(getString(descriptionRes));
        description.setTextSize(15);
        description.setGravity(android.view.Gravity.CENTER);
        description.setPadding(0, 8, 0, 20);
        emptyState.addView(description);
        parent.addView(emptyState);
    }

    private MaterialButton createPrimaryButton(String text) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setTextColor(getColor(R.color.onPrimary));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setCornerRadius(16);
        button.setIconResource(android.R.drawable.ic_input_add);
        button.setIconTint(android.content.res.ColorStateList.valueOf(getColor(R.color.onPrimary)));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.primary)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 52);
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        button.setLayoutParams(params);
        return button;
    }

    private void openChat(Item item) {
        Intent chatIntent = new Intent(MainActivity.this, ChatActivity.class);
        chatIntent.putExtra(ChatActivity.EXTRA_ITEM_ID, item.getId());
        chatIntent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, item.getContactName());
        chatIntent.putExtra(ChatActivity.EXTRA_ITEM_TYPE, item.getType());
        startActivity(chatIntent);
    }

    private void showBrowseAndScrollTo(Item item) {
        showBrowse();
        int position = allItems.indexOf(item);
        if (position >= 0) {
            recyclerItems.scrollToPosition(position);
        }
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.categories_filter,
                R.layout.spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterCategory.setAdapter(categoryAdapter);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.types_filter,
                R.layout.spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterType.setAdapter(typeAdapter);
    }

    private void applyFilters() {
        String query = searchInput.getText().toString().trim().toLowerCase();
        String category = filterCategory.getSelectedItem().toString();
        String type = filterType.getSelectedItem().toString();

        List<Item> filtered = new ArrayList<>();
        for (Item item : allItems) {
            boolean matchText = (item.getTitle() + " " + item.getLocation() + " " + item.getCategory())
                    .toLowerCase()
                    .contains(query);
            boolean matchCategory = category.equals(getString(R.string.all_categories))
                    || item.getCategory().equals(category);
            boolean matchType = type.equals(getString(R.string.all_types)) || item.getType().equals(type);
            if (matchText && matchCategory && matchType) {
                filtered.add(item);
            }
        }

        filtered.sort(Comparator.comparingLong(Item::getCreatedAt).reversed());
        itemAdapter.submitList(filtered);
    }

    private void openContact(String contact) {
        if (contact.contains("@")) {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:" + contact));
            startActivitySafe(emailIntent);
            return;
        }

        String phoneSanitized = contact.replaceAll("[\\\\s-]", "");
        if (phoneSanitized.matches("^\\\\+?[0-9]{8,15}$")) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse("tel:" + phoneSanitized));
            startActivitySafe(dialIntent);
            return;
        }

        Toast.makeText(this, R.string.contact_not_recognized, Toast.LENGTH_LONG).show();
    }

    private void startActivitySafe(Intent intent) {
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(Item item) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.delete_item_title)
                .setMessage(getString(R.string.delete_item_message, item.getTitle()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    storageManager.deleteItemFromCloud(item.getId(), error -> {
                        if (error != null) {
                            Toast.makeText(this, R.string.cloud_save_error, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        allItems.removeIf(existing -> existing.getId().equals(item.getId()));
                        applyFilters();
                        Toast.makeText(this, R.string.item_deleted, Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
