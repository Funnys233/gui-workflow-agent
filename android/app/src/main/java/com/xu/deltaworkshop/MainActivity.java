package com.xu.deltaworkshop;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    static final String[] STATIONS={"技术中心","工作台","制药台","防具台"};
    private LinearLayout shell,body;
    private int page=1;
    private boolean modelBusy=false,resumed=false;
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextInputEditText composer;
    private TextView timerStatus;
    private final Runnable refreshTimer=new Runnable(){@Override public void run(){
        if(!resumed || timerStatus==null)return;
        timerStatus.setText(AutoCycle.status(MainActivity.this));
        if(AutoCycle.enabled(MainActivity.this))main.postDelayed(this,1000);
    }};
    private final androidx.activity.result.ActivityResultLauncher<Intent> alarmSettings=
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
            if(AutoCycle.hasAlarmPermission(this))setAutoCycle(true);
            else render();
        });
    private String draft="";
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){recordChatResult();render();}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null){page=state.getInt("page",1);draft=state.getString("draft","");}
        androidx.core.content.ContextCompat.registerReceiver(this,receiver,new IntentFilter(ScriptRunner.UPDATE),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        render();
    }
    @Override protected void onResume(){super.onResume();resumed=true;recordChatResult();render();}
    @Override protected void onPause(){resumed=false;main.removeCallbacks(refreshTimer);super.onPause();}
    @Override protected void onDestroy(){unregisterReceiver(receiver);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("page",page);out.putString("draft",composer==null?draft:String.valueOf(composer.getText()));super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);recordChatResult();render();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private int color(int id){return getColor(id);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color(R.color.ink));t.setLineSpacing(dp(3),1);
        if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private void addText(LinearLayout target,String value,int size,boolean bold,int top){TextView t=text(value,size,bold);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);target.addView(t,p);}
    private MaterialButton button(String value,boolean primary,Runnable action){
        MaterialButton b=new MaterialButton(this,null,primary?com.google.android.material.R.attr.materialButtonStyle:com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(value);b.setAllCaps(false);b.setMinHeight(dp(52));b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);b.setLayoutParams(p);return b;
    }
    private void divider(LinearLayout target){View line=new View(this);line.setBackgroundColor(color(R.color.surface_variant));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(8);p.bottomMargin=dp(8);target.addView(line,p);}
    private void render(){
        if(isFinishing()||isDestroyed())return;
        main.removeCallbacks(refreshTimer);timerStatus=null;
        if(composer!=null){draft=String.valueOf(composer.getText());composer=null;}
        shell=column();shell.setBackgroundColor(color(R.color.surface));
        boolean light=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)!=android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat controller=new androidx.core.view.WindowInsetsControllerCompat(getWindow(),getWindow().getDecorView());controller.setAppearanceLightStatusBars(light);controller.setAppearanceLightNavigationBars(light);
        ViewCompat.setOnApplyWindowInsetsListener(shell,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        MaterialToolbar toolbar=new MaterialToolbar(this);toolbar.setTitle(page==2?"和助手聊聊":"制造助手");toolbar.setTitleTextColor(color(R.color.ink));
        toolbar.getMenu().add("设置").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);toolbar.setOnMenuItemClickListener(item->{settings();return true;});shell.addView(toolbar);
        body=column();shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        if(page==2)chatPage();else workshopPage();
        BottomNavigationView nav=new BottomNavigationView(this);nav.setBackgroundColor(color(R.color.surface_variant));nav.getMenu().add(0,1,0,"制造").setIcon(R.drawable.ic_workshop);nav.getMenu().add(0,2,1,"聊天").setIcon(R.drawable.ic_chat);
        nav.setSelectedItemId(page);nav.setOnItemSelectedListener(item->{if(page!=item.getItemId()){page=item.getItemId();render();}return true;});shell.addView(nav);
        setContentView(shell);
        refreshTimer.run();
    }
    private void workshopPage(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout content=column();content.setPadding(dp(24),dp(8),dp(24),dp(24));scroll.addView(content);
        boolean ready=WorkshopAccessibility.instance!=null;
        if(!ready){
            addText(content,"先开启游戏操作",22,true,4);
            addText(content,"助手需要读取游戏画面并执行点击。只需在系统设置里开启一次。",15,false,8);
            content.addView(button("去开启游戏操作",true,this::permissions));divider(content);
        }
        JSONObject r=ScriptRunner.readLatest(this);
        addText(content,ScriptRunner.running.get()?"任务执行中":"四台制造状态",22,true,12);
        String checked="尚未检查。这些状态会在检查后更新。";
        if(r!=null)try{checked="最近核对 "+new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(Long.parseLong(r.optString("runId"))))+" · 以下为当时记录";}catch(Exception ignored){}
        TextView time=text(checked,12,false);time.setTextColor(color(R.color.secondary_ink));addTextView(content,time,8);
        for(String name:STATIONS){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(14),0,dp(14));
            TextView station=text(name,16,true);row.addView(station,new LinearLayout.LayoutParams(dp(92),-2));
            TextView state=text(ResultText.station(r,name),14,false);state.setGravity(Gravity.END);state.setTextColor(color(R.color.secondary_ink));row.addView(state,new LinearLayout.LayoutParams(0,-2,1));content.addView(row);
        }
        divider(content);
        MaterialSwitch autoCycle=new MaterialSwitch(this);
        autoCycle.setText("自动续造");autoCycle.setMinHeight(dp(48));
        autoCycle.setChecked(AutoCycle.enabled(this));
        content.addView(autoCycle,new LinearLayout.LayoutParams(-1,-2));
        timerStatus=text(AutoCycle.status(this),14,false);
        timerStatus.setTextColor(color(R.color.secondary_ink));
        addTextView(content,timerStatus,0);
        autoCycle.setOnCheckedChangeListener((view,enabled)->setAutoCycle(enabled));
        divider(content);
        MaterialButton budget=button("设置各台补料上限",false,this::purchaseSettings);
        budget.setEnabled(!ScriptRunner.running.get());content.addView(budget);
        MaterialButton cycle=button(ScriptRunner.running.get()?"正在执行，请稍候":"收获并续造",true,()->startTask("cycle",false));
        cycle.setEnabled(!ScriptRunner.running.get());content.addView(cycle);
        MaterialButton check=button("检查制造",false,()->startTask("check",false));check.setEnabled(!ScriptRunner.running.get());content.addView(check);
        LinearLayout secondary=new LinearLayout(this);
        MaterialButton harvest=button("只收获",false,()->startTask("harvest",false)),quote=button("查询补料",false,()->startTask("quote",false));
        secondary.addView(harvest,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,-2,1);qp.leftMargin=dp(12);secondary.addView(quote,qp);
        harvest.setEnabled(!ScriptRunner.running.get());quote.setEnabled(!ScriptRunner.running.get());content.addView(secondary);
        if(ScriptRunner.running.get())content.addView(button("停止本次任务",false,()->{ScriptRunner.requestStop();toast("正在停止；已完成的购买或开工会保留记录。");}));
        addText(content,"最近一次结果",18,true,24);addText(content,ResultText.summary(r),14,false,8);
        addText(content,"手机保持解锁，并先在游戏中登录。只制造每台首件置顶道具。",12,false,20);
    }
    private void setAutoCycle(boolean enabled){
        if(enabled && AutoCycle.hasSecureLock(this)){
            toast("自动续造适用于未设密码或指纹锁的手机。");render();return;
        }
        if(enabled && Build.VERSION.SDK_INT>=31 && !AutoCycle.hasAlarmPermission(this)){
            toast("请允许「闹钟和提醒」，以便到点自动续造。");
            alarmSettings.launch(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:"+getPackageName())));
            return;
        }
        AutoCycle.setEnabled(this,enabled);render();
    }
    private void addTextView(LinearLayout parent,TextView view,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(view,p);}
    private void chatPage(){
        ScrollView scroll=new ScrollView(this);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout messages=column();messages.setPadding(dp(24),dp(8),dp(24),dp(20));scroll.addView(messages);
        JSONArray history=ChatStore.load(this);
        if(history.length()==0){addText(messages,"说说你想做什么",24,true,20);addText(messages,"可以说“收获并续造”或“看看造好了没有”。补料按制造页设置的各台上限执行。",16,false,12);}
        if(!new ModelSettings(this).configured()){
            addText(messages,"AI 聊天还差一步",18,true,20);addText(messages,"在设置里填写一次模型密钥。制造页的按钮现在就能使用。",14,false,8);messages.addView(button("设置 AI 聊天",false,this::modelSettings));
        }
        for(int i=0;i<history.length();i++){
            JSONObject e=history.optJSONObject(i);if(e==null)continue;boolean user=e.optString("role").equals("user");
            addText(messages,user?"你":"助手",12,true,20);TextView message=text(e.optString("content"),15,false);message.setTextIsSelectable(true);addTextView(messages,message,4);
        }
        if(modelBusy)addText(messages,"正在理解你的意思…",14,false,16);
        if(ScriptRunner.running.get())addText(messages,"手机正在执行制造流程，结束后会在这里显示结果。",14,false,16);
        LinearLayout input=column();input.setPadding(dp(16),0,dp(16),dp(8));
        TextInputLayout field=new TextInputLayout(this);field.setHint("给助手发消息");composer=new TextInputEditText(field.getContext());composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);composer.setMaxLines(4);composer.setText(draft);field.addView(composer);input.addView(field);
        MaterialButton send=button(modelBusy?"正在回复":"发送",true,this::sendChat);send.setEnabled(!modelBusy&&!ScriptRunner.running.get());input.addView(send);body.addView(input);
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void sendChat(){
        String value=String.valueOf(composer.getText()).trim();if(value.isEmpty()||modelBusy)return;
        if(value.length()>4000){toast("消息太长，请缩短一些。");return;}
        if(!new ModelSettings(this).configured()){modelSettings();return;}
        draft="";composer.setText("");ChatStore.add(this,"user",value);modelBusy=true;render();
        Context app=getApplicationContext();JSONArray history=ChatStore.load(app);
        new Thread(()->{
            try{
                AiClient.Reply reply=AiClient.send(app,history);
                if(reply.action()==null)ChatStore.add(app,"assistant",reply.text());
                else ChatStore.add(app,"assistant","准备执行："+actionName(reply.action())+"。");
                main.post(()->{modelBusy=false;if(reply.action()!=null&&resumed&&!isDestroyed())startTask(reply.action(),true);else if(reply.action()!=null)ChatStore.add(app,"assistant","应用已进入后台，尚未开始。请回到制造页使用按钮启动。");render();});
            }catch(Exception e){ChatStore.add(app,"assistant",e.getMessage()==null?"模型连接未完成，请检查设置或使用按钮。":e.getMessage());main.post(()->{modelBusy=false;render();});}
        },"model-chat").start();
    }
    private String actionName(String mode){return switch(mode){case "cycle"->"收获并续造";case "check"->"检查制造";case "harvest"->"只收获";default->"查询补料";};}
    private void startTask(String mode,boolean fromChat){
        if(ScriptRunner.running.get()){toast("本次任务还在执行。");return;}
        if(WorkshopAccessibility.instance==null){permissions();return;}
        if(getPackageManager().getLaunchIntentForPackage("com.tencent.tmgp.dfm")==null){toast("请先安装并登录三角洲。");return;}
        try{
            getSharedPreferences("chat",0).edit().putBoolean("awaitingResult",fromChat).putLong("startedAfter",System.currentTimeMillis()).apply();
            RunService.start(this,mode);toast("正在启动"+actionName(mode));main.postDelayed(this::render,300);
        }catch(Exception e){toast("无法启动，请保持助手在前台后再试。");}
    }
    private void recordChatResult(){
        android.content.SharedPreferences p=getSharedPreferences("chat",0);if(!p.getBoolean("awaitingResult",false))return;
        JSONObject r=ScriptRunner.readLatest(this);if(r==null||!("finished".equals(r.optString("phase"))||"failed".equals(r.optString("phase"))||"awaiting_approval".equals(r.optString("phase"))))return;
        if(r.optLong("runId")<p.getLong("startedAfter",Long.MAX_VALUE))return;
        StringBuilder value=new StringBuilder("本地执行结果\n").append(ResultText.summary(r));for(String station:STATIONS)value.append("\n").append(station).append("：").append(ResultText.station(r,station));
        ChatStore.add(this,"assistant",value.toString());p.edit().putBoolean("awaitingResult",false).apply();
    }
    private void permissions(){
        new MaterialAlertDialogBuilder(this).setTitle("开启游戏操作")
            .setMessage("此权限让制造助手读取屏幕并执行点击，用于收获、补料和制造。\n\n接下来在系统页面中，找到“制造助手 · 游戏操作”并开启。完成后返回这里。")
            .setNegativeButton("暂不开启",null).setPositiveButton("前往开启",(d,w)->{
                Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);intent.putExtra(":settings:fragment_args_key",new ComponentName(this,WorkshopAccessibility.class).flattenToString());startActivity(intent);
            }).show();
    }
    private void settings(){
        String[] items={WorkshopAccessibility.instance==null?"开启游戏操作":"游戏操作已开启","AI 聊天设置","通知提醒","关于与开源说明"};
        new MaterialAlertDialogBuilder(this).setTitle("设置").setItems(items,(d,index)->{
            switch(index){case 0->permissions();case 1->modelSettings();case 2->{if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);else toast("此系统无需单独授权通知。");}default->about();}
        }).setNegativeButton("关闭",null).show();
    }
    private TextInputEditText input(LinearLayout parent,String label,String value,boolean secret){
        TextInputLayout layout=new TextInputLayout(this);layout.setHint(label);TextInputEditText edit=new TextInputEditText(layout.getContext());
        edit.setSingleLine(true);edit.setText(value);edit.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        if(secret)layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);layout.addView(edit);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);parent.addView(layout,p);return edit;
    }
    private void modelSettings(){
        ModelSettings model=new ModelSettings(this);LinearLayout content=column();content.setPadding(dp(24),dp(8),dp(24),dp(8));
        addText(content,"选择服务，填写一次密钥。模型调用由该服务计费；按钮操作不调用模型。",14,false,0);
        Spinner provider=new Spinner(this);String[] providers={"DeepSeek","自定义兼容服务"};provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,providers));content.addView(provider,new LinearLayout.LayoutParams(-1,dp(56)));
        TextInputEditText secret=input(content,model.configured()?"模型密钥（留空保留）":"模型密钥","",true);
        boolean custom=!model.endpoint().equals("https://api.deepseek.com");
        LinearLayout advanced=column();TextInputEditText endpoint=input(advanced,"服务地址",model.endpoint(),false),name=input(advanced,"模型名称",model.model(),false);content.addView(advanced);
        advanced.setVisibility(custom?View.VISIBLE:View.GONE);provider.setSelection(custom?1:0);
        provider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){advanced.setVisibility(pos==1?View.VISIBLE:View.GONE);}});
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("AI 聊天设置").setView(scroll).setNegativeButton("取消",null).setNeutralButton("删除密钥",(d,w)->{model.clear();render();}).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            try{if(!model.configured()&&String.valueOf(secret.getText()).trim().isEmpty())throw new IllegalArgumentException("请填写模型密钥");boolean preset=provider.getSelectedItemPosition()==0;model.save(preset?"https://api.deepseek.com":String.valueOf(endpoint.getText()),preset?"deepseek-flash":String.valueOf(name.getText()),String.valueOf(secret.getText()));dialog.dismiss();toast("已保存，密钥仅加密保存在这台手机。");render();}catch(Exception e){secret.setError(e.getMessage());}
        }));dialog.show();
    }
    private void purchaseSettings(){
        LinearLayout content=column();
        content.setPadding(dp(24),dp(8),dp(24),dp(8));
        addText(content,"单位为哈夫币。各台在自己的上限内自动补料，填 0 关闭该台购买。",14,false,0);
        TextInputEditText[] fields=new TextInputEditText[STATIONS.length];
        for(int i=0;i<STATIONS.length;i++) {
            String station=STATIONS[i];
            fields[i]=input(content,station,String.valueOf(ScriptRunner.purchaseCap(this,station)),false);
            fields[i].setInputType(InputType.TYPE_CLASS_NUMBER);
            fields[i].setSelectAllOnFocus(true);
        }
        ScrollView scroll=new ScrollView(this);
        scroll.addView(content);
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this)
            .setTitle("各台补料上限").setView(scroll)
            .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            long[] caps=new long[fields.length];
            for(int i=0;i<fields.length;i++) {
                TextInputEditText field=fields[i];
                try {
                    caps[i]=Long.parseLong(String.valueOf(field.getText()).trim());
                    if(caps[i]<0 || caps[i]>ScriptRunner.MAX_PURCHASE_CAP) throw new NumberFormatException();
                } catch(NumberFormatException e) {
                    field.setError("请输入 0 到 999,999,999 之间的整数");
                    field.requestFocus();
                    return;
                }
            }
            ScriptRunner.savePurchaseCaps(this,caps);
            dialog.dismiss();
        }));
        dialog.show();
    }
    private void about(){new MaterialAlertDialogBuilder(this).setTitle("制造助手 · 测试版 0.1.19")
        .setMessage("执行和中文识别已内置，无需安装 AutoJs6 或 PalmClaw。\n\n首次在新尺寸上续造前，点「查询补料」自动验证本机布局，查询不会扣费。显示适配通过后即可尝试「收获并续造」。若游戏登录已失效，请在游戏内重新登录。\n\n包含 Mozilla Rhino（MPL-2.0）、AndroidX / Material（Apache-2.0）和 Google ML Kit。完整说明随源码提供。\n\n模型密钥加密保存在本机；只有聊天内容和简要制造结果会发给你选择的模型服务，截图保存在应用内部。")
        .setPositiveButton("知道了",null).show();}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}
