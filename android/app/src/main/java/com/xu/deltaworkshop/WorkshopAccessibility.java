package com.xu.deltaworkshop;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class WorkshopAccessibility extends AccessibilityService {
    public static volatile WorkshopAccessibility instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastCapture;
    private android.view.View taskOverlay;
    private static final String GAME = "com.tencent.tmgp.dfm";
    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onDestroy() { hideTaskOverlay(); if (instance == this) instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() { ScriptRunner.requestStop(); }
    public void closeGameAndLock() {
        main.post(() -> {
            if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
                android.util.Log.w("DeltaWorkshop", "Could not return home after completed cycle");
                return;
            }
            main.postDelayed(() -> {
                if (!performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) {
                    android.util.Log.w("DeltaWorkshop", "Could not lock screen after completed cycle");
                }
            }, 500);
        });
    }
    public void showTaskOverlay(String label) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> showTaskOverlay(label)); return; }
        hideTaskOverlay();
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(18, 0, 8, 0);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(android.graphics.Color.rgb(18, 70, 62)); background.setCornerRadius(18); row.setBackground(background);
        android.widget.TextView status = new android.widget.TextView(this);
        status.setText(label); status.setTextColor(android.graphics.Color.WHITE); status.setTextSize(13);
        row.addView(status);
        android.widget.Button stop = new android.widget.Button(this);
        stop.setText("停止"); stop.setTextSize(13); stop.setAllCaps(false);
        stop.setOnClickListener(v -> { ScriptRunner.requestStop(); status.setText("正在停止"); stop.setEnabled(false); });
        row.addView(stop, new android.widget.LinearLayout.LayoutParams(dp(64), dp(48)));
        android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT, dp(48),
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL; params.y = dp(4);
        ((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).addView(row, params); taskOverlay = row;
    }
    public void hideTaskOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::hideTaskOverlay); return; }
        if (taskOverlay != null) {
            try { ((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).removeView(taskOverlay); } catch (Exception ignored) {}
            taskOverlay = null;
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    public String foregroundPackage() {
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        try { return String.valueOf(root.getPackageName()); } finally { root.recycle(); }
    }
    public synchronized Bitmap capture() throws Exception {
        long pause = 350 - (android.os.SystemClock.uptimeMillis() - lastCapture);
        if (pause > 0) Thread.sleep(pause);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Bitmap> bitmap = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult r) {
                try (android.hardware.HardwareBuffer buffer = r.getHardwareBuffer()) {
                    Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, r.getColorSpace());
                    if (hardware == null) throw new IllegalStateException("SCREENSHOT_EMPTY");
                    Bitmap copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    hardware.recycle();
                    bitmap.set(copy);
                } catch (Exception e) { failure.set(e); }
                finally { latch.countDown(); }
            }
            @Override public void onFailure(int code) { failure.set(new IllegalStateException("SCREENSHOT_FAILED_" + code)); latch.countDown(); }
        });
        if (!latch.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("SCREENSHOT_TIMEOUT");
        lastCapture = android.os.SystemClock.uptimeMillis();
        if (failure.get() != null) throw failure.get();
        return bitmap.get();
    }
    public boolean tap(int x, int y, long duration) throws Exception {
        if (!GAME.equals(foregroundPackage())) throw new IllegalStateException("GAME_NOT_FOREGROUND");
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean ok = new java.util.concurrent.atomic.AtomicBoolean(false);
        main.post(() -> {
            Path path = new Path(); path.moveTo(x, y);
            boolean sent = dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(1, duration))).build(), new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription g) { ok.set(true); latch.countDown(); }
                    @Override public void onCancelled(GestureDescription g) { latch.countDown(); }
                }, main);
            if (!sent) latch.countDown();
        });
        return latch.await(5, TimeUnit.SECONDS) && ok.get();
    }
}
