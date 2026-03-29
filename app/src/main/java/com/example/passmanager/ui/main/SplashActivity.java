package com.example.passmanager.ui.main;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.passmanager.R;

import java.util.Random;

public class SplashActivity extends AppCompatActivity {

    private TextView textDecryption;
    private ProgressBar progressBoot;
    private Handler textHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    private final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        textDecryption = findViewById(R.id.text_decryption);
        progressBoot = findViewById(R.id.progress_boot);

        runBootSequence();
    }

    private void runBootSequence() {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(1200);
        animator.addUpdateListener(animation -> progressBoot.setProgress((int) animation.getAnimatedValue()));
        animator.start();

        Runnable textScrambler = new Runnable() {
            @Override
            public void run() {
                textDecryption.setText("Decrypting: " + generateGibberish(12));
                textHandler.postDelayed(this, 50);
            }
        };
        textHandler.post(textScrambler);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            textHandler.removeCallbacks(textScrambler);
            textDecryption.setText("VAULT SECURED");
            textDecryption.setTextColor(getResources().getColor(android.R.color.holo_green_light, getTheme()));

            new Handler(Looper.getMainLooper()).postDelayed(this::goToNextScreen, 150);
        }, 1200);
    }

    private String generateGibberish(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        return sb.toString();
    }

    private void goToNextScreen() {
        Intent intent = new Intent(SplashActivity.this, WelcomeActivity.class);
        startActivity(intent);
        finish();
    }
}