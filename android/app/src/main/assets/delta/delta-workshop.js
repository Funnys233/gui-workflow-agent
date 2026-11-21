/* Shared native workshop runner. Redmi K70E, landscape 2712x1220. */
module.exports = function (args, rootDirectory) {
    args = args || {};
    var root = rootDirectory || files.join(files.cwd(), "delta");
    var runId = String(Date.now());
    var out = files.join(root, "runs", runId);
    var result = {schema: args.manufacture === true ? "delta-cycle-v1" : "delta-harvest-v1", runId: runId, ok: false,
        dryRun: args.dryRun === true, collected: [], stations: [], actions: [],
        purchases: 0, craftStarts: 0};
    var templates = {};
    var samples = {};
    var gamePackage = "com.tencent.tmgp.dfm";
    var entryTimeout = 90000;
    var origins = {"overview":[175,20], "technical-header":[434,237],
        "workbench-header":[870,237], "medical-header":[434,695],
        "armor-header":[870,695], "lobby-workshop":[993,1105], "reward-title":[1243,201],
        "warehouse-full":[1255,362], "mode-selection":[114,235], "event-popup":[566,346],
        "promotion-close":[2470,58], "promotion-cta":[1843,968], "logged-out":[868,515]};
    var lock = new java.io.File(files.join(root, "workshop.lock"));
    var locked = false;
    var started = Date.now();
    var dismissedPopups = 0;
    var workshopOpen = false;
    var stations = [
        {name: "技术中心", header: "technical-header", headerRegion: [420,225,180,65], x:342,y:347,right:729},
        {name: "工作台", header: "workbench-header", headerRegion: [850,225,170,65], x:779,y:347,right:1165},
        {name: "制药台", header: "medical-header", headerRegion: [420,680,150,70], x:342,y:805,right:729},
        {name: "防具台", header: "armor-header", headerRegion: [850,680,160,70], x:779,y:805,right:1165}
    ];

    function snapshot(fn,layout) {
        if (Date.now() - started > (args.manufacture === true ? 240000 : 90000)) throw new Error("RUN_TIMEOUT");
        var hardware = null, frame = null;
        try {
            hardware = automator.captureScreen(layout);
            frame = images.copy(hardware);
            if (frame.getWidth() !== 2712 || frame.getHeight() !== 1220)
                throw new Error("UNSUPPORTED_SCREEN: use landscape 2712x1220");
            if (samples["logged-out"] && match(frame,"logged-out")) {
                save(frame,"logged-out");
                throw new Error("ACCOUNT_LOGGED_OUT");
            }
            return fn(frame);
        } finally {
            if (frame) frame.recycle();
            if (hardware) hardware.recycle();
        }
    }
    function match(frame, name, region) {
        var pts=samples[name];
        var origin=origins[name] || [region[0]+45,region[1]+26];
        var shifts=[[0,0],[-1,0],[1,0],[0,-1],[0,1],[-2,0],[2,0],[0,-2],[0,2]];
        // Resampling a calibrated card can shift its idle label on both axes.
        if(result.calibratedLayout && (name==="idle" || name==="reward-title" || name==="warehouse-full"))
            shifts=shifts.concat([[-1,-1],[-1,1],[1,-1],[1,1]]);
        // The short warehouse toast is 27 px above the older dialog placement.
        if(name==="warehouse-full") shifts=shifts.concat([[0,-27],[-1,-27],[1,-27],[0,-26],[0,-28],[-2,-27],[2,-27]]);
        for(var k=0;k<shifts.length;k++) {
            var left=origin[0]+shifts[k][0],top=origin[1]+shifts[k][1];
            // The disconnect prompt has dark gaps between its light characters.
            if(name==="logged-out" &&
                (((images.pixel(frame,left+11,top+36)>>>8)&255)>=100 ||
                 ((images.pixel(frame,left+900,top+15)>>>8)&255)>=100)) continue;
            var good=0;
            for(var j=0;j<pts.length;j++) {
                var c=images.pixel(frame,left+pts[j][0],top+pts[j][1]);
                var r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
                if(name==="promotion-cta" ? gold(c) :
                    Math.min(r,g,b)>=(name==="idle"?120:140) && Math.max(r,g,b)-Math.min(r,g,b)<85) good++;
            }
            if(good/pts.length>=0.87) return true;
        }
        return false;
    }
    function overview(frame) {
        if (!match(frame,"overview",[160,10,200,90])) return false;
        for (var i=0;i<stations.length;i++)
            if (!match(frame,stations[i].header,stations[i].headerRegion)) return false;
        return true;
    }
    function reward(frame) { return match(frame,"reward-title",[1215,180,280,100]); }
    function gold(color) {
        var r=(color >>> 16)&255, g=(color >>> 8)&255, b=color&255;
        return r>=135 && g>=110 && b<145 && r-b>=50 && g-b>=35 && r>=g;
    }
    function state(frame,s) {
        var hits=0;
        for (var j=0;j<4;j++) {
            var y=s.y+30+j*40;
            if (gold(images.pixel(frame,s.x,y))) hits++;
            if (gold(images.pixel(frame,s.right,y))) hits++;
        }
        if (hits>=7) return "ready";
        if (match(frame,"idle",[s.x+100,s.y+100,200,80])) return "idle";
        return "busy_or_unknown";
    }
    function save(frame,name) { images.save(frame,files.join(out,name+".png")); }
    function checkpoint() { files.write(files.join(out,"result.json"),JSON.stringify(result,null,2)); }
    function checkWarehouse(frame) {
        if(workshopOpen && match(frame,"warehouse-full",[1235,320,260,105])) {
            save(frame,"warehouse-full");
            throw new Error("WAREHOUSE_FULL");
        }
    }
    function waitFor(predicate,timeout,enteringLobby,layout) {
        var end=Date.now()+timeout;
        do {
            if (snapshot(function(f){
                checkWarehouse(f);
                var promotion=enteringLobby && match(f,"promotion-close") && match(f,"promotion-cta");
                if (promotion || match(f,"event-popup",[550,330,300,90])) {
                    if (dismissedPopups>=3) throw new Error("TOO_MANY_POPUPS");
                    dismissedPopups++;
                    tap(2498,81);
                    result.actions.push(promotion?"close_promotion_popup":"close_known_event_popup");
                    return false;
                }
                return predicate(f);
            },layout)) return true;
            sleep(350);
        } while (Date.now()<end);
        return false;
    }
    function tap(x,y,layout) {
        if (!press(x,y,100)) throw new Error("TAP_FAILED");
        if(args.observeTransientPrompts===true) {
            // Capture during the transition: a short rejection toast can disappear
            // before the ordinary post-tap settle delay has elapsed.
            for(var probe=0;probe<3;probe++) {sleep(100);snapshot(checkWarehouse,layout);}
        } else sleep(450);
    }
    function waitForLandscape(timeout) {
        var end=Date.now()+timeout;
        do {
            if (currentPackage()===gamePackage) {
                var frame=automator.captureScreen();
                try {
                    if (frame.getWidth()===2712 && frame.getHeight()===1220) return;
                    if (typeof calibratedLayout==="function" && calibratedLayout() && frame.getWidth()>frame.getHeight()) return;
                } finally {
                    frame.recycle();
                }
            }
            sleep(250);
        } while(Date.now()<end);
        throw new Error("UNSUPPORTED_SCREEN: use landscape 2712x1220");
    }
    function reachOverview() {
        if (snapshot(overview)) return;
        var lobby = function(f) {return match(f,"lobby-workshop",[970,1080,200,100]);};
        var mode = function(f) {return match(f,"mode-selection",[100,220,560,160]);};
        function entryPage(f) {
            if(args.detectPendingPriceChange===true &&
                require(files.join(root,"delta-reprice.js")).dialog(f))
                throw Error("REPRICE_PENDING");
            return overview(f)||lobby(f)||mode(f);
        }
        if (!snapshot(entryPage) && !waitFor(entryPage,entryTimeout,true))
            throw new Error("GAME_PAGE_UNKNOWN: log in and close popups manually");
        if (snapshot(mode)) {
            tap(490,1080);
            if (!waitFor(function(f){return overview(f)||lobby(f);},12000,true)) throw new Error("LOBBY_NOT_FOUND");
            result.actions.push("enter_operations_lobby");
        }
        if (snapshot(overview)) return;
        if (!snapshot(lobby)) throw new Error("LOBBY_CHANGED");
        tap(1047,1131);
        if (!waitFor(overview,8000)) throw new Error("OVERVIEW_NOT_FOUND");
        result.actions.push("open_workshop");
    }

    try {
        files.ensureDir(out+"/");
        if (!lock.createNewFile()) throw new Error("ANOTHER_RUN_OR_STALE_LOCK");
        locked=true;
        result.phase="initializing";
        checkpoint();
        if (!auto.service) throw new Error("ACCESSIBILITY_NOT_RUNNING");
        if (!device.isScreenOn()) throw new Error("SCREEN_OFF");
        var names=["overview","technical-header","workbench-header","medical-header",
            "armor-header","idle","lobby-workshop","reward-title","warehouse-full","mode-selection","event-popup",
            "promotion-close","promotion-cta","logged-out"];
        for (var n=0;n<names.length;n++) {
            templates[names[n]]=android.graphics.BitmapFactory.decodeFile(files.join(root,"assets",names[n]+".png"));
            if (!templates[names[n]]) throw new Error("TEMPLATE_MISSING: "+names[n]);
            var bmp=templates[names[n]],points=[];
            for(var y=2;y<bmp.getHeight()-2;y+=3) for(var x=2;x<bmp.getWidth()-2;x+=3) {
                var color=bmp.getPixel(x,y),r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
                if(names[n]==="promotion-cta" ? gold(color) :
                    Math.min(r,g,b)>=(names[n]==="idle"?140:180) && Math.max(r,g,b)-Math.min(r,g,b)<60) points.push([x,y]);
            }
            if(points.length<12) throw new Error("TEMPLATE_TOO_FEW_ANCHORS: "+names[n]);
            if(points.length>240) {
                var reduced=[],step=Math.ceil(points.length/240);
                for(var p=0;p<points.length;p+=step) reduced.push(points[p]);
                points=reduced;
            }
            samples[names[n]]=points;
        }
        if (args.manufacture === true && !result.dryRun) {
            require(files.join(root,"delta-manufacture.js")).prepareOcr();
        }
        if (currentPackage() !== gamePackage) {
            if (!app.launchPackage(gamePackage)) throw new Error("GAME_LAUNCH_FAILED");
            sleep(2000);
        }
        waitForLandscape(45000);
        if (typeof calibratedLayout==="function" && calibratedLayout()) {
            result.calibratedLayout=true;
            result.actions=prepareWorkshopOverview(entryTimeout);
            if(args.inspectOnly===true) {
                result.overviewCheckOnly=typeof cycleLayoutVerified!=="function" || !cycleLayoutVerified();
                args.timersOnly=true;
            }
            if (args.detectPendingPriceChange===true && snapshot(require(files.join(root,"delta-reprice.js")).dialog,"center"))
                throw new Error("REPRICE_PENDING");
            if (!snapshot(overview)) throw new Error("OPEN_WORKSHOP_FOR_LAYOUT_CHECK");
        } else reachOverview();
        workshopOpen=true;
        if(args.expectedAccount) {
            var currentAccount=snapshot(require(files.join(root,"delta-manufacture.js")).accountId);
            if(currentAccount!==args.expectedAccount) throw new Error("AUTOCYCLE_ACCOUNT_MISMATCH");
        }
        result.phase="collecting";
        checkpoint();
        snapshot(function(f){save(f,"before");});
        for (var i=0;i<stations.length;i++) {
            var station=stations[i];
            var status=null;
            if (!waitFor(function(f){
                if(!overview(f))return false;
                status=state(f,station);return true;
            },10000)) throw new Error("OVERVIEW_OBSCURED");
            var entry={name:station.name,before:status};
            result.stations.push(entry);
            if (result.dryRun || args.inspectOnly === true || args.previewOnly === true || status!=="ready") continue;
            // Revalidate immediately before the only collection tap; never retry it blindly.
            if (!waitFor(function(f){return overview(f)&&state(f,station)==="ready";},10000))
                throw new Error("CARD_CHANGED: "+station.name);
            result.pendingAction={type:"collect",station:station.name};
            checkpoint();
            tap(station.x+190,station.y+100,"center");
            if (!waitFor(reward,8000,false,"center")) throw new Error("REWARD_NOT_CONFIRMED: "+station.name);
            snapshot(function(f){save(f,"reward-"+i);},"center");
            if (!snapshot(reward,"center")) throw new Error("REWARD_PAGE_CHANGED");
            tap(1355,1080);
            if (!waitFor(function(f){return overview(f)&&state(f,station)==="idle";},8000))
                throw new Error("IDLE_NOT_CONFIRMED: "+station.name);
            entry.after="idle";
            entry.rewardConfirmed=true;
            result.collected.push(station.name);
            delete result.pendingAction;
            checkpoint();
        }
        if (args.manufacture === true && !result.dryRun) {
            result.phase="manufacturing";
            checkpoint();
            require(files.join(root,"delta-manufacture.js")).run({
                args:args,result:result,root:root,out:out,stations:stations,
                snapshot:snapshot,overview:overview,state:state,tap:tap,
                waitFor:waitFor,save:save,checkpoint:checkpoint
            });
        }
        result.finalStates=snapshot(function(f){
            if (!overview(f)) throw new Error("FINAL_PAGE_UNKNOWN");
            save(f,"after");
            return stations.map(function(s){return {name:s.name,state:state(f,s)};});
        });
        if (!result.dryRun && args.inspectOnly !== true && args.previewOnly !== true && result.finalStates.some(function(s){return s.state==="ready";}))
            throw new Error("UNCLAIMED_REWARD_REMAINS");
        result.phase=result.needsApproval ? "awaiting_approval" : "finished";
        result.ok=!result.needsApproval;
    } catch (e) {
        result.phase="failed";
        result.error=String(e);
        if(result.error.indexOf("WAREHOUSE_FULL")>=0 && result.pendingAction && result.pendingAction.type==="collect") {
            result.blockedAction=result.pendingAction;
            delete result.pendingAction;
        }
        try {snapshot(function(f){save(f,"error");});} catch (_) {}
    } finally {
        result.durationMs=Date.now()-started;
        result.finishedAt=new Date().toISOString();
        result.evidenceDirectory=out;
        try {files.write(files.join(out,"result.json"),JSON.stringify(result,null,2));} catch (_) {}
        Object.keys(templates).forEach(function(k){if(templates[k]) templates[k].recycle();});
        if (locked) lock.delete();
        if(args.returnToPalmClaw === true) {
            try {
                var returnIntent=context.getPackageManager().getLaunchIntentForPackage("com.palmclaw");
                if(returnIntent) {
                    returnIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    context.startActivity(returnIntent);
                }
            } catch (_) {}
        }
        console.log(JSON.stringify(result));
    }
    return result;
};
