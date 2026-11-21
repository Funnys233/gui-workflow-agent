/* Native recipe selection, quotes, and guarded manufacturing. */
var api = module.exports;

function normalize(value) {
    return String(value).toUpperCase().replace(/裝/g,"装").replace(/[^A-Z0-9\u4e00-\u9fff]/g, "");
}
function inside(item, box) {
    var x=(item.left+item.right)/2,y=(item.top+item.bottom)/2;
    return x>=box[0] && y>=box[1] && x<box[2] && y<box[3];
}
function textIn(items,box) {
    return items.filter(function(i){return inside(i,box);})
        .sort(function(a,b){return a.top-b.top || a.left-b.left;})
        .map(function(i){return i.text;}).join(" ");
}
function rawDetect(frame,region) {
    ocr.mode="mlkit";
    var detected=region?ocr.detect(frame,{region:region}):ocr.detect(frame),items=[];
    for(var i=0;i<detected.length;i++) {
        var d=detected[i],b=d.bounds;
        items.push({text:String(d.label),left:Number(b.left),top:Number(b.top),
            right:Number(b.right),bottom:Number(b.bottom)});
    }
    return items;
}
var labels={};
function buttonInk(c) {
    var r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
    return (Math.min(r,g,b)>=150 && Math.max(r,g,b)-Math.min(r,g,b)<85) ||
        (g>=150 && g-r>=35 && g-b>=20);
}
function pixelButton(frame) {
    var names=["refill","produce","busy"];
    var tolerance=typeof calibratedLayout==="function" && calibratedLayout()?2:0;
    var root=files.exists(files.join(files.cwd(),"delta-manufacture.js")) ? files.cwd() : files.join(files.cwd(),"delta");
    for(var n=0;n<names.length;n++) {
        var name=names[n];
        if(!labels[name]) {
            var bmp=android.graphics.BitmapFactory.decodeFile(files.join(root,"assets/button-"+name+".png"));
            if(!bmp) throw new Error("BUTTON_TEMPLATE_MISSING: "+name);
            var points=[];
            for(var y=1;y<bmp.getHeight()-1;y+=2) for(var x=1;x<bmp.getWidth()-1;x+=2)
                points.push([x,y,buttonInk(bmp.getPixel(x,y))]);
            bmp.recycle();labels[name]=points;
        }
        var pts=labels[name];
        for(var dx=-tolerance;dx<=tolerance;dx++) for(var shift=-2;shift<=2;shift++) {
            var yes=0,no=0,positive=0,negative=0;
            for(var i=0;i<pts.length;i++) {
                var p=pts[i],ink=buttonInk(images.pixel(frame,2275+dx+p[0],1098+shift+p[1]));
                if(p[2]) {positive++;if(ink)yes++;}
                else {negative++;if(!ink)no++;}
            }
            if(positive>=12 && yes/positive>=0.88 && no/negative>=0.94) return name;
        }
    }
    return "unknown";
}
function detect(frame) {
    var items=rawDetect(frame);
    function refine(region) {
        var box=[region[0],region[1],region[0]+region[2],region[1]+region[3]];
        items=items.filter(function(i){return !inside(i,box);}).concat(rawDetect(frame,region));
    }
    refine([160,10,500,90]);
    var title=normalize(textIn(items,[160,10,660,100]));
    if(/技术中心|工作台|制药台|防具台/.test(title)) {
        refine([265,260,435,135]);
        refine([963,205,1087,80]);
        refine([2150,950,465,115]);
        items=items.filter(function(i){return !inside(i,[2100,1070,2615,1180]);});
        var label={produce:"生产",refill:"一键补齐",busy:"中止"}[pixelButton(frame)];
        if(label) items.push({text:label,left:2280,top:1100,right:2430,bottom:1145});
    }
    // Read timer regions independently of OCR's spelling of the overview heading.
    [[403,502,180,42],[840,502,180,42],[403,960,180,42],[840,960,180,42]].forEach(refine);
    return items;
}
function countdown(text) {
    var m=String(text).match(/\b(\d{2})\s*[:：]\s*([0-5]\d)\s*[:：]\s*([0-5]\d)\b/);
    return m ? m[1]+":"+m[2]+":"+m[3] : null;
}
function countdownAt(entry, time) {
    if (!entry.remainingReadAt || time<entry.remainingReadAt) return null;
    var parts=entry.remaining.split(":").map(Number);
    var left=Math.max(0,parts[0]*3600+parts[1]*60+parts[2]-Math.floor((time-entry.remainingReadAt)/1000));
    return [Math.floor(left/3600),Math.floor(left/60)%60,left%60].map(function(part){
        return ("0"+part).slice(-2);
    }).join(":");
}
function button(items) {
    var text=normalize(textIn(items,[2100,1070,2615,1180]));
    if(text==="生产") return "produce";
    if(text==="一键补齐") return "refill";
    if(text==="中止") return "busy";
    return "unknown";
}
function stationPage(items,s) {
    return normalize(textIn(items,[160,10,610,100])).indexOf(s.name)>=0;
}
function sameRecipe(items,name) {
    return labelsAgree(selectedTitle(items),name);
}
function selectedTitle(items) {
    var titles=items.filter(function(i){return inside(i,[963,205,2050,285]);})
        .sort(function(a,b){return a.left-b.left;});
    if(!titles.length) return "";
    // The separate three-line details icon is occasionally recognized as 三 or =.
    return titles[0].text.trim().replace(/\s+[三=≡]+\s*$/,"")
        .replace(/\s*[=≡]+\s*$/,"").replace(/\s*[*×]\s*\d+\s*$/,"");
}
function labelsAgree(a,b) {
    a=normalize(a);b=normalize(b);
    if(!a || !b || Math.min(a.length,b.length)/Math.max(a.length,b.length)<0.8) return false;
    var row=[];
    for(var j=0;j<=b.length;j++) row[j]=j;
    for(var i=1;i<=a.length;i++) {
        var next=[i];
        for(var j=1;j<=b.length;j++) next[j]=Math.min(next[j-1]+1,row[j]+1,row[j-1]+(a[i-1]===b[j-1]?0:1));
        row=next;
    }
    return row[b.length]<=Math.max(1,Math.floor(Math.max(a.length,b.length)*0.15));
}
function selectedFirst(frame,busy) {
    var hits=0,top=busy?220:273;
    [top+22,top+65,top+115].forEach(function(y){
        [109,812].forEach(function(x){
            var c=images.pixel(frame,x,y),r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
            if(Math.min(r,g,b)>=185 && Math.max(r,g,b)-Math.min(r,g,b)<60) hits++;
        });
    });
    return hits===6;
}
function firstRecipe(items,busy) {
    return textIn(items,[265,busy?260:310,700,busy?335:395]).trim();
}
function quoteMask(frame) {
    var points=[];
    for(var y=965;y<1020;y+=2) for(var x=1200;x<1515;x+=2) {
        var c=images.pixel(frame,x,y),r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
        if(g>=130 && g-r>=35 && g-b>=20) points.push((y-965)*315+x-1200);
    }
    if(points.length<20) throw new Error("QUOTE_DIGITS_NOT_VISIBLE");
    return points;
}
function sameQuoteMask(a,b) {
    if(!a || !b || !a.length || !b.length) return false;
    var set={},intersection=0;
    a.forEach(function(p){set[p]=true;});
    b.forEach(function(p){if(set[p]) intersection++;});
    return intersection/(a.length+b.length-intersection)>=0.995;
}
function gray(c) {
    var r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
    return Math.min(r,g,b)>=115 && Math.max(r,g,b)-Math.min(r,g,b)<65;
}
var pinPattern=null;
var dialogPattern=null;
function refillDialog(frame) {
    if(!dialogPattern) {
        var root=files.exists(files.join(files.cwd(),"delta-manufacture.js"))?files.cwd():files.join(files.cwd(),"delta");
        var bmp=android.graphics.BitmapFactory.decodeFile(files.join(root,"assets/refill-dialog.png"));
        if(!bmp) throw new Error("REFILL_DIALOG_TEMPLATE_MISSING");
        dialogPattern=[];
        for(var y=1;y<bmp.getHeight()-1;y+=3) for(var x=1;x<bmp.getWidth()-1;x+=3)
            dialogPattern.push([x,y,gray(bmp.getPixel(x,y))]);
        bmp.recycle();
    }
    var yes=0,no=0,positive=0,negative=0;
    dialogPattern.forEach(function(p){
        var ink=gray(images.pixel(frame,425+p[0],110+p[1]));
        if(p[2]){positive++;if(ink)yes++;}else{negative++;if(!ink)no++;}
    });
    return positive>=12 && yes/positive>=0.88 && no/negative>=0.90;
}
function pinned(frame,busy,root) {
    if(!pinPattern) {
        var bitmap=android.graphics.BitmapFactory.decodeFile(files.join(root,"assets/pinned.png"));
        if(!bitmap) throw new Error("PIN_TEMPLATE_MISSING");
        pinPattern=[];
        for(var y=1;y<bitmap.getHeight()-1;y+=2) for(var x=1;x<bitmap.getWidth()-1;x+=2)
            pinPattern.push([x,y,gray(bitmap.getPixel(x,y))]);
        bitmap.recycle();
    }
    // Idle lists have the 已解锁 heading; active lists begin 54 pixels higher.
    var originY=busy?233:287;
    var shifts=[[0,0],[-1,0],[1,0],[0,-1],[0,1],[-2,0],[2,0],[0,-2],[0,2],[-1,-1],[-1,1],[1,-1],[1,1]];
    for(var shift=0;shift<shifts.length;shift++) {
        var yes=0,no=0,yesAll=0,noAll=0;
        for(var i=0;i<pinPattern.length;i++) {
            var p=pinPattern[i],ink=gray(images.pixel(frame,769+shifts[shift][0]+p[0],originY+shifts[shift][1]+p[1]));
            if(p[2]) {yesAll++;if(ink)yes++;}
            else {noAll++;if(!ink)no++;}
        }
        if(yesAll>=10 && yes/yesAll>=0.88 && no/noAll>=0.88) return true;
    }
    return false;
}
function overviewTimer(items,s) {
    return countdown(textIn(items,[s.x+35,s.y+140,s.right,s.y+205]));
}
function accountId(frame) {
    var parts=rawDetect(frame,[340,1180,660,40]).sort(function(a,b){return a.left-b.left;});
    var line=parts.map(function(i){return i.text;}).join("").replace(/\s/g,"");
    var match=line.match(/UID\D*([0-9]{8,32})_/i);
    return match?match[1]:null;
}
function running(items) {
    return button(items)==="busy" &&
        countdown(textIn(items,[2150,950,2615,1060]));
}
function verifiedRunning(frame,s,name) {
    var items=detect(frame);
    return stationPage(items,s) && sameRecipe(items,name) && running(items);
}

api.prepareOcr=function() { ocr.mode="mlkit"; };

api.run=function(c) {
    var a=c.args,r=c.result;
    var quoteOnly=a.quoteOnly===true;
    var stationCaps=a.stationCaps;
    if(stationCaps!==undefined) {
        if(!stationCaps || a.approvedPlan || !c.stations.every(function(station){
            var limit=stationCaps[station.name];
            return typeof limit==="number" && isFinite(limit) && limit>=0 &&
                Math.floor(limit)===limit && limit<=999999999;
        })) throw Error("PURCHASE_BUDGET_INVALID");
    }
    r.manufacturing=[];
    r.quotedTotal=0;
    var approval=null,approvalUseMarker=null,approvalBudget=0,projectedTotal=0,tolerance=0;
    // A review file is written by quoteOnly. A purchase approval is tied to that file,
    // that account, those recipe names, one aggregate budget, and one execution.
    if(a.approvedPlan) {
        if(!/^\d{10,16}$/.test(String(a.approvedPlan))) throw new Error("APPROVAL_ID_INVALID");
        var path=files.join(c.root,"runs",String(a.approvedPlan),"manufacture-plan.json");
        approval=JSON.parse(files.read(path));
        if(approval.schema!=="delta-manufacture-plan-v3" || !approval.complete ||
            Date.now()-approval.createdAt>30*60*1000 || approval.createdAt>Date.now())
            throw new Error("APPROVAL_PLAN_EXPIRED_OR_INCOMPLETE");
        approvalUseMarker=new java.io.File(files.join(c.root,"runs",String(a.approvedPlan),"approval-used.lock"));
        var settingsPath=files.join(c.root,"manufacture-settings.json");
        if(files.exists(settingsPath)) tolerance=Number(JSON.parse(files.read(settingsPath)).purchasePriceTolerance||0);
        if(!isFinite(tolerance) || tolerance<0 || tolerance>10000 || Math.floor(tolerance)!==tolerance)
            throw new Error("PRICE_TOLERANCE_INVALID");
        approvalBudget=approval.total+tolerance;
        if(a.purchaseBudget!==undefined) {
            var limit=Number(a.purchaseBudget);
            if(!isFinite(limit)||limit<0||Math.floor(limit)!==limit) throw new Error("PURCHASE_BUDGET_INVALID");
            approvalBudget=Math.min(approvalBudget,limit);
        }
        projectedTotal=approval.total;
        r.approvedBudget=approvalBudget;r.priceTolerance=tolerance;
        r.remainingApprovedBudget=approvalBudget;
    }
    function observe(layout) {
        var end=Date.now()+12000,items;
        do {
            items=c.snapshot(detect,layout);
            if(!/USB调|已连接到/.test(normalize(items.map(function(i){return i.text;}).join(""))))
                return items;
            sleep(400);
        } while(Date.now()<end);
        throw new Error("SCREEN_OBSCURED_BY_SYSTEM_NOTIFICATION");
    }
    function waitForQuote() {
        var previous=null,cost=null;
        c.waitFor(function(f){
            if(!refillDialog(f)) {previous=null;return false;}
            var current;
            try {current=api.quote(f);}
            catch(e) {
                if(!/^Error: PRICE_(GLYPH_LAYOUT_UNKNOWN|COMMA_UNKNOWN|DIGIT_UNCERTAIN|NUMBER_FORMAT_UNKNOWN)\b/.test(String(e))) throw e;
                previous=null;return false;
            }
            if(current!==previous) {previous=current;return false;}
            cost=current;return true;
        },10000,false,"center");
        return cost;
    }
    function budgetSpent() {
        return r.spentUpperBound!==undefined?r.spentUpperBound:(r.spent||0);
    }
    function back(s) {
        var p=observe("station");
        if(!stationPage(p,s)) throw new Error("BACK_PAGE_CHANGED: "+s.name);
        c.tap(130,54);
        if(!c.waitFor(c.overview,6000)) throw new Error("RETURN_TO_OVERVIEW_FAILED");
    }
    var account=null;
    if(!r.overviewCheckOnly) {
        account=c.snapshot(accountId);
        sleep(150);
        if(!account || account!==c.snapshot(accountId)) throw new Error("ACCOUNT_ID_UNREADABLE");
        r.timerAccount=account;
    }
    if(approval && account!==approval.account) throw new Error("APPROVAL_ACCOUNT_MISMATCH");
    if(approvalUseMarker && !approvalUseMarker.createNewFile()) throw new Error("APPROVAL_ALREADY_USED");
    var plan={schema:"delta-manufacture-plan-v3",runId:r.runId,createdAt:Date.now(),
        account:account,complete:false,stations:[],total:0};
    for(var i=0;i<c.stations.length;i++) {
        var s=c.stations[i];
        var status=null;
        if(!c.waitFor(function(f){
            if(!c.overview(f))return false;
            status=c.state(f,s);return true;
        },10000)) throw new Error("MANUFACTURE_OVERVIEW_OBSCURED");
        var entry={name:s.name,before:status};
        var stationCap=stationCaps===undefined?null:stationCaps[s.name];
        r.manufacturing.push(entry);
        if(status==="ready") {
            entry.status="awaiting_collection";
            c.checkpoint();
            continue;
        }
        var ov=status==="busy_or_unknown"?observe():null;
        var busy=ov && overviewTimer(ov,s);
        if(status!=="idle" && !busy) {
            entry.timerText=textIn(ov||[],[s.x+35,s.y+140,s.right,s.y+205]);
            throw new Error("STATION_STATE_UNKNOWN: "+s.name);
        }
        entry.remaining=busy||null;
        if(busy) entry.remainingReadAt=Date.now();
        // Scheduling only needs the status and countdown on each overview card.
        if(a.timersOnly===true || (busy && a.inspectOnly!==true)) {
            entry.status=busy?"already_running":"inspected";
            c.checkpoint();
            continue;
        }
        if(!c.waitFor(function(f){return c.overview(f)&&c.state(f,s)===status;},7000))
            throw new Error("STATION_CHANGED_BEFORE_OPEN: "+s.name);
        c.tap(s.x+190,s.y+100);
        var page=observe("station");
        if(!stationPage(page,s)) throw new Error("WRONG_STATION_PAGE: "+s.name);
        c.snapshot(function(f){c.save(f,"recipe-list-"+i);},"station");
        if(!c.snapshot(function(f){return pinned(f,!!busy,c.root);},"station")) {
            entry.status="no_pinned_recipe";
            back(s);c.checkpoint();continue;
        }
        var name=firstRecipe(page,!!busy);
        if(normalize(name).length<2) throw new Error("PINNED_NAME_UNREADABLE: "+s.name);
        entry.recipe=name;
        c.tap(450,busy?294:348);
        page=observe("station");
        if(!stationPage(page,s) ||
            !c.snapshot(function(f){return pinned(f,!!busy,c.root)&&selectedFirst(f,!!busy);},"station"))
            throw new Error("PINNED_SELECTION_NOT_CONFIRMED: "+s.name);
        // The selected row's white border and pin determine identity. The large title
        // supplies the name; small list text can confuse 进 and 选 in the game's font.
        var listName=name;
        name=selectedTitle(page);
        if(normalize(name).length<2 || !labelsAgree(listName,name) || /USB|调试/.test(normalize(name)))
            throw new Error("SELECTED_TITLE_UNREADABLE_OR_OBSCURED");
        entry.recipe=name;
        c.snapshot(function(f){c.save(f,"recipe-selected-"+i);},"station");
        if(busy || a.inspectOnly===true) {
            entry.status=busy?"already_running":"inspected";
            var recipeTimer=running(page);
            if(recipeTimer) {
                entry.remaining=recipeTimer;
                entry.remainingReadAt=Date.now();
            }
            back(s);c.checkpoint();continue;
        }
        var matchingItems=approval && approval.stations.filter(function(item){
            return item.name===s.name && normalize(item.recipe)===normalize(name);
        });
        var approvedItem=matchingItems && matchingItems.length===1?matchingItems[0]:null;
        var action=button(page),cost=0;
        if(action!=="produce" && action!=="refill") throw new Error("PRODUCTION_BUTTON_UNKNOWN: "+s.name);
        if(action==="refill") {
            if(!quoteOnly && stationCap===0) {
                entry.status="purchase_disabled";
                back(s);c.checkpoint();continue;
            }
            c.tap(2350,1120,"center");
            cost=waitForQuote();
            if(cost===null) {
                if(!c.snapshot(refillDialog,"center")) throw new Error("REFILL_DIALOG_NOT_READY: "+s.name);
                entry.status="quote_unavailable";
                c.snapshot(function(f){c.save(f,"refill-quote-unavailable-"+i);},"center");
                c.tap(2330,142);
                back(s);c.checkpoint();continue;
            }
            entry.quote=cost;r.quotedTotal+=cost;
            c.snapshot(function(f){c.save(f,"refill-quote-"+i);},"center");
            var priceMask=c.snapshot(quoteMask,"center");
            if(stationCap===null)
                plan.stations.push({name:s.name,recipe:name,maxCost:cost,priceMask:priceMask});
            plan.total+=cost;
            var projected=approvedItem?projectedTotal+cost-approvedItem.maxCost:Infinity;
            var priceAllowed=stationCap!==null?cost<=stationCap:
                approvedItem && projected<=approvalBudget &&
                (tolerance>0 || (cost===approvedItem.maxCost && sameQuoteMask(approvedItem.priceMask,priceMask)));
            if(quoteOnly || !priceAllowed) {
                entry.status=quoteOnly?"quoted":stationCap!==null?"over_station_cap":"needs_purchase_approval";
                if(stationCap!==null) entry.purchaseCap=stationCap;
                // The modal title and amount were both verified. Close the quote without buying.
                c.tap(2330,142);
                back(s);c.checkpoint();continue;
            }
            if(approval && budgetSpent()+cost>approvalBudget) throw new Error("APPROVED_TOTAL_EXCEEDED");
            if(!c.snapshot(function(f){
                if(api.quote(f)!==cost) throw Error("QUOTE_CHANGED_BEFORE_PURCHASE");
                if(stationCap!==null && accountId(f)!==account) throw Error("APPROVAL_ACCOUNT_MISMATCH");
                return sameQuoteMask(priceMask,quoteMask(f));
            },"center"))
                throw new Error("QUOTE_DIGITS_CHANGED_BEFORE_PURCHASE");
            projectedTotal=projected;
            r.pendingAction={type:"buy_materials",station:s.name,recipe:name,cost:cost};
            r.unresolvedPurchase=true;
            delete r.remainingApprovedBudget;
            c.checkpoint();
            // One initial order. Only a recognized partial-fill page may receive one extra confirmation.
            c.tap(1355,991,"center");
            var reprice=require(files.join(c.root,"delta-reprice.js")),changed=false;
            if(!c.waitFor(function(f){
                if(r.calibratedLayout?c.snapshot(reprice.dialog,"center"):reprice.dialog(f)) {changed=true;return true;}
                page=detect(f);
                return stationPage(page,s)&&sameRecipe(page,name)&&button(page)==="produce";
            },12000,false,"station"))
                throw new Error("MATERIAL_PURCHASE_NOT_CONFIRMED: "+s.name);
            var costCeiling=cost,priorBudgetSpent=budgetSpent();
            if(changed) {
                var info=c.snapshot(function(f){
                    c.save(f,"price-change-"+i);
                    if(accountId(f)!==account)throw Error("REPRICE_ACCOUNT_CHANGED");
                    return reprice.inspect(f,rawDetect(f,[400,210,1900,630]));
                },"center");
                if(info.remainingCost-info.increaseUpperBound>cost)
                    throw Error("REPRICE_REMAINDER_INCONSISTENT");
                costCeiling=cost+info.increaseUpperBound;
                if(stationCap!==null) {
                    entry.purchaseCap=stationCap;
                    entry.purchaseCostUpperBound=costCeiling;
                    entry.remainingPurchaseCost=info.remainingCost;
                    if(costCeiling>stationCap) throw Error("REPRICE_STATION_BUDGET_EXCEEDED: "+s.name);
                } else if(priorBudgetSpent+costCeiling>approvalBudget ||
                    projectedTotal+info.increaseUpperBound>approvalBudget) {
                    throw Error("REPRICE_TOTAL_EXCEEDS_BUDGET");
                }
                var changeMask=c.snapshot(quoteMask,"center");
                r.pendingAction={type:"confirm_price_change",station:s.name,recipe:name,
                    remainingCost:info.remainingCost,orderCostUpperBound:costCeiling};
                c.checkpoint();
                sleep(300);
                c.snapshot(function(f){
                    var current=reprice.inspect(f,rawDetect(f,[400,210,1900,630]));
                    if(accountId(f)!==account || current.remainingCost!==info.remainingCost ||
                        current.increaseUpperBound!==info.increaseUpperBound ||
                        !sameQuoteMask(changeMask,quoteMask(f)))
                        throw Error("REPRICE_PAGE_CHANGED_BEFORE_CONFIRM");
                },"center");
                // The game excludes already purchased rows. Confirm the remaining selection once.
                c.tap(1355,991,"center");
                r.priceChangeConfirmations=(r.priceChangeConfirmations||0)+1;
                if(!c.waitFor(function(f){
                    page=detect(f);
                    return stationPage(page,s)&&sameRecipe(page,name)&&button(page)==="produce";
                },12000,false,"station")) throw Error("REPRICE_PURCHASE_NOT_CONFIRMED: "+s.name);
                projectedTotal+=info.increaseUpperBound;
                entry.purchaseCostUpperBound=costCeiling;
                entry.priceChangeConfirmed=true;
            }
            r.purchases++;
            if(changed || r.spendingExact===false) {
                r.confirmedSpent=(r.confirmedSpent!==undefined?r.confirmedSpent:(r.spent||0))+(changed?0:cost);
                r.spendingExact=false;
                r.spentUpperBound=priorBudgetSpent+costCeiling;
                delete r.spent;
            } else r.spent=(r.spent||0)+cost;
            if(approval) r.remainingApprovedBudget=approvalBudget-budgetSpent();
            delete r.unresolvedPurchase;delete r.pendingAction;c.checkpoint();
        } else {
            if(approval) approval.stations.forEach(function(p){
                if(p.name===s.name && normalize(p.recipe)===normalize(name)) projectedTotal-=p.maxCost;
            });
            if(stationCap===null) plan.stations.push({name:s.name,recipe:name,maxCost:0});
            if(quoteOnly) {
                entry.status="materials_ready";entry.quote=0;
                back(s);c.checkpoint();continue;
            }
        }
        // Re-read the selected recipe and require the exact 生产 label immediately before starting.
        page=observe("station");
        if(!stationPage(page,s) || !sameRecipe(page,name) || button(page)!=="produce" ||
            !c.snapshot(function(f){return pinned(f,false,c.root)&&selectedFirst(f,false);},"station"))
            throw new Error("PRODUCTION_PRECONDITION_CHANGED: "+s.name);
        r.pendingAction={type:"start_craft",station:s.name,recipe:name};c.checkpoint();
        c.tap(2350,1120);
        var remaining=null;
        if(!c.waitFor(function(f){
            remaining=verifiedRunning(f,s,name);return !!remaining;
        },12000,false,"station")) throw new Error("CRAFT_START_NOT_CONFIRMED: "+s.name);
        c.snapshot(function(f){c.save(f,"craft-started-"+i);},"station");
        r.craftStarts++;entry.status="started";entry.remaining=remaining;
        entry.remainingReadAt=Date.now();
        delete r.pendingAction;c.checkpoint();
        back(s);
        var returned=observe();
        if(!overviewTimer(returned,s)) throw new Error("OVERVIEW_COUNTDOWN_NOT_CONFIRMED: "+s.name);
    }
    plan.complete=true;
    if(a.previewOnly===true) r.previewTotal=plan.total;
    if(stationCaps===undefined && (quoteOnly && !a.previewOnly ||
        r.manufacturing.some(function(e){return e.status==="needs_purchase_approval";}))) {
        if(!quoteOnly) {
            plan.stations=plan.stations.filter(function(p){return r.manufacturing.some(function(e){
                return e.name===p.name && e.status==="needs_purchase_approval";
            });});
            plan.total=plan.stations.reduce(function(sum,p){return sum+p.maxCost;},0);
        }
        files.write(files.join(c.out,"manufacture-plan.json"),JSON.stringify(plan,null,2));
        r.approvalPlan=r.runId;r.approvalTotal=plan.total;
    }
    if(r.manufacturing.some(function(e){return !!e.remainingReadAt;})) {
        var final=observe();
        r.timerReadAt=Date.now();
        r.manufacturing.forEach(function(e,index){
            if(!e.remainingReadAt) return;
            e.finalRemaining=overviewTimer(final,c.stations[index])||countdownAt(e,r.timerReadAt);
            if(!e.finalRemaining) throw new Error("FINAL_COUNTDOWN_MISSING: "+e.name);
        });
    }
    r.needsApproval=r.manufacturing.some(function(e){return e.status==="needs_purchase_approval";});
    c.checkpoint();
};

// Used by native screenshot checks; these are the same checks the live runner uses.
api.detect=detect;
api.inspect=function(frame,s,root,busy) {
    var items=detect(frame);
    return {station:stationPage(items,s),pinned:pinned(frame,busy,root),
        selected:selectedFirst(frame,busy),recipe:firstRecipe(items,busy),button:button(items),remaining:running(items)};
};
api.quote=function(frame){
    if(!refillDialog(frame)) throw new Error("REFILL_DIALOG_UNKNOWN");
    var root=files.exists(files.join(files.cwd(),"delta-price.js"))?files.cwd():files.join(files.cwd(),"delta");
    return require(files.join(root,"delta-price.js"))(frame);
};
api.quoteMask=quoteMask;
api.sameQuoteMask=sameQuoteMask;
api.overviewTimer=overviewTimer;
api.accountId=accountId;
api.refillDialog=refillDialog;
api.verifiedRunning=verifiedRunning;
