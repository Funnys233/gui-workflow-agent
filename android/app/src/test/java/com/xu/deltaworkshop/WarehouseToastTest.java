package com.xu.deltaworkshop;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class WarehouseToastTest {
    private boolean recognizes(boolean displayToast,boolean legacy) throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/assets/delta/delta-workshop.js")),StandardCharsets.UTF_8);
        int start=source.indexOf("    function match("),end=source.indexOf("    function overview(",start);
        assertTrue(start>=0&&end>start);
        Context js=Context.enter();js.setOptimizationLevel(-1);Scriptable scope=js.initStandardObjects();
        try{
            String setup="""
                var java=Packages.java;
                var width=61,height=25,shown=DISPLAY_TOAST;
                var frame={};
                function pixel(x,y){
                    return x>=0 && x<width && y>=0 && y<height && (x%9<4 || y%9<3) ? 0xf0f0f0 : 0x151515;
                }
                var result={calibratedLayout:false};
                var samples={'warehouse-full':[]},origins={'warehouse-full':[1255,362]};
                for(var y=2;y<height-2;y+=3)for(var x=2;x<width-2;x+=3){
                    var c=pixel(x,y),r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
                    if(Math.min(r,g,b)>=180&&Math.max(r,g,b)-Math.min(r,g,b)<60)samples['warehouse-full'].push([x,y]);
                }
                var images={pixel:function(f,x,y){
                    return shown ? pixel(x-1255,y-(LEGACY ? 362 : 335)) : 0x151515;
                }};
                """.replace("DISPLAY_TOAST",String.valueOf(displayToast)).replace("LEGACY",String.valueOf(legacy));
            js.evaluateString(scope,setup,"synthetic-toast-fixture",1,null);
            js.evaluateString(scope,source.substring(start,end),"actual-runner-matcher",1,null);
            return Context.toBoolean(js.evaluateString(scope,"match(frame,'warehouse-full',[1235,320,260,105]);","check",1,null));
        }finally{Context.exit();}
    }
    @Test public void recognizesShortToastAtItsCurrentPosition() throws Exception {assertTrue(recognizes(true,false));}
    @Test public void ordinaryOverviewDoesNotBecomeWarehouseError() throws Exception {assertFalse(recognizes(false,false));}
    @Test public void existingDialogPositionStillRecognized() throws Exception {assertTrue(recognizes(true,true));}
}
