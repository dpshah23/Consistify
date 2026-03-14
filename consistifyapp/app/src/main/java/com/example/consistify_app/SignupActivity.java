package com.example.consistify_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        EditText etUsername = findViewById(R.id.et_username);
        EditText etEmail = findViewById(R.id.et_email);
        EditText etPassword = findViewById(R.id.et_password);
        Button btnSignup = findViewById(R.id.btn_signup);
        TextView tvGoLogin = findViewById(R.id.tv_go_login);

        btnSignup.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
                Toast.makeText(this, "Empty fields", Toast.LENGTH_SHORT).show();
                return;
            }

            ApiClient.getApi().signupUser(username, email, password).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Toast.makeText(SignupActivity.this, "Account created! Please log in.", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(SignupActivity.this, "Sign up failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    Toast.makeText(SignupActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                }
            });
        });

        tvGoLogin.setOnClickListener(v -> finish());
    }
}
