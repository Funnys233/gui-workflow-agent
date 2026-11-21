package com.xu.deltaworkshop;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AutoCycle extends BroadcastReceiver {
    static final String DEADLINE = "deadline";
    private static final long MINUTE = 60_000L;
    private static final Pattern GAME_TIMER = Pattern.compile("[0-9]{2}:[0-5][0-9]:[0-5][0-9]");
    private static final String CHECK_AGAIN = "检查制造后开始计时";
    private static PowerManager.WakeLock displayWakeLock;

    private static SharedPreferences state(Context c) {
        return c.getSharedPreferences("auto-cycle", Context.MODE_PRIVATE);
    }

    private static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    static boolean enabled(Context c) {
        return state(c).getBoolean("enabled", false);
    }

    static boolean hasAlarmPermission(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static boolean hasSecureLock(Context c) {
        return c.getSystemService(KeyguardManager.class).isDeviceSecure();
    }

    static synchronized void setEnabled(Context c, boolean enabled) {
        state(c).edit().putBoolean("enabled", enabled).apply();
        if (enabled) updateFromResult(c, ScriptRunner.readLatest(c));
        else clearDeadline(c, CHECK_AGAIN);
    }

    static long delayMillis(JSONObject result) {
        JSONArray stations = result.optJSONArray("manufacturing");
        if (stations == null) return 0;
        long latest = -1;
        for (int i = 0; i < stations.length(); i++) {
            JSONObject station = stations.optJSONObject(i);
            if (station == null) continue;
            String remaining = station.optString("finalRemaining");
            if (!GAME_TIMER.matcher(remaining).matches()) continue;
            long hours = Integer.parseInt(remaining.substring(0, 2));
            long minutes = Integer.parseInt(remaining.substring(3, 5));
            long seconds = Integer.parseInt(remaining.substring(6, 8));
            latest = Math.max(latest, hours * 3600 + minutes * 60 + seconds);
        }
        return latest < 0 ? 0 : ((latest + 119) / 60) * MINUTE;
    }

    static synchronized void updateFromResult(Context c, JSONObject result) {
        if (!enabled(c)) return;
        if (result != null && result.optBoolean("overviewCheckOnly")) {
            clearDeadline(c, "当前游戏画面仅支持检查制造");
            return;
        }
        if (result == null || !result.optBoolean("ok")) {
            clearDeadline(c, CHECK_AGAIN);
            return;
        }
        String account = result.optString("timerAccount");
        long age = System.currentTimeMillis() - result.optLong("timerReadAt");
        long remaining = delayMillis(result) - age;
        if (account.isEmpty() || age < 0 || remaining <= 0) {
            clearDeadline(c, CHECK_AGAIN);
            return;
        }
        if (!hasAlarmPermission(c)) {
            clearDeadline(c, "请开启「闹钟和提醒」权限");
            return;
        }
        long deadline = SystemClock.elapsedRealtime() + remaining;
        state(c).edit().putLong(DEADLINE, deadline).putInt("boot", bootCount(c))
            .putString("account", account).remove("message").apply();
        setAlarm(c, deadline);
    }

    private static void setAlarm(Context c, long deadline) {
        Intent alarm = new Intent(c, AutoCycle.class).putExtra(DEADLINE, deadline);
        PendingIntent pending = PendingIntent.getBroadcast(c, 0, alarm,
            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE);
        long wakeAt = System.currentTimeMillis() + Math.max(1, deadline - SystemClock.elapsedRealtime());
        try {
            c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(wakeAt, show), pending);
        } catch (SecurityException e) {
            clearDeadline(c, "请开启「闹钟和提醒」权限");
        }
    }

    private static void clearDeadline(Context c, String message) {
        PendingIntent pending = PendingIntent.getBroadcast(c, 0, new Intent(c, AutoCycle.class),
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) pending.cancel();
        state(c).edit().remove(DEADLINE).remove("account").putString("message", message).apply();
    }

    static synchronized String dueAccount(Context c, long deadline) {
        if (!matchesDeadline(c, deadline) || deadline > SystemClock.elapsedRealtime()) return "";
        return state(c).getString("account", "");
    }

    private static boolean matchesDeadline(Context c, long deadline) {
        SharedPreferences saved = state(c);
        return enabled(c) && deadline > 0 && saved.getLong(DEADLINE, 0) == deadline
            && saved.getInt("boot", -1) == bootCount(c);
    }

    static synchronized void pause(Context c, long deadline, String message) {
        if (dueAccount(c, deadline).isEmpty()) return;
        clearDeadline(c, message);
        c.sendBroadcast(new Intent(ScriptRunner.UPDATE).setPackage(c.getPackageName()));
    }

    static String status(Context c) {
        if (!enabled(c)) return "开启后按最晚完成时间自动续造";
        if (hasSecureLock(c)) return "手机已设安全锁，自动续造暂停";
        if (!hasAlarmPermission(c)) return "请开启「闹钟和提醒」权限";
        SharedPreferences saved = state(c);
        long deadline = saved.getLong(DEADLINE, 0);
        if (deadline == 0) return saved.getString("message", CHECK_AGAIN);
        if (saved.getInt("boot", -1) != bootCount(c)) return "手机已重启，检查制造后重新计时";
        long left = deadline - SystemClock.elapsedRealtime();
        if (left <= -MINUTE) return "未能自动唤醒，检查制造后重新计时";
        if (left <= 0) return "正在唤醒手机…";
        long seconds = (left + 999) / 1000;
        return String.format(Locale.ROOT, "距下轮还有 %02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    @SuppressWarnings("deprecation")
    private static void wakeDisplay(Context c) {
        PowerManager power = c.getSystemService(PowerManager.class);
        if (power.isInteractive()) return;
        try {
            if (displayWakeLock == null) {
                displayWakeLock = power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "delta:auto-cycle-display");
                displayWakeLock.setReferenceCounted(false);
            }
            // Some devices defer the configured wake activity until the display is already on.
            displayWakeLock.acquire(10_000L);
            Log.i("DeltaWorkshop", "Requested display wake for automatic cycle");
        } catch (SecurityException e) {
            Log.w("DeltaWorkshop", "Display wake request denied", e);
        }
    }

    @Override public void onReceive(Context c, Intent intent) {
        long deadline = intent.getLongExtra(DEADLINE, 0);
        synchronized (AutoCycle.class) {
            if (matchesDeadline(c, deadline) && deadline > SystemClock.elapsedRealtime()) {
                setAlarm(c, deadline);
                return;
            }
        }
        if (dueAccount(c, deadline).isEmpty()) return;
        if (hasSecureLock(c)) {
            pause(c, deadline, "手机已设安全锁，自动续造暂停");
            return;
        }
        if (ScriptRunner.running.get()) {
            pause(c, deadline, "已有任务在执行，检查制造后可重新计时");
            return;
        }
        WorkshopAccessibility service = WorkshopAccessibility.instance;
        if (service == null) {
            pause(c, deadline, "请开启「游戏操作」后重新检查制造");
            return;
        }
        try {
            wakeDisplay(c);
            service.startActivity(new Intent(c, AutoCycleActivity.class).putExtra(DEADLINE, deadline)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            pause(c, deadline, "未能自动唤醒，检查制造后重新计时");
        }
    }
}
