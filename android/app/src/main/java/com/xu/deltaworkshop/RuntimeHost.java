package com.xu.deltaworkshop;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.PowerManager;
import android.os.SystemClock;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** Narrow in-process host for the existing, bundled workshop rules. No remote script API. */
public final class RuntimeHost implements AutoCloseable {
    private static final String GAME = "com.tencent.tmgp.dfm";
    private static final int REFERENCE_WIDTH = WorkshopLayout.WIDTH;
    private static final int REFERENCE_HEIGHT = WorkshopLayout.HEIGHT;
    private static final int STATION_RIGHT_START = 2100;
    private final Context context;
    private final File root;
    private final com.google.mlkit.vision.text.TextRecognizer recognizer;
    private final String mode;
    private boolean calibratedLayout;
    private boolean cycleLayoutVerified;
    private WorkshopLayout.Transform calibration;
    private WorkshopLayout.Transform captureTransform;
    private WorkshopLayout.Transform rightTransform;
    private String calibrationKey;
    private int captureWidth;
    private int captureHeight;
    public RuntimeHost(Context c, String mode) throws Exception {
        context = c.getApplicationContext(); root = new File(context.getFilesDir(), "delta");
        this.mode = mode;
        installAssets("delta", root);
        recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    }
    public Context getContext() { return context; }
    public String root() { return root.getAbsolutePath(); }
    public void check() {
        if (ScriptRunner.cancelled.get()) throw new IllegalStateException("USER_STOPPED");
        if (WorkshopAccessibility.instance == null) throw new IllegalStateException("ACCESSIBILITY_NOT_RUNNING");
    }
    public void sleep(long ms) throws Exception {
        long end = android.os.SystemClock.uptimeMillis() + ms;
        while (android.os.SystemClock.uptimeMillis() < end) {
            check(); Thread.sleep(Math.min(100, Math.max(1, end - android.os.SystemClock.uptimeMillis())));
        }
    }
    public boolean screenOn() { return ((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive(); }
    public boolean unlocked() { return !((android.app.KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked(); }
    private Bitmap takeFrame() throws Exception {
        check(); RunDiagnostics.mark("capture_wait");
        Bitmap frame=WorkshopAccessibility.instance.capture();
        RunDiagnostics.captured(frame.getWidth(),frame.getHeight());
        return frame;
    }
    public Bitmap capture(String layout) throws Exception {
        Bitmap frame=takeFrame();
        int width=frame.getWidth(), height=frame.getHeight();
        captureTransform=null;
        rightTransform=null;
        if (calibration!=null && !calibrationKey.equals(width+"x"+height)) {
            frame.recycle();
            throw new IllegalStateException("LAYOUT_CHANGED");
        }
        if (width<=height || height<540 || width==REFERENCE_WIDTH && height==REFERENCE_HEIGHT) return frame;
        calibratedLayout=true;
        if (calibration==null) return frame;
        try {
            captureTransform="center".equals(layout)?calibration.centered(width):calibration;
            captureWidth=width;captureHeight=height;
            Bitmap normalized=Bitmap.createBitmap(REFERENCE_WIDTH,REFERENCE_HEIGHT,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(normalized);
            Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
            drawNormalized(canvas,frame,paint,captureTransform);
            if ("station".equals(layout)) {
                rightTransform=calibration.rightAligned(width);
                canvas.clipRect(STATION_RIGHT_START,0,REFERENCE_WIDTH,REFERENCE_HEIGHT);
                drawNormalized(canvas,frame,paint,rightTransform);
            }
            return normalized;
        } finally {
            frame.recycle();
        }
    }
    private static void drawNormalized(Canvas canvas,Bitmap frame,Paint paint,WorkshopLayout.Transform transform) {
        double x=transform.x(),y=transform.y(),sx=transform.sx(),sy=transform.sy();
        canvas.drawBitmap(frame,null,new RectF((float)(-x/sx),(float)(-y/sy),
            (float)((frame.getWidth()-x)/sx),(float)((frame.getHeight()-y)/sy)),paint);
    }
    public boolean calibratedLayout() { return calibratedLayout; }
    public boolean cycleLayoutVerified() { return cycleLayoutVerified; }
    private SharedPreferences calibrationStore() {
        return context.getSharedPreferences("workshop-layout-v1",Context.MODE_PRIVATE);
    }
    void saveCalibration(JSONObject result) throws org.json.JSONException {
        if (calibration==null || !result.optBoolean("ok")) return;
        SharedPreferences.Editor edit=calibrationStore().edit().putString(calibrationKey,calibration.encode());
        if ("quote".equals(mode)) {
            if (verifiedRecipe(result)) {
                edit.putString("cycle:"+calibrationKey,calibration.encode());
                cycleLayoutVerified=true;
            }
            result.put("layoutVerified",cycleLayoutVerified);
        }
        edit.apply();
    }
    static boolean verifiedRecipe(JSONObject result) {
        JSONArray stations=result.optJSONArray("manufacturing");
        if (!result.optBoolean("ok") || stations==null || stations.length()!=MainActivity.STATIONS.length) return false;
        boolean verified=false;
        for (int i=0;i<stations.length();i++) {
            JSONObject station=stations.optJSONObject(i);
            if (station==null || !MainActivity.STATIONS[i].equals(station.optString("name"))) return false;
            String status=station.optString("status");
            if ("materials_ready".equals(status) || "quoted".equals(status) && station.optLong("quote")>0) verified=true;
        }
        return verified;
    }
    private static int[] pixels(Bitmap bitmap) {
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        int[] values=new int[width*height];
        bitmap.getPixels(values,0,width,0,0,width,height);
        return values;
    }
    private WorkshopLayout.Pattern pattern(String name,int x,int y) {
        Bitmap bitmap=BitmapFactory.decodeFile(new File(root,"assets/"+name+".png").getPath());
        if(bitmap==null)throw new IllegalStateException("TEMPLATE_MISSING: "+name);
        try {return new WorkshopLayout.Pattern(x,y,bitmap.getWidth(),bitmap.getHeight(),pixels(bitmap));}
        finally {bitmap.recycle();}
    }
    public String prepareOverview(long timeout) throws Exception {
        if(!calibratedLayout)throw new IllegalStateException("LAYOUT_CHECK_ONLY");
        WorkshopLayout.Pattern[] anchors={pattern("overview",175,20),pattern("technical-header",434,237),
            pattern("workbench-header",870,237),pattern("medical-header",434,695),pattern("armor-header",870,695)};
        WorkshopLayout.Pattern lobby=pattern("lobby-workshop",993,1105),modePicker=pattern("mode-selection",114,235);
        WorkshopLayout.Pattern promotionClose=pattern("promotion-close",2470,58);
        JSONArray actions=new JSONArray();
        boolean selectedMode=false,openedLobby=false;
        int promotions=0;
        long end=SystemClock.elapsedRealtime()+timeout;
        do {
            if(GAME.equals(currentPackage())) {
                Bitmap bitmap=takeFrame();
                try {
                    int w=bitmap.getWidth(),h=bitmap.getHeight();
                    if(w>h && h>=540) {
                        WorkshopLayout.Frame frame=new WorkshopLayout.Frame(w,h,pixels(bitmap));
                        String key=w+"x"+h;
                        SharedPreferences savedLayout=calibrationStore();
                        WorkshopLayout.Transform saved=WorkshopLayout.Transform.fromString(savedLayout.getString(key,""));
                        boolean reused=WorkshopLayout.valid(frame,anchors,saved);
                        WorkshopLayout.Transform fitted=reused?saved:WorkshopLayout.calibrate(frame,anchors);
                        if(fitted!=null) {
                            calibration=fitted;calibrationKey=key;
                            WorkshopLayout.Transform verified=WorkshopLayout.Transform.fromString(savedLayout.getString("cycle:"+key,""));
                            cycleLayoutVerified=fitted.equals(verified) || WorkshopLayout.valid(frame,anchors,verified);
                            if ("cycle".equals(mode) && !cycleLayoutVerified) throw new IllegalStateException("LAYOUT_CYCLE_UNVERIFIED");
                            android.util.Log.i("DeltaWorkshop","Workshop layout "+key+(reused?" reused":" calibrated"));
                            return actions.toString();
                        }
                        double scale=frame.scale();
                        WorkshopLayout.Mark option=openedLobby?null:WorkshopLayout.locate(frame,lobby,
                            w/2.0+(lobby.x()-REFERENCE_WIDTH/2.0)*scale,frame.top()+lobby.y()*scale,130,46);
                        if(option!=null) {
                            navigate((int)Math.round(option.x()+54*option.scale()),(int)Math.round(option.y()+26*option.scale()));
                            openedLobby=true;actions.put("open_workshop");
                        } else if(!selectedMode && !openedLobby && WorkshopLayout.locate(frame,modePicker,
                            frame.left()+modePicker.x()*scale,frame.top()+modePicker.y()*scale,110,65)!=null && enterOperations(bitmap,frame.top())) {
                            selectedMode=true;actions.put("enter_operations_lobby");
                        } else {
                            WorkshopLayout.Mark close=promotionClose(bitmap,frame,promotionClose);
                            if (close!=null) {
                                if (promotions++>=3) throw new IllegalStateException("TOO_MANY_POPUPS");
                                navigate((int)Math.round(close.x()+28*close.scale()),(int)Math.round(close.y()+23*close.scale()));
                                actions.put("close_promotion_popup");
                            }
                        }
                    }
                } finally {bitmap.recycle();}
            }
            sleep(450);
        } while(SystemClock.elapsedRealtime()<end);
        throw new IllegalStateException("OPEN_WORKSHOP_FOR_LAYOUT_CHECK");
    }
    private WorkshopLayout.Mark promotionClose(Bitmap bitmap,WorkshopLayout.Frame frame,WorkshopLayout.Pattern pattern) throws Exception {
        double scale=frame.scale();
        WorkshopLayout.Mark mark=WorkshopLayout.locate(frame,pattern,
            frame.width()-(REFERENCE_WIDTH-pattern.x())*scale,frame.top()+pattern.y()*scale,110,42);
        if (mark==null) return null;
        int x=Math.max(0,(int)Math.round(frame.width()/2.0+(1630-REFERENCE_WIDTH/2.0)*scale));
        int y=(int)Math.round(frame.top()+920*scale);
        int width=Math.min(frame.width()-x,(int)Math.round(620*scale));
        int height=Math.min(frame.height()-y,(int)Math.round(145*scale));
        if (width<=0 || height<=0) return null;
        Bitmap button=Bitmap.createBitmap(bitmap,x,y,width,height);
        try {
            for (Text.TextBlock block:readText(button).getTextBlocks()) for (Text.Line line:block.getLines()) {
                if ("前往获取".equals(line.getText().replaceAll("\\s+",""))) return mark;
            }
            return null;
        } finally { button.recycle(); }
    }
    private boolean enterOperations(Bitmap bitmap,int top) throws Exception {
        for(Text.TextBlock block:readText(bitmap).getTextBlocks()) for(Text.Line line:block.getLines()) {
            android.graphics.Rect bounds=line.getBoundingBox();
            if(bounds!=null && bounds.centerX()<bitmap.getWidth()/3 && bounds.centerY()>top+(bitmap.getHeight()-2*top)*3/4
                    && line.getText().replaceAll("\\s+","").startsWith("前往游玩")) {
                navigate(bounds.centerX(),bounds.centerY());
                return true;
            }
        }
        return false;
    }
    private void navigate(int x,int y) throws Exception {
        check();
        if(!WorkshopAccessibility.instance.tap(x,y,100))throw new IllegalStateException("TAP_FAILED");
        sleep(450);
    }
    public Bitmap copy(Bitmap b) { return b.copy(Bitmap.Config.ARGB_8888, false); }
    public int pixel(Bitmap b, int x, int y) { return b.getPixel(x,y); }
    public void save(Bitmap b, String p) throws Exception { try (FileOutputStream out = new FileOutputStream(safe(p))) { b.compress(Bitmap.CompressFormat.PNG,100,out); } }
    public String read(String p) throws Exception { return new String(Files.readAllBytes(safe(p).toPath()), StandardCharsets.UTF_8); }
    public boolean exists(String p) throws Exception { return safe(p).exists(); }
    public void ensure(String p) throws Exception { File d=safe(p); if (!d.isDirectory() && !d.mkdirs()) throw new IllegalStateException("CANNOT_CREATE_DIRECTORY"); }
    public void write(String p, String value) throws Exception {
        File f=safe(p); File parent=f.getParentFile(); if(parent != null && !parent.isDirectory()) parent.mkdirs();
        File tmp=new File(f.getPath()+".tmp"); Files.write(tmp.toPath(),value.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(),f.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if(f.getName().equals("result.json")) ScriptRunner.update(context,value);
    }
    private File safe(String p) throws Exception {
        File file=new File(p).getCanonicalFile(); String base=root.getCanonicalPath();
        if (!file.getPath().equals(base) && !file.getPath().startsWith(base+File.separator)) throw new SecurityException("PATH_OUTSIDE_WORKSHOP");
        return file;
    }
    public String currentPackage() { check(); return WorkshopAccessibility.instance.foregroundPackage(); }
    public boolean launch(String pkg) {
        check(); if(!GAME.equals(pkg)) throw new SecurityException("PACKAGE_NOT_ALLOWED");
        Intent intent=context.getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); WorkshopAccessibility.instance.startActivity(intent); return true;
    }
    public boolean press(int x,int y,long duration) throws Exception {
        check(); RunDiagnostics.mark("tap_"+x+"_"+y);
        if (calibratedLayout) {
            if ("check".equals(mode) || captureTransform==null) throw new IllegalStateException("LAYOUT_CHECK_ONLY");
            if ("cycle".equals(mode) && !cycleLayoutVerified) throw new IllegalStateException("LAYOUT_CYCLE_UNVERIFIED");
            WorkshopLayout.Transform transform=rightTransform!=null && x>=STATION_RIGHT_START?rightTransform:captureTransform;
            x=transform.mapX(x);y=transform.mapY(y);
            if (x<0 || y<0 || x>=captureWidth || y>=captureHeight) throw new IllegalStateException("LAYOUT_CHANGED");
        }
        boolean completed=WorkshopAccessibility.instance.tap(x,y,duration);
        return completed;
    }
    public String detect(Bitmap frame,int x,int y,int w,int h) throws Exception {
        check(); RunDiagnostics.mark("ocr_wait"); Bitmap cropped=null;
        try {
            Bitmap source=frame;
            if(w>0 && h>0) { cropped=Bitmap.createBitmap(frame,x,y,w,h); source=cropped; } else { x=0; y=0; }
            Text text=readText(source);
            JSONArray items=new JSONArray();
            for(Text.TextBlock block:text.getTextBlocks()) for(Text.Line line:block.getLines()) {
                android.graphics.Rect box=line.getBoundingBox(); if(box==null) continue;
                JSONObject item=new JSONObject(); item.put("label",line.getText());
                item.put("bounds",new JSONObject().put("left",box.left+x).put("top",box.top+y).put("right",box.right+x).put("bottom",box.bottom+y)); items.put(item);
            }
            return items.toString();
        } finally { if(cropped!=null) cropped.recycle(); RunDiagnostics.mark("ocr_finished"); }
    }
    private Text readText(Bitmap frame) throws Exception {
        return Tasks.await(recognizer.process(InputImage.fromBitmap(frame,0)),15,TimeUnit.SECONDS);
    }
    public void log(String s) { android.util.Log.d("DeltaWorkshop",s); }
    private void installAssets(String path, File dest) throws Exception {
        String[] children=context.getAssets().list(path);
        if(children==null) throw new IllegalStateException("BUNDLED_RULES_MISSING");
        if(children.length==0) {
            File parent=dest.getParentFile(); if(parent!=null)parent.mkdirs();
            try(java.io.InputStream in=context.getAssets().open(path); FileOutputStream out=new FileOutputStream(dest)){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
        } else {
            dest.mkdirs(); for(String name:children) installAssets(path+"/"+name,new File(dest,name));
        }
    }
    @Override public void close() { recognizer.close(); }
}
