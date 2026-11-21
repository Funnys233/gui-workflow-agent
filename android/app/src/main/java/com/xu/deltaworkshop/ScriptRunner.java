package com.xu.deltaworkshop;

import android.content.Context;
import org.json.JSONObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScriptRunner {
    static final AtomicBoolean running=new AtomicBoolean(false), cancelled=new AtomicBoolean(false);
    static volatile JSONObject latest;
    static final String UPDATE="com.xu.deltaworkshop.RESULT_CHANGED";
    static final long MAX_PURCHASE_CAP=999_999_999L;
    static long purchaseCap(Context context,String station) {
        return context.getSharedPreferences("workshop",Context.MODE_PRIVATE).getLong("cap."+station,0);
    }
    static void savePurchaseCaps(Context context,long[] caps) {
        var editor=context.getSharedPreferences("workshop",Context.MODE_PRIVATE).edit();
        for(int i=0;i<MainActivity.STATIONS.length;i++) {
            if(caps[i]<0 || caps[i]>MAX_PURCHASE_CAP) throw new IllegalArgumentException("PURCHASE_BUDGET_INVALID");
            editor.putLong("cap."+MainActivity.STATIONS[i],caps[i]);
        }
        editor.apply();
    }
    static void requestStop(){ cancelled.set(true); }
    static void update(Context c,String json) {
        try {
            JSONObject r=new JSONObject(json); latest=r;
            Files.write(new File(c.getFilesDir(),"latest-result.json").toPath(),json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            c.sendBroadcast(new android.content.Intent(UPDATE).setPackage(c.getPackageName()));
        } catch(Exception e){android.util.Log.w("DeltaWorkshop","Result checkpoint failed",e);}
    }
    static JSONObject readLatest(Context c) {
        if(latest!=null)return latest;
        try {latest=new JSONObject(new String(Files.readAllBytes(new File(c.getFilesDir(),"latest-result.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));} catch(Exception ignored){}
        return latest;
    }
    static JSONObject execute(Context c,String mode,String expectedAccount) throws Exception {
        JSONObject args=new JSONObject().put("manufacture",true).put("observeTransientPrompts",true)
            .put("detectPendingPriceChange",true);
        if(expectedAccount!=null)args.put("expectedAccount",expectedAccount);
        switch(mode) {
            case "cycle":
                JSONObject caps=new JSONObject();
                for(String station:MainActivity.STATIONS) caps.put(station,purchaseCap(c,station));
                args.put("stationCaps",caps);
                break;
            case "check": args.put("inspectOnly",true).put("timersOnly",AutoCycle.enabled(c));break;
            case "harvest":args.put("manufacture",false);break;
            case "quote":args.put("quoteOnly",true).put("previewOnly",true);break;
            default:throw new IllegalArgumentException("UNKNOWN_ACTION");
        }
        org.mozilla.javascript.Context js=org.mozilla.javascript.Context.enter();
        js.setOptimizationLevel(-1);js.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
        try(RuntimeHost host=new RuntimeHost(c,mode)) {
            Scriptable scope=js.initStandardObjects();
            ScriptableObject.putProperty(scope,"host",org.mozilla.javascript.Context.javaToJS(host,scope));
            String bootstrap;
            try(java.io.InputStream input=c.getAssets().open("bootstrap.js");java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream()){
                byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)buffer.write(bytes,0,count);
                bootstrap=buffer.toString(java.nio.charset.StandardCharsets.UTF_8.name());
            }
            js.evaluateString(scope,bootstrap,"bootstrap.js",1,null);
            String code="JSON.stringify(require(files.join(files.cwd(),'delta-workshop.js'))("+args+",files.cwd()))";
            String value=org.mozilla.javascript.Context.toString(js.evaluateString(scope,code,"run-workshop",1,null));
            JSONObject result=new JSONObject(value);
            host.saveCalibration(result);
            return result;
        } finally { org.mozilla.javascript.Context.exit(); }
    }
}
