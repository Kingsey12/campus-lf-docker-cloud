package com.campus.lostfound;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.appbar.MaterialToolbar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;

import com.campus.lostfound.data.StorageManager;
import com.campus.lostfound.model.Item;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddItemActivity extends AppCompatActivity {

    private EditText inputTitle;
    private Spinner inputCategory;
    private Spinner inputType;
    private EditText inputLocation;
    private EditText inputDate;
    private EditText inputContactName;
    private EditText inputContact;
    private EditText inputDescription;
    private TextView statusText;
    private ImageView imagePreview;
    private View layoutAddImage;
    private String selectedImageUriPath = null;

    private StorageManager storageManager;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    imageUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {
                            // Some gallery providers grant temporary read access only.
                        }
                        saveImagePreview(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_add_item);

        storageManager = new StorageManager(this);

        inputTitle = findViewById(R.id.input_title);
        inputCategory = findViewById(R.id.input_category);
        inputType = findViewById(R.id.input_type);
        inputLocation = findViewById(R.id.input_location);
        inputDate = findViewById(R.id.input_date);
        inputContactName = findViewById(R.id.input_contact_name);
        inputContact = findViewById(R.id.input_contact);
        inputDescription = findViewById(R.id.input_description);
        statusText = findViewById(R.id.text_status);
        imagePreview = findViewById(R.id.image_preview);
        layoutAddImage = findViewById(R.id.layout_add_image);
        View cardImage = findViewById(R.id.card_image);
        Button buttonPost = findViewById(R.id.button_post);
        NestedScrollView addItemScroll = findViewById(R.id.add_item_scroll);
        ViewCompat.setOnApplyWindowInsetsListener(addItemScroll, (view, insets) -> {
            int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                    40 + bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(addItemScroll);
        View.OnFocusChangeListener scrollToField = (view, hasFocus) -> {
            if (hasFocus) {
                addItemScroll.postDelayed(() -> addItemScroll.smoothScrollTo(0, view.getBottom()), 150);
            }
        };
        inputTitle.setOnFocusChangeListener(scrollToField);
        inputLocation.setOnFocusChangeListener(scrollToField);
        inputDate.setOnFocusChangeListener(scrollToField);
        inputContactName.setOnFocusChangeListener(scrollToField);
        inputContact.setOnFocusChangeListener(scrollToField);
        inputDescription.setOnFocusChangeListener(scrollToField);

        setupSpinners();

        MaterialToolbar toolbar = findViewById(R.id.toolbar_add);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        cardImage.setOnClickListener(v -> pickImage());
        buttonPost.setOnClickListener(v -> createItem());
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        imagePickerLauncher.launch(intent);
    }

    private void saveImagePreview(Uri uri) {
        selectedImageUriPath = uri.toString();
        imagePreview.setImageURI(uri);
        layoutAddImage.setVisibility(View.GONE);
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.categories,
                R.layout.spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        inputCategory.setAdapter(categoryAdapter);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.types,
                R.layout.spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        inputType.setAdapter(typeAdapter);
    }

    private void createItem() {
        String title = inputTitle.getText().toString().trim();
        String category = inputCategory.getSelectedItem().toString();
        String type = inputType.getSelectedItem().toString();
        String location = inputLocation.getText().toString().trim();
        String date = inputDate.getText().toString().trim();
        String contactName = inputContactName.getText().toString().trim();
        String contact = inputContact.getText().toString().trim();
        String description = inputDescription.getText().toString().trim();

        if (title.isEmpty() || location.isEmpty() || date.isEmpty() || contactName.isEmpty()
                || description.isEmpty()) {
            statusText.setText(R.string.fill_required_fields);
            return;
        }

        String itemId = UUID.randomUUID().toString();
        Item item = new Item(
                itemId,
                title,
                category,
                type,
                location,
                date,
                contactName,
                contact,
                description,
                selectedImageUriPath,
                System.currentTimeMillis());

        statusText.setText(R.string.cloud_saving);
        if (selectedImageUriPath != null && selectedImageUriPath.startsWith("content://")) {
            imageExecutor.execute(() -> {
                String imageData = compressImageForFirestore(Uri.parse(selectedImageUriPath));
                runOnUiThread(() -> {
                    if (imageData == null) {
                        statusText.setText(R.string.cloud_image_error);
                        return;
                    }
                    item.setImageUri(imageData);
                    saveItemToCloud(item);
                });
            });
            return;
        }
        saveItemToCloud(item);
    }

    private String compressImageForFirestore(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1440) {
                options.inSampleSize *= 2;
            }
            Bitmap bitmap;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) {
                return null;
            }

            int maxSide = 720;
            float scale = Math.min(1f, maxSide / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
            if (scale < 1f) {
                bitmap = Bitmap.createScaledBitmap(bitmap,
                        Math.round(bitmap.getWidth() * scale),
                        Math.round(bitmap.getHeight() * scale), true);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int quality = 72;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
            while (output.size() > 450 * 1024 && quality > 32) {
                output.reset();
                quality -= 8;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
            }
            if (output.size() > 450 * 1024) {
                Bitmap smaller = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, Math.round(bitmap.getWidth() * 0.7f)),
                        Math.max(1, Math.round(bitmap.getHeight() * 0.7f)), true);
                bitmap.recycle();
                bitmap = smaller;
                output.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, output);
            }
            bitmap.recycle();
            return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        } catch (Exception error) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private void saveItemToCloud(Item item) {
        storageManager.ensureSignedIn(new StorageManager.AuthCallback() {
            @Override
            public void onReady(String userId) {
                item.setCreatorId(userId);
                storageManager.saveItem(item, error -> runOnUiThread(() -> {
                    if (error != null) {
                        statusText.setText(R.string.cloud_save_error);
                        return;
                    }
                    statusText.setText(R.string.item_posted_successfully);
                    finish();
                }));
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> statusText.setText(R.string.auth_error));
            }
        });
    }
}
