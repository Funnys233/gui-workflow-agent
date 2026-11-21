package com.xu.deltaworkshop;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class BundledRulesTest {
    private String run(boolean wrongAccount) throws Exception {
        String module=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/assets/delta/delta-manufacture.js")),java.nio.charset.StandardCharsets.UTF_8);
        Context js=Context.enter();js.setOptimizationLevel(-1);js.setLanguageVersion(Context.VERSION_ES6);
        try{
            Scriptable scope=js.initStandardObjects();
            String fixture="""
                var module={exports:{}}, files={cwd:function(){return '/delta';},join:function(){return Array.prototype.join.call(arguments,'/');},exists:function(){return false;},read:function(){return JSON.stringify({schema:'delta-manufacture-plan-v3',complete:true,createdAt:Date.now(),account:'99999999999999999999',total:100,stations:[]});}};
                var items=[{label:'特勤处',bounds:{left:175,top:20,right:340,bottom:80}},
                  {label:'06:58:01',bounds:{left:403,top:502,right:580,bottom:544}},
                  {label:'07:58:02',bounds:{left:840,top:502,right:1010,bottom:544}},
                  {label:'07:58:03',bounds:{left:403,top:960,right:580,bottom:1002}},
                  {label:'07:58:04',bounds:{left:840,top:960,right:1010,bottom:1002}},
                  {label:'CN UID:100000000000000000001_20260101',bounds:{left:340,top:1180,right:990,bottom:1219}}];
                var ocr={mode:'mlkit',detect:function(f,o){if(!o)return items;var r=o.region;return items.filter(function(i){var b=i.bounds,x=(b.left+b.right)/2,y=(b.top+b.bottom)/2;return x>=r[0]&&x<r[0]+r[2]&&y>=r[1]&&y<r[1]+r[3];});}};
                function sleep(){}
                var frame={},tapCount=0,result={runId:'1000000000002',purchases:0,craftStarts:0};
                var stations=[{name:'技术中心',x:342,y:347,right:729},{name:'工作台',x:779,y:347,right:1165},{name:'制药台',x:342,y:805,right:729},{name:'防具台',x:779,y:805,right:1165}];
                var c={args:{},result:result,root:'/delta',out:'/delta/runs/test',stations:stations,snapshot:function(fn){return fn(frame);},overview:function(){return true;},state:function(){return 'busy_or_unknown';},tap:function(){tapCount++;throw Error('unexpected tap');},waitFor:function(fn){return fn(frame);},checkpoint:function(){},save:function(){}};
                """;
            js.evaluateString(scope,fixture,"fixture",1,null);js.evaluateString(scope,module,"bundled-manufacture",1,null);
            if(wrongAccount)js.evaluateString(scope,"c.args.approvedPlan='1000000000001';","approval",1,null);
            return Context.toString(js.evaluateString(scope,"try{module.exports.run(c);JSON.stringify({result:result,taps:tapCount});}catch(e){String(e);}","run",1,null));
        }finally{Context.exit();}
    }
    @Test public void nativeRhinoReadsLongUidAndSkipsAllRunningStations() throws Exception {
        org.json.JSONObject value=new org.json.JSONObject(run(false));
        assertEquals(0,value.getInt("taps"));
        org.json.JSONArray states=value.getJSONObject("result").getJSONArray("manufacturing");assertEquals(4,states.length());
        for(int i=0;i<4;i++)assertEquals("already_running",states.getJSONObject(i).getString("status"));
    }
    @Test public void differentAccountStopsBeforeAnyPurchaseOrTap() throws Exception {
        assertTrue(run(true).contains("APPROVAL_ACCOUNT_MISMATCH"));
    }
}
