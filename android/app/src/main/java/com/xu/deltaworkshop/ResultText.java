package com.xu.deltaworkshop;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

final class ResultText {
    static String money(long n){return NumberFormat.getIntegerInstance(Locale.US).format(n);}
    static String headline(JSONObject r){
        if(r==null)return "尚未检查制造";
        String phase=r.optString("phase");
        if("failed".equals(phase))return friendlyError(r.optString("error"));
        if(r.optBoolean("budgetBlocked"))return "补料超过总上限，本轮停止";
        if(r.has("layoutVerified"))return r.optBoolean("layoutVerified")?"本机适配通过，可收获并续造":"尚未验证本机续造布局";
        if(r.has("previewTotal")||r.has("approvalPlan"))return "补料报价已核对";
        if(!"finished".equals(phase))return "正在核对游戏画面";
        int started=r.optInt("craftStarts"),collected=r.optJSONArray("collected")==null?0:r.optJSONArray("collected").length();
        if(started>0)return "已开工 "+started+" 台";
        if(collected>0)return "已收获 "+collected+" 件成品";
        return "制造状态已核对";
    }
    static String station(JSONObject r,String name){
        if(r==null)return "尚未检查";
        JSONArray items=r.optJSONArray("manufacturing");
        if(items!=null)for(int i=0;i<items.length();i++){
            JSONObject e=items.optJSONObject(i);if(e==null||!name.equals(e.optString("name")))continue;
            if(e.has("remainingPurchaseCost")&&r.optBoolean("unresolvedPurchase"))
                return "待处理 · "+money(e.optLong("remainingPurchaseCost"))+" 哈夫币";
            String state=e.optString("status"),timer=e.optString("finalRemaining",e.optString("remaining"));
            if(timer.matches("[0-9]{2}:[0-5][0-9]:[0-5][0-9]"))return "制造中 · "+timer;
            switch(state){
                case "no_pinned_recipe":return "未找到首件置顶道具";
                case "purchase_disabled":return "缺料 · 未开启购买";
                case "over_station_cap":return "已跳过 · 报价 "+money(e.optLong("quote"))+" 超过上限 "+money(e.optLong("purchaseCap"));
                case "quote_unavailable":return "报价未能确认 · 已跳过";
                case "awaiting_collection":return "已完成，待收获";
                case "needs_purchase_approval":
                case "quoted":return "补料 "+money(e.optLong("quote"))+" 哈夫币";
                case "materials_ready":return "材料齐全";
                case "inspected":return "空闲";
            }
        }
        JSONArray states=r.optJSONArray("finalStates");if(states==null)states=r.optJSONArray("stations");
        if(states!=null)for(int i=0;i<states.length();i++){
            JSONObject e=states.optJSONObject(i);if(e==null||!name.equals(e.optString("name")))continue;
            switch(e.optString("state",e.optString("after",e.optString("before")))){
                case "ready":return "已完成，待收获";
                case "idle":return "空闲";
                default:return "制造中或尚未确认";
            }
        }
        return "尚未核对";
    }
    static String summary(JSONObject r){
        if(r==null)return "先检查一次，获取这台手机上的制造状态。";
        StringBuilder s=new StringBuilder(headline(r));
        if(r.has("previewTotal")||r.has("approvalTotal"))
            s.append("\n报价合计 ").append(money(r.optLong("previewTotal",r.optLong("approvalTotal")))).append(" 哈夫币");
        if(r.has("layoutVerified") && !r.optBoolean("layoutVerified"))
            s.append("\n至少需要一台空闲工作台的首件置顶配方，并识别到生产按钮或有效补料报价。准备好后再点「查询补料」。");
        if(r.has("budgetEstimate") && (r.optBoolean("budgetBlocked")||r.optBoolean("unresolvedPurchase")))
            s.append("\n整轮预计最高 ").append(money(r.optLong("budgetEstimate")))
                .append(" / 上限 ").append(money(r.optLong("purchaseCap"))).append(" 哈夫币");
        if("failed".equals(r.optString("phase")) && r.optJSONArray("collected")!=null && r.optJSONArray("collected").length()>0)
            s.append("\n已收获 ").append(r.optJSONArray("collected").length()).append(" 件，其余未完成。");
        if("failed".equals(r.optString("phase")))s.append("\n").append(recovery(r.optString("error")));
        if(r.optBoolean("unresolvedPurchase"))s.append("\n可能已有部分扣费。先核对游戏材料，勿重复补料。");
        if(r.has("spentUpperBound"))s.append("\n本轮支出不超过 ").append(money(r.optLong("spentUpperBound"))).append(" 哈夫币");
        else if(r.has("spent"))s.append(r.optBoolean("unresolvedPurchase")?"\n已核实花费 ":"\n本轮花费 ")
            .append(money(r.optLong("spent"))).append(" 哈夫币");
        if(r.has("craftStarts"))s.append("\n开工 ").append(r.optInt("craftStarts")).append(" 台 · 购买 ").append(r.optInt("purchases")).append(" 次");
        return s.toString();
    }
    static String friendlyError(String e){
        if(e.contains("USER_STOPPED"))return "已停止任务";
        if(e.contains("RUN_TIMEOUT"))return "任务超时，已停止";
        if(e.contains("ACCESSIBILITY"))return "游戏操作权限未开启";
        if(e.contains("SCREEN_OFF"))return "手机已锁屏，任务停止";
        if(e.contains("LAYOUT_CYCLE_UNVERIFIED"))return "本机还未完成续造适配验证";
        if(e.contains("LAYOUT_CHECK_ONLY"))return "当前游戏画面仅支持检查制造";
        if(e.contains("OPEN_WORKSHOP_FOR_LAYOUT_CHECK"))return "请先打开游戏内的特勤处总览";
        if(e.contains("LAYOUT_CHANGED"))return "游戏画面尺寸已变化";
        if(e.contains("UNSUPPORTED_SCREEN"))return "当前游戏画面尺寸尚未适配";
        if(e.contains("STATION_STATE_UNKNOWN"))return "工作台状态未识别";
        if(e.contains("WRONG_STATION_PAGE"))return "进入配方页后未识别到工作台名称";
        if(e.contains("PINNED_NAME_UNREADABLE")||e.contains("PINNED_SELECTION_NOT_CONFIRMED")||e.contains("SELECTED_TITLE_UNREADABLE"))return "未能确认首件置顶配方";
        if(e.contains("PRODUCTION_BUTTON_UNKNOWN")||e.contains("PRODUCTION_PRECONDITION_CHANGED"))return "未能确认配方的生产或补料按钮";
        if(e.contains("REFILL_DIALOG_NOT_READY")||e.contains("QUOTE_DIGITS")||e.contains("QUOTE_CHANGED"))return "未能确认补料清单或金额";
        if(e.contains("CRAFT_START_NOT_CONFIRMED"))return "未能确认开工后的倒计时";
        if(e.contains("WAREHOUSE_FULL"))return "仓库空间不足";
        if(e.contains("REPRICE_PENDING"))return "游戏中有待处理的变价订单";
        if(e.contains("REPRICE_STATION_BUDGET_EXCEEDED"))return "变价后可能超过该台上限";
        if(e.contains("LOGGED_OUT"))return "游戏账号已下线";
        if(e.contains("AUTOCYCLE_ACCOUNT_MISMATCH"))return "游戏账号已切换，自动续造暂停";
        if(e.contains("APPROVAL_ALREADY_USED"))return "这份补料确认已使用";
        if(e.contains("APPROVAL_PLAN_EXPIRED"))return "补料报价已过期";
        if(e.contains("APPROVAL_ACCOUNT_MISMATCH"))return "当前账号与报价账号不同";
        if(e.contains("ANOTHER_RUN_OR_STALE_LOCK"))return "有未结束或待核对的任务";
        if(e.contains("OCR"))return "文字识别未完成";
        if(e.contains("BUDGET")||e.contains("TOTAL_EXCEEDED"))return "当前价格超过本轮上限";
        return "画面或操作结果未确认，任务已停止";
    }
    static String recovery(String e){
        if(e.contains("ACCESSIBILITY"))return "打开设置，开启“游戏操作”后再试。";
        if(e.contains("WAREHOUSE_FULL"))return "请先在游戏里腾出仓库空间。";
        if(e.contains("LOGGED_OUT"))return "请在手机重新登录游戏。";
        if(e.contains("AUTOCYCLE_ACCOUNT_MISMATCH"))return "如需为当前账号续造，请先检查制造。";
        if(e.contains("REPRICE"))return "请在游戏中核对并处理当前订单。";
        if(e.contains("LAYOUT_CYCLE_UNVERIFIED"))return "先点一次「查询补料」。本机适配通过后可继续。";
        if(e.contains("LAYOUT_CHECK_ONLY")||e.contains("OPEN_WORKSHOP_FOR_LAYOUT_CHECK")||e.contains("LAYOUT_CHANGED"))return "在游戏停在特勤处后，回到助手点「检查制造」。";
        if(e.contains("UNSUPPORTED_SCREEN"))return "请把游戏切换到横屏后重试。";
        if(e.contains("APPROVAL"))return "请核对账号，重新查询本轮报价。";
        return "请先查看游戏画面，确认是否已领取、买入或开工，再决定下一步。";
    }
    static JSONObject modelResult(JSONObject r) throws Exception {
        JSONObject safe=new JSONObject().put("summary",summary(r));
        JSONArray rows=new JSONArray();for(String name:MainActivity.STATIONS)rows.put(new JSONObject().put("station",name).put("state",station(r,name)));
        return safe.put("stations",rows);
    }
}
