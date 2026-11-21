package com.xu.deltaworkshop;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;

public class RunService extends Service {
    private PowerManager.WakeLock wakeLock;
    private static final int NOTICE=10;
    static boolean shouldCloseAndLock(String mode, JSONObject result) {
        return "cycle".equals(mode) && result != null && result.optBoolean("ok")
            && !result.optBoolean("unresolvedPurchase") && !result.optBoolean("needsApproval");
    }
    public static void start(Context c,String mode) {
        start(c,mode,null);
    }
    static void start(Context c,String mode,String expectedAccount) {
        Intent i=new Intent(c,RunService.class).putExtra("mode",mode).putExtra("expectedAccount",expectedAccount);
        c.startForegroundService(i);
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("stop".equals(intent.getAction())){ScriptRunner.requestStop();return START_NOT_STICKY;}
        if(!ScriptRunner.running.compareAndSet(false,true))return START_NOT_STICKY;
        ScriptRunner.cancelled.set(false);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("workshop","制造任务",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RunService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(NOTICE,new Notification.Builder(this,"workshop").setSmallIcon(R.drawable.ic_launcher).setContentTitle("制造任务执行中")
            .setContentText("正在核对游戏画面，点此返回助手").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止",stop).build()).build());
        wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"delta:workshop");wakeLock.acquire(300000);
        String mode=intent.getStringExtra("mode");
        JSONObject previous=ScriptRunner.readLatest(this);
        WorkshopAccessibility accessibility=WorkshopAccessibility.instance;
        if(accessibility!=null)accessibility.showTaskOverlay("check".equals(mode)?"正在检查制造":"制造助手执行中");
        new Thread(()->{
            try (RunDiagnostics diagnostics=new RunDiagnostics(this)) {
                JSONObject result=ScriptRunner.execute(this,mode,intent.getStringExtra("expectedAccount"));
                if(result.optString("error").contains("REPRICE_PENDING") && previous!=null && previous.optBoolean("unresolvedPurchase"))
                    result=previous;
                ScriptRunner.update(this,result.toString());
            }
            catch(Throwable e){
                try {ScriptRunner.update(this,new JSONObject().put("schema","delta-cycle-v1").put("runId",String.valueOf(System.currentTimeMillis())).put("ok",false).put("phase","failed").put("error",String.valueOf(e)).toString());}catch(Exception ignored){}
            } finally {
                JSONObject result=ScriptRunner.readLatest(this);
                if("cycle".equals(mode)||"check".equals(mode))AutoCycle.updateFromResult(this,result);
                ScriptRunner.running.set(false);
                if(WorkshopAccessibility.instance!=null)WorkshopAccessibility.instance.hideTaskOverlay();
                sendBroadcast(new Intent(ScriptRunner.UPDATE).setPackage(getPackageName()));
                if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
                stopForeground(STOP_FOREGROUND_REMOVE);
                manager.notify(NOTICE,new Notification.Builder(this,"workshop").setSmallIcon(R.drawable.ic_launcher).setContentTitle("制造任务已有结果")
                    .setContentText(ResultText.headline(result)).setContentIntent(open).setAutoCancel(true).build());
                if (shouldCloseAndLock(mode, result)) {
                    WorkshopAccessibility service=WorkshopAccessibility.instance;
                    if (service!=null) service.closeGameAndLock();
                } else {
                    Intent back=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    if(result==null || !(result.optBoolean("unresolvedPurchase") || result.optString("error").contains("REPRICE_PENDING"))) {
                        try {WorkshopAccessibility service=WorkshopAccessibility.instance;if(service!=null)service.startActivity(back);}catch(Exception ignored){}
                    }
                }
                stopSelf();
            }
        },"workshop-run").start();
        return START_NOT_STICKY;
    }
}
