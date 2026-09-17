package com.voicelib.vox;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "vox_prefs";
    private static final String KEY_RATE = "speech_rate";
    private static final String KEY_PITCH = "speech_pitch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermissionIfNeeded();
        setContentView(buildUi());
    }

    private android.view.View buildUi() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(247, 247, 249));

        TextView title = text("VOX", 32, true);
        root.addView(title);

        TextView subtitle = text("V0.1 — lecteur vocal d’écran", 18, false);
        subtitle.setPadding(0, dp(4), 0, dp(22));
        root.addView(subtitle);

        TextView instructions = text(
                "1. Active VOX dans Accessibilité.\n" +
                "2. Retourne dans ChatGPT, Chrome ou une autre appli.\n" +
                "3. Touche la bulle VOX ▶ pour lire le texte visible.\n" +
                "4. Utilise la notification pour pause, précédent, suivant ou arrêt.",
                16, false);
        root.addView(instructions);

        Button accessibility = new Button(this);
        accessibility.setText("Activer VOX dans Accessibilité");
        accessibility.setAllCaps(false);
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, dp(22), 0, dp(20));
        root.addView(accessibility, buttonParams);

        TextView rateLabel = text("Vitesse", 16, true);
        root.addView(rateLabel);
        SeekBar rate = new SeekBar(this);
        rate.setMax(150); // 0.50x -> 2.00x
        rate.setProgress(Math.round((prefs.getFloat(KEY_RATE, 1.0f) - 0.5f) * 100));
        root.addView(rate);

        TextView rateValue = text(formatRate(prefs.getFloat(KEY_RATE, 1.0f)), 14, false);
        rateValue.setGravity(Gravity.END);
        root.addView(rateValue);
        rate.setOnSeekBarChangeListener(simpleListener(progress -> {
            float value = 0.5f + progress / 100f;
            prefs.edit().putFloat(KEY_RATE, value).apply();
            rateValue.setText(formatRate(value));
            VoxAccessibilityService service = VoxAccessibilityService.getInstance();
            if (service != null) service.reloadVoiceSettings();
        }));

        TextView pitchLabel = text("Hauteur — grave ↔ aigu", 16, true);
        pitchLabel.setPadding(0, dp(18), 0, 0);
        root.addView(pitchLabel);
        SeekBar pitch = new SeekBar(this);
        pitch.setMax(140); // 0.60 -> 2.00
        pitch.setProgress(Math.round((prefs.getFloat(KEY_PITCH, 1.0f) - 0.6f) * 100));
        root.addView(pitch);

        TextView pitchValue = text(formatPitch(prefs.getFloat(KEY_PITCH, 1.0f)), 14, false);
        pitchValue.setGravity(Gravity.END);
        root.addView(pitchValue);
        pitch.setOnSeekBarChangeListener(simpleListener(progress -> {
            float value = 0.6f + progress / 100f;
            prefs.edit().putFloat(KEY_PITCH, value).apply();
            pitchValue.setText(formatPitch(value));
            VoxAccessibilityService service = VoxAccessibilityService.getInstance();
            if (service != null) service.reloadVoiceSettings();
        }));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER);
        presets.setPadding(0, dp(18), 0, 0);
        addPresetButton(presets, "Naturel", 1.0f, 1.0f, rate, pitch);
        addPresetButton(presets, "Deep", 0.92f, 0.72f, rate, pitch);
        addPresetButton(presets, "Clair", 1.06f, 1.28f, rate, pitch);
        root.addView(presets);

        TextView note = text(
                "V0.1 : le réglage de timbre est volontairement simple. Les effets Robot, Radio, ASMR et les commandes vocales arriveront après validation de la lecture d’écran.",
                14, false);
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void addPresetButton(LinearLayout parent, String name, float rateValue, float pitchValue,
                                 SeekBar rateBar, SeekBar pitchBar) {
        Button button = new Button(this);
        button.setText(name);
        button.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(button, lp);
        button.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putFloat(KEY_RATE, rateValue)
                    .putFloat(KEY_PITCH, pitchValue)
                    .apply();
            rateBar.setProgress(Math.round((rateValue - 0.5f) * 100));
            pitchBar.setProgress(Math.round((pitchValue - 0.6f) * 100));
            VoxAccessibilityService service = VoxAccessibilityService.getInstance();
            if (service != null) service.reloadVoiceSettings();
            Toast.makeText(this, "Preset " + name, Toast.LENGTH_SHORT).show();
        });
    }

    private SeekBar.OnSeekBarChangeListener simpleListener(IntConsumer consumer) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) consumer.accept(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private interface IntConsumer { void accept(int value); }

    private TextView text(String value, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.rgb(25, 25, 30));
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    private String formatRate(float value) { return String.format(java.util.Locale.FRANCE, "%.2f×", value); }
    private String formatPitch(float value) { return String.format(java.util.Locale.FRANCE, "%.2f", value); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }
}
