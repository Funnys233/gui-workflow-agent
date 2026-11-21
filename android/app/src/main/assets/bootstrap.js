/* Private compatibility surface: only the bundled, audited workshop modules run here. */
var java=Packages.java, android=Packages.android, context=host.getContext();
var files={
 cwd:function(){return String(host.root());},
 join:function(){return Array.prototype.slice.call(arguments).map(String).join('/').replace(/\/+/g,'/');},
 exists:function(p){return host.exists(String(p));},
 read:function(p){return String(host.read(String(p)));},
 write:function(p,s){host.write(String(p),String(s));},
 ensureDir:function(p){host.ensure(String(p));}
};
var images={copy:function(b){return host.copy(b);},pixel:function(b,x,y){return host.pixel(b,x,y);},save:function(b,p){host.save(b,String(p));}};
var automator={captureScreen:function(layout){return host.capture(layout||'overview');}};
var auto={service:true};
var device={isScreenOn:function(){return host.screenOn()&&host.unlocked();}};
var app={launchPackage:function(p){return host.launch(String(p));}};
function currentPackage(){return String(host.currentPackage());}
function calibratedLayout(){return host.calibratedLayout();}
function cycleLayoutVerified(){return host.cycleLayoutVerified();}
function prepareWorkshopOverview(timeout){return JSON.parse(String(host.prepareOverview(timeout)));}
function sleep(ms){host.sleep(ms);}
function press(x,y,ms){return host.press(x,y,ms);}
var ocr={mode:'mlkit',detect:function(frame,options){
 var r=options&&options.region;
 return JSON.parse(String(host.detect(frame,r?r[0]:0,r?r[1]:0,r?r[2]:0,r?r[3]:0)));
}};
var console={log:function(s){host.log(String(s));}};
var moduleCache={};
function require(path){
 path=String(path);if(moduleCache[path])return moduleCache[path].exports;
 var module={exports:{}};moduleCache[path]=module;
 var body=files.read(path);
 new Function('module','exports','require',body)(module,module.exports,require);
 return module.exports;
}
