package com.campus.lostfound;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.View;

import com.campus.lostfound.data.StorageManager;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

public class AuthActivity extends AppCompatActivity {
    private static final String GACHON_EMAIL_REGEX = "^[^@\\s]+@gachon\\.ac\\.kr$";
    private StorageManager storageManager;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView statusText;
    private TextView descriptionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_auth);

        ScrollView authScroll = findViewById(R.id.auth_scroll);
        int originalBottomPadding = authScroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(authScroll, (view, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                    originalBottomPadding + Math.max(ime.bottom, system.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(authScroll);

        storageManager = new StorageManager(this);
        emailInput = findViewById(R.id.auth_email);
        passwordInput = findViewById(R.id.auth_password);
        statusText = findViewById(R.id.auth_status);
        descriptionText = findViewById(R.id.auth_description);

        Button signIn = findViewById(R.id.button_sign_in);
        Button createAccount = findViewById(R.id.button_create_account);
        Button forgotPassword = findViewById(R.id.button_forgot_password);
        Button guest = findViewById(R.id.button_guest);
        View.OnFocusChangeListener scrollToField = (view, hasFocus) -> {
            if (hasFocus) {
                authScroll.postDelayed(() -> authScroll.smoothScrollTo(0, view.getBottom()), 150);
            }
        };
        emailInput.setOnFocusChangeListener(scrollToField);
        passwordInput.setOnFocusChangeListener(scrollToField);

        signIn.setOnClickListener(v -> signIn(false));
        createAccount.setOnClickListener(v -> signIn(true));
        forgotPassword.setOnClickListener(v -> resetPassword());
        guest.setOnClickListener(v -> {
            storageManager.ensureSignedIn(new StorageManager.AuthCallback() {
                @Override
                public void onReady(String userId) {
                    finish();
                }

                @Override
                public void onError(Exception error) {
                    showError(error);
                }
            });
        });
    }

    private void signIn(boolean createAccount) {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || password.length() < 6) {
            descriptionText.setText(R.string.auth_invalid_input);
            return;
        }
        if (!email.toLowerCase(java.util.Locale.ROOT).matches(GACHON_EMAIL_REGEX)) {
            descriptionText.setText(R.string.auth_gachon_only);
            return;
        }

        descriptionText.setText(R.string.auth_working);
        StorageManager.AuthCallback callback = new StorageManager.AuthCallback() {
            @Override
            public void onReady(String userId) {
                if (!storageManager.isEmailVerified()) {
                    storageManager.sendVerificationEmail(error -> runOnUiThread(() -> {
                        storageManager.signOut();
                        descriptionText.setText(error == null
                                ? R.string.auth_verify_sent
                                : R.string.auth_verify_error);
                    }));
                    return;
                }
                finish();
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        };
        if (createAccount) {
            storageManager.createEmailAccount(email, password, callback);
        } else {
            storageManager.signInWithEmail(email, password, callback);
        }
    }

    private void showError(Exception error) {
        int message;
        if (error instanceof FirebaseAuthInvalidUserException) {
            message = R.string.auth_user_not_found;
        } else if (error instanceof FirebaseAuthInvalidCredentialsException) {
            message = R.string.auth_wrong_password;
        } else if (error instanceof FirebaseAuthUserCollisionException) {
            message = R.string.auth_email_in_use;
        } else if (error != null && error.getMessage() != null
                && error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("badly formatted")) {
            message = R.string.auth_invalid_email;
        } else {
            descriptionText.setText(error == null ? getString(R.string.auth_error) : error.getLocalizedMessage());
            return;
        }
        descriptionText.setText(message);
    }

    private void resetPassword() {
        String email = emailInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (!email.matches(GACHON_EMAIL_REGEX)) {
            descriptionText.setText(R.string.auth_enter_email_for_reset);
            return;
        }
        statusText.setText(R.string.auth_working);
        storageManager.sendPasswordReset(email, error -> runOnUiThread(() -> {
            if (error == null) {
                descriptionText.setText(R.string.auth_reset_sent);
            } else {
                descriptionText.setText(R.string.auth_reset_error);
            }
        }));
    }
}
