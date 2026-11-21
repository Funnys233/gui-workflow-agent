package com.xu.deltaworkshop;

import android.content.Context;
import org.json.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** AI receives only four named workshop actions, never scripts, taps or budget settings. */
final class AiClient {
    record Reply(String text,String action){}
    static final String[] ACTIONS={"cycle","check","harvest","quote"};
    static String allowedAction(String name,String args) throws Exception {
        if(!"workshop_action".equals(name))throw new IllegalArgumentException("模型请求了不支持的操作");
        JSONObject value=new JSONObject(args);
        if(value.length()!=1)throw new IllegalArgumentException("模型返回的操作参数不正确");
        String action=value.getString("action");
        for(String permitted:ACTIONS)if(permitted.equals(action))return action;
        throw new IllegalArgumentException("模型请求了不支持的操作");
    }
    static Reply send(Context c,JSONArray history) throws Exception {
        ModelSettings settings=new ModelSettings(c);
        if(!settings.configured())throw new IllegalStateException("先在设置里选择模型并填写密钥；按钮操作无需模型。");
        JSONArray messages=new JSONArray();
        String prompt="你是中文三角洲制造助手。用户通过你启动手机本地固定流程。只在用户要求操作时调用workshop_action。cycle收获并续造四台首件置顶道具；check只检查；harvest只收获；quote刷新补料报价。不能传入购买授权、坐标、脚本、账号或预算。用户明确要求收获并续造时，应用按用户在制造页保存的各台上限自动补料。只检查、只收获和查询报价均不购买。不能称操作完成，直到本地结果已确认。仓库不足、下线、未知画面会停止。不提供已实现范围之外的能力。游戏需用户先登录解锁。已制造的台跳过。用简短中文回答，若用户在问问题，不调用操作。最近保存的制造记录（不是实时状态）：";
        messages.put(new JSONObject().put("role","system").put("content",prompt+ResultText.modelResult(ScriptRunner.readLatest(c))));
        for(int i=Math.max(0,history.length()-20);i<history.length();i++) {
            JSONObject entry=history.getJSONObject(i);
            String role=entry.optString("role"),text=entry.optString("content");
            if((role.equals("user")||role.equals("assistant"))&&!text.isBlank())messages.put(new JSONObject().put("role",role).put("content",text));
        }
        JSONObject params=new JSONObject().put("type","object").put("properties",new JSONObject().put("action",new JSONObject().put("type","string").put("enum",new JSONArray(ACTIONS))))
            .put("required",new JSONArray().put("action")).put("additionalProperties",false);
        JSONObject function=new JSONObject().put("name","workshop_action").put("description","执行手机内置的三角洲制造流程，一次请求只调用一次。补料额度由本机设置决定。").put("parameters",params);
        JSONObject body=new JSONObject().put("model",settings.model()).put("messages",messages).put("stream",false)
            .put("max_tokens",1200).put("tools",new JSONArray().put(new JSONObject().put("type","function").put("function",function)));
        String endpoint=settings.endpoint();if(!endpoint.endsWith("/chat/completions"))endpoint+="/chat/completions";
        HttpsURLConnection connection=(HttpsURLConnection)new URL(endpoint).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setConnectTimeout(15000);connection.setReadTimeout(75000);connection.setDoOutput(true);
        connection.setRequestProperty("Authorization","Bearer "+settings.secret());connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
        try {
            try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=connection.getResponseCode();
            if(code!=200)throw new IOException(code==401?"模型密钥无效，请在设置中检查。":code==429?"模型服务暂时限流或额度不足，请稍后再试。":"模型服务返回错误（"+code+"），请检查服务地址和模型名称。");
            String response=readBounded(connection.getInputStream());
            JSONObject message=new JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            JSONArray calls=message.optJSONArray("tool_calls");String content=message.optString("content","");
            if(content.equals("null"))content="";
            if(calls!=null&&calls.length()>0){
                if(calls.length()!=1)throw new IllegalStateException("模型返回了多项操作，请每次只执行一个任务。");
                JSONObject call=calls.getJSONObject(0).getJSONObject("function");
                return new Reply(content,allowedAction(call.getString("name"),call.getString("arguments")));
            }
            if(content.isBlank())throw new IllegalStateException("模型没有返回有效内容，请重试或使用按钮。");
            return new Reply(content,null);
        } finally{connection.disconnect();}
    }
    private static String readBounded(InputStream input) throws IOException {
        try(InputStream in=input; ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1){if(out.size()+count>512000)throw new IOException("模型响应过长");out.write(buffer,0,count);}
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
