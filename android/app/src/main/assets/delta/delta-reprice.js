/* Recognize the game's partial-fill confirmation and reserve a conservative cost. */
var model=null;
function root() {
    return files.exists(files.join(files.cwd(),"delta-reprice.js"))?files.cwd():files.join(files.cwd(),"delta");
}
function patterns() {
    if(!model) model=JSON.parse(files.read(files.join(root(),"assets/reprice-patterns.json")));
    if(model.schema!=="delta-reprice-patterns-v1") throw Error("REPRICE_PATTERNS_INVALID");
    return model;
}
function red(c) {
    var r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
    return r>=140 && r-g>=60 && r-b>=60;
}
function gray(c) {
    var r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
    return Math.min(r,g,b)>=115 && Math.max(r,g,b)-Math.min(r,g,b)<65;
}
function dialog(frame) {
    var t=patterns().title,k=0,yes=0,no=0,pos=0,neg=0;
    for(var y=0;y<t.h;y+=t.step) for(var x=0;x<t.w;x+=t.step) {
        var ink=gray(images.pixel(frame,t.x+x,t.y+y));
        if(t.mask[k++]==="1") {pos++;if(ink)yes++;}
        else {neg++;if(!ink)no++;}
    }
    return pos>=12 && yes/pos>=0.88 && no/neg>=0.90;
}
function groups(values) {
    var result=[];
    values.forEach(function(v){
        var last=result.length?result[result.length-1]:null;
        if(!last || v>last[1]+1) result.push([v,v]);else last[1]=v;
    });
    return result;
}
function symbol(frame,g,t) {
    var w=g.right-g.left+1,h=g.bottom-g.top+1;
    if(Math.abs(w-t.w)>2 || Math.abs(h-t.h)>2) return false;
    var union=0,intersection=0,k=0;
    for(var y=0;y<t.h;y++) for(var x=0;x<t.w;x++) {
        var ink=red(images.pixel(frame,g.left+Math.min(w-1,Math.floor((x+0.5)*w/t.w)),
            g.top+Math.min(h-1,Math.floor((y+0.5)*h/t.h))));
        var ref=t.mask[k++]==="1";
        if(ink||ref)union++;if(ink&&ref)intersection++;
    }
    return union>0 && intersection/union>=0.82;
}
function inspect(frame,items) {
    if(!dialog(frame)) throw Error("REPRICE_DIALOG_UNKNOWN");
    var receipts=items.filter(function(i){return String(i.text).replace(/\s/g,"")==="购买成功" &&
        i.left>=390 && i.left<750 && i.top>230 && i.top<820;});
    // The success heading must be visible below every changed row; no scrolling or hidden amounts.
    if(receipts.length!==1) throw Error("REPRICE_RECEIPT_NOT_VISIBLE");
    var bottom=Math.floor(receipts[0].top)-10,ys=[],rows=[];
    for(var y=240;y<bottom;y++) {
        for(var x=2000;x<2250;x++) if(red(images.pixel(frame,x,y))) {ys.push(y);break;}
    }
    groups(ys).forEach(function(band){
        var cols=[];
        for(var x=2000;x<2250;x++) {
            var top=bottom,last=-1;
            for(var y=band[0];y<=band[1];y++) if(red(images.pixel(frame,x,y))) {
                top=Math.min(top,y);last=Math.max(last,y);
            }
            if(last>=0)cols.push({x:x,top:top,bottom:last});
        }
        var glyphs=[];
        cols.forEach(function(c){
            var g=glyphs.length?glyphs[glyphs.length-1]:null;
            if(!g || c.x>g.right+1)glyphs.push({left:c.x,right:c.x,top:c.top,bottom:c.bottom});
            else {g.right=c.x;g.top=Math.min(g.top,c.top);g.bottom=Math.max(g.bottom,c.bottom);}
        });
        var n=glyphs.length,digits=n-4,refs=patterns().symbols;
        if(digits<1 || digits>3 || glyphs[0].left<=2000 || glyphs[n-1].right>=2249 ||
            !symbol(frame,glyphs[0],refs[0]) || !symbol(frame,glyphs[1],refs[1]) ||
            !symbol(frame,glyphs[n-2],refs[2]) || !symbol(frame,glyphs[n-1],refs[3]))
            throw Error("REPRICE_PERCENT_LAYOUT_UNKNOWN");
        for(var j=2;j<n-2;j++) {
            var g=glyphs[j];
            if(g.bottom-g.top<20 || g.right-g.left<3 || g.right-g.left>22)
                throw Error("REPRICE_PERCENT_DIGIT_UNKNOWN");
        }
        var center=Math.round((band[0]+band[1])/2),checked=0;
        for(var yy=center-11;yy<=center+11;yy++) for(var xx=501;xx<=528;xx++)
            if(gray(images.pixel(frame,xx,yy)))checked++;
        if(checked<30)throw Error("REPRICE_ITEM_NOT_SELECTED");
        rows.push({digits:digits});
    });
    if(!rows.length || rows.length>3)throw Error("REPRICE_CHANGED_ROWS_UNKNOWN");
    var remaining=require(files.join(root(),"delta-price.js"))(frame);
    // N displayed digits bound a positive rate below 10^N percent, including rounding.
    // For a one-digit rate, extra charge is at most remaining * 10 / 110.
    // Do not guess exact partial-fill spending from the old quote or OCR percentages.
    var maxPercent=Math.pow(10,Math.max.apply(null,rows.map(function(r){return r.digits;})));
    return {remainingCost:remaining,increaseUpperBound:Math.ceil(remaining*maxPercent/(100+maxPercent)),
        changedRows:rows.length,maxPercentExclusive:maxPercent};
}
module.exports={dialog:dialog,inspect:inspect};
