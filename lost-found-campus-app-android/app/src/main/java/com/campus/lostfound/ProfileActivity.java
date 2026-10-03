package com.campus.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.campus.lostfound.data.StorageManager;

public class ProfileActivity extends AppCompatActivity {
    private StorageManager storageManager;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        storageManager = new StorageManager(this);
        TextView emailText = findViewById(R.id.profile_email);
        statusText = findViewById(R.id.profile_status);
        Button logout = findViewById(R.id.button_logout);
        Button delete = findViewById(R.id.button_delete_account);

        String email = storageManager.getCurrentUserEmail();
        emailText.setText(email == null ? "Gachon account" : email);

        logout.setOnClickListener(v -> {
            storageManager.signOut();
            returnToMain();
        });

        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete account?")
                .setMessage("Your Firebase account will be permanently deleted.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                .show());
    }

    private void deleteAccount() {
        statusText.setText("Deleting account...");
        storageManager.deleteCurrentAccount(error -> runOnUiThread(() -> {
            if (error != null) {
                statusText.setText(error.getLocalizedMessage());
                return;
            }
            returnToMain();
        }));
    }

    private void returnToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}