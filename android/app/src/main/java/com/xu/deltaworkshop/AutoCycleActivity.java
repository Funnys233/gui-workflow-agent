package com.xu.deltaworkshop;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Visible just long enough for Android to wake the display and dismiss a swipe lock. */
public final class AutoCycleActivity extends Activity {
    private long deadline;
    private boolean requested;

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        deadline = getIntent().getLongExtra(AutoCycle.DEADLINE, 0);
        if (AutoCycle.dueAccount(this, deadline).isEmpty() || AutoCycle.hasSecureLock(this)) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView status = new TextView(this);
        status.setText("正在打开三角洲…");
        status.setTextSize(18);
        status.setTextColor(getColor(R.color.ink));
        status.setBackgroundColor(getColor(R.color.surface));
        status.setGravity(Gravity.CENTER);
        setContentView(status);
    }

    @Override protected void onResume() {
        super.onResume();
        if (isFinishing() || requested) return;
        requested = true;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard.isDeviceSecure()) {
            stop("手机已设安全锁，自动续造暂停");
        } else if (!keyguard.isKeyguardLocked()) {
            getWindow().getDecorView().post(this::startRound);
        } else {
            keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { startRound(); }
                @Override public void onDismissError() { stop("未能解除滑动锁屏，检查制造后重新计时"); }
                @Override public void onDismissCancelled() { stop("唤醒已取消，检查制造后重新计时"); }
            });
        }
    }

    private void startRound() {
        if (isFinishing() || isDestroyed()) return;
        String account = AutoCycle.dueAccount(this, deadline);
        if (account.isEmpty()) {
            finish();
            return;
        }
        if (AutoCycle.hasSecureLock(this) || ScriptRunner.running.get()) {
            stop("自动续造暂停，检查制造后重新计时");
            return;
        }
        try {
            RunService.start(this, "cycle", account);
            AutoCycle.pause(this, deadline, "正在执行自动续造…");
            finish();
        } catch (RuntimeException e) {
            stop("未能开始续造，检查制造后重新计时");
        }
    }

    private void stop(String message) {
        AutoCycle.pause(this, deadline, message);
        finish();
    }
}
