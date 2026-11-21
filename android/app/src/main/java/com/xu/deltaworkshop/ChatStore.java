package com.xu.deltaworkshop;

import android.content.Context;
import org.json.*;

final class ChatStore {
    static JSONArray load(Context c){try{return new JSONArray(c.getSharedPreferences("chat",0).getString("history","[]"));}catch(Exception e){return new JSONArray();}}
    static synchronized void add(Context c,String role,String text){
        try{
            JSONArray history=load(c);history.put(new JSONObject().put("role",role).put("content",text));
            JSONArray kept=new JSONArray();for(int i=Math.max(0,history.length()-60);i<history.length();i++)kept.put(history.get(i));
            c.getSharedPreferences("chat",0).edit().putString("history",kept.toString()).apply();
        }catch(Exception ignored){}
    }
}
