package com.voicelib.vox;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VoxAccessibilityService extends AccessibilityService implements TextToSpeech.OnInitListener {
    public static final String ACTION_TOGGLE = "com.voicelib.vox.TOGGLE";
    public static final String ACTION_PREVIOUS = "com.voicelib.vox.PREVIOUS";
    public static final String ACTION_NEXT = "com.voicelib.vox.NEXT";
    public static final String ACTION_STOP = "com.voicelib.vox.STOP";

    private static final String PREFS = "vox_prefs";
    private static final String KEY_RATE = "speech_rate";
    private static final String KEY_PITCH = "speech_pitch";
    private static final String CHANNEL_ID = "vox_reader";
    private static final int NOTIFICATION_ID = 3107;
    private static final int MAX_CAPTURED_CHARS = 24000;

    private static VoxAccessibilityService instance;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean paused = false;
    private boolean speaking = false;
    private final List<String> chunks = new ArrayList<>();
    private int currentIndex = 0;
    private WindowManager windowManager;
    private View bubble;

    public static VoxAccessibilityService getInstance() { return instance; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        tts = new TextToSpeech(this, this);
        createNotificationChannel();
        showBubble();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // V0.1 reads only on explicit user request via the VOX bubble.
    }

    @Override
    public void onInterrupt() {
        pauseReading();
    }

    @Override
    public void onDestroy() {
        hideBubble();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        cancelNotification();
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(this, "VOX : moteur vocal indisponible", Toast.LENGTH_LONG).show();
            return;
        }
        ttsReady = true;
        int result = tts.setLanguage(Locale.FRANCE);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }
        reloadVoiceSettings();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                speaking = true;
                updateNotification();
            }

            @Override public void onDone(String utteranceId) {
                speaking = false;
                if (!paused) {
                    currentIndex++;
                    if (currentIndex < chunks.size()) {
                        speakCurrent();
                    } else {
                        currentIndex = Math.max(0, chunks.size() - 1);
                        cancelNotification();
                    }
                }
            }

            @Override public void onError(String utteranceId) {
                speaking = false;
                updateNotification();
            }
        });
    }

    public void reloadVoiceSettings() {
        if (!ttsReady || tts == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        tts.setSpeechRate(prefs.getFloat(KEY_RATE, 1.0f));
        tts.setPitch(prefs.getFloat(KEY_PITCH, 1.0f));
    }

    private void showBubble() {
        if (bubble != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        TextView button = new TextView(this);
        button.setText("VOX ▶");
        button.setTextSize(14);
        button.setTextColor(0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6222228);
        bg.setCornerRadius(dp(24));
        button.setBackground(bg);
        button.setElevation(dp(8));
        button.setOnClickListener(v -> readCurrentScreen());
        button.setOnLongClickListener(v -> {
            stopReading();
            Toast.makeText(this, "VOX : lecture arrêtée", Toast.LENGTH_SHORT).show();
            return true;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.x = dp(8);
        params.y = 0;
        windowManager.addView(button, params);
        bubble = button;
    }

    private void hideBubble() {
        if (bubble != null && windowManager != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) { }
            bubble = null;
        }
    }

    private void readCurrentScreen() {
        if (!ttsReady) {
            Toast.makeText(this, "VOX initialise encore la voix…", Toast.LENGTH_SHORT).show();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Toast.makeText(this, "VOX : aucun texte accessible sur cet écran", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> blocks = new ArrayList<>();
        collectText(root, blocks, new HashSet<>());
        root.recycle();

        List<String> clean = cleanBlocks(blocks);
        chunks.clear();
        chunks.addAll(segment(clean));
        currentIndex = 0;
        paused = false;

        if (chunks.isEmpty()) {
            Toast.makeText(this, "VOX : aucun texte lisible trouvé", Toast.LENGTH_SHORT).show();
            return;
        }

        reloadVoiceSettings();
        speakCurrent();
    }

    private void collectText(AccessibilityNodeInfo node, List<String> out, Set<Integer> visited) {
        if (node == null) return;
        int identity = System.identityHashCode(node);
        if (!visited.add(identity)) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        if (node.getChildCount() == 0) {
            if (text != null && !text.toString().trim().isEmpty()) {
                out.add(text.toString().trim());
            } else if (desc != null && !desc.toString().trim().isEmpty()) {
                out.add(desc.toString().trim());
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectText(child, out, visited);
                child.recycle();
            }
        }
    }

    private List<String> cleanBlocks(List<String> input) {
        List<String> result = new ArrayList<>();
        String previous = null;
        int total = 0;
        for (String raw : input) {
            String value = raw.replaceAll("\\s+", " ").trim();
            if (value.isEmpty() || value.equals(previous)) continue;
            if (value.equals("VOX ▶")) continue;
            if (total + value.length() > MAX_CAPTURED_CHARS) break;
            result.add(value);
            total += value.length();
            previous = value;
        }
        return result;
    }

    private List<String> segment(List<String> blocks) {
        List<String> result = new ArrayList<>();
        for (String block : blocks) {
            if (block.length() <= 700) {
                result.add(block);
                continue;
            }
            BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.FRANCE);
            iterator.setText(block);
            int start = iterator.first();
            int end = iterator.next();
            StringBuilder current = new StringBuilder();
            while (end != BreakIterator.DONE) {
                String sentence = block.substring(start, end).trim();
                if (current.length() + sentence.length() + 1 > 700 && current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                if (!sentence.isEmpty()) {
                    if (current.length() > 0) current.append(' ');
                    current.append(sentence);
                }
                start = end;
                end = iterator.next();
            }
            if (current.length() > 0) result.add(current.toString());
        }
        return result;
    }

    private void speakCurrent() {
        if (!ttsReady || chunks.isEmpty() || currentIndex < 0 || currentIndex >= chunks.size()) return;
        paused = false;
        String utteranceId = "vox-" + currentIndex + "-" + System.nanoTime();
        Bundle params = new Bundle();
        tts.speak(chunks.get(currentIndex), TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        updateNotification();
    }

    private void pauseReading() {
        if (tts != null) tts.stop();
        paused = true;
        speaking = false;
        updateNotification();
    }

    private void resumeReading() {
        if (chunks.isEmpty()) return;
        speakCurrent(); // V0.1 restarts the current chunk.
    }

    private void previousChunk() {
        if (chunks.isEmpty()) return;
        currentIndex = Math.max(0, currentIndex - 1);
        speakCurrent();
    }

    private void nextChunk() {
        if (chunks.isEmpty()) return;
        currentIndex = Math.min(chunks.size() - 1, currentIndex + 1);
        speakCurrent();
    }

    private void stopReading() {
        if (tts != null) tts.stop();
        paused = false;
        speaking = false;
        chunks.clear();
        currentIndex = 0;
        cancelNotification();
    }

    public void handleMediaAction(String action) {
        switch (action) {
            case ACTION_TOGGLE:
                if (speaking && !paused) pauseReading(); else resumeReading();
                break;
            case ACTION_PREVIOUS:
                previousChunk();
                break;
            case ACTION_NEXT:
                nextChunk();
                break;
            case ACTION_STOP:
                stopReading();
                break;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Lecture VOX", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Commandes de lecture vocale VOX");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MediaControlReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateNotification() {
        if (chunks.isEmpty()) return;
        String state = paused ? "En pause" : "Lecture en cours";
        String position = (currentIndex + 1) + "/" + chunks.size();
        int toggleIcon = paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause;
        String toggleTitle = paused ? "Reprendre" : "Pause";

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("VOX — " + state)
                .setContentText("Passage " + position)
                .setOnlyAlertOnce(true)
                .setOngoing(!paused)
                .addAction(android.R.drawable.ic_media_previous, "Précédent", actionIntent(ACTION_PREVIOUS, 1))
                .addAction(toggleIcon, toggleTitle, actionIntent(ACTION_TOGGLE, 2))
                .addAction(android.R.drawable.ic_media_next, "Suivant", actionIntent(ACTION_NEXT, 3))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", actionIntent(ACTION_STOP, 4));

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void cancelNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
