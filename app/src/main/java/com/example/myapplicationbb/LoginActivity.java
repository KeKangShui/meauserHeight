package com.example.myapplicationbb;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences("login_prefs", MODE_PRIVATE);

        // 检查是否已经登录
        if (isLoggedIn()) {
            startMainActivity();
            finish();
            return;
        }

        // 初始化视图
        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);

        // 设置登录按钮点击事件
        loginButton.setOnClickListener(v -> attemptLogin());

        // 设置注册链接点击事件
        findViewById(R.id.register_link).setOnClickListener(v -> {
            // TODO: 跳转到注册页面
            Toast.makeText(this, "注册功能即将推出", Toast.LENGTH_SHORT).show();
        });
    }

    private void attemptLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // 验证输入
        if (TextUtils.isEmpty(username)) {
            usernameInput.setError("请输入用户名");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("请输入密码");
            return;
        }

        // TODO: 实现实际的登录验证逻辑
        // 这里暂时使用模拟登录
        if ("admin".equals(username) && "password".equals(password)) {
            // 保存登录状态
            saveLoginState(username);
            // 跳转到主页面
            startMainActivity();
            finish();
        } else {
            Toast.makeText(this, "用户名或密码错误", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLoginState(String username) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("is_logged_in", true);
        editor.putString("username", username);
        editor.apply();
    }

    private boolean isLoggedIn() {
        return sharedPreferences.getBoolean("is_logged_in", false);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}