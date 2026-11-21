package com.xu.deltaworkshop;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Local metadata only; no model settings, chat messages or screen contents. */
final class RunDiagnostics implements AutoCloseable {
    private final Context context;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final long started=SystemClock.elapsedRealtime();
    private static volatile String operation="starting";
    private static volatile long operationAt;
    private static volatile int width,height;
    RunDiagnostics(Context c){context=c;mark("starting");timer.scheduleAtFixedRate(this::write,3,5,TimeUnit.SECONDS);}
    static void mark(String value){operation=value;operationAt=SystemClock.elapsedRealtime();}
    static void captured(int w,int h){width=w;height=h;mark("captured");}
    private void write(){
        try{
            JSONObject data=new JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-started).put("operation",operation)
                .put("operationAgeMs",SystemClock.elapsedRealtime()-operationAt).put("captureWidth",width).put("captureHeight",height);
            JSONArray threads=new JSONArray();
            for(var entry:Thread.getAllStackTraces().entrySet()) {
                String name=entry.getKey().getName();if(!name.equals("main")&&!name.equals("workshop-run"))continue;
                JSONArray stack=new JSONArray();for(StackTraceElement frame:entry.getValue())stack.put(frame.toString());
                threads.put(new JSONObject().put("name",name).put("state",entry.getKey().getState().toString()).put("stack",stack));
            }
            data.put("threads",threads);
            Files.write(new File(context.getFilesDir(),"run-diagnostics.json").toPath(),data.toString(2).getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }
    @Override public void close(){write();timer.shutdownNow();}
}
