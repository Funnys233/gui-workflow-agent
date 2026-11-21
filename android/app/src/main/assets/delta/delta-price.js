/* Read this game's fixed price font. Unknown or ambiguous glyphs fail closed. */
var model=null;
module.exports=function(frame) {
    var root=files.exists(files.join(files.cwd(),"delta-price.js"))?files.cwd():files.join(files.cwd(),"delta");
    if(!model) {
        model=JSON.parse(files.read(files.join(root,"assets/price-digits.json")));
        if(model.schema!=="delta-price-font-v1" || model.digits.length<10)
            throw new Error("PRICE_FONT_INCOMPLETE");
        for(var d=0;d<10;d++) if(!model.digits.some(function(t){return t.digit===String(d);}))
            throw new Error("PRICE_FONT_DIGIT_MISSING: "+d);
    }
    var width=415,height=55,mask=[],columns=[];
    for(var x=0;x<width;x++) {
        var top=height,bottom=-1;
        for(var y=0;y<height;y++) {
            var c=images.pixel(frame,1150+x,965+y),r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
            var ink=g>=150 && g-r>=35 && g-b>=20;
            mask[y*width+x]=ink;
            if(ink){top=Math.min(top,y);bottom=Math.max(bottom,y);}
        }
        if(bottom>=0) columns.push({x:x,top:top,bottom:bottom});
    }
    var groups=[];
    columns.forEach(function(c){
        var last=groups.length?groups[groups.length-1]:null;
        if(!last || c.x>last.right+1) groups.push({left:c.x,right:c.x,top:c.top,bottom:c.bottom});
        else {last.right=c.x;last.top=Math.min(last.top,c.top);last.bottom=Math.max(last.bottom,c.bottom);}
    });
    if(groups.length<1 || groups.length>12) throw new Error("PRICE_GLYPH_LAYOUT_UNKNOWN");
    var maxHeight=Math.max.apply(null,groups.map(function(g){return g.bottom-g.top+1;}));
    var text="";
    groups.forEach(function(g,index){
        var w=g.right-g.left+1,h=g.bottom-g.top+1;
        if(h<maxHeight*0.6) {
            if(w<3 || w>8 || g.bottom<maxHeight-2) throw new Error("PRICE_COMMA_UNKNOWN");
            text+=",";return;
        }
        var glyph="";
        for(var v=0;v<model.height;v++) for(var u=0;u<model.width;u++) {
            var xx=g.left+Math.min(w-1,Math.floor((u+0.5)*w/model.width));
            var yy=g.top+Math.min(h-1,Math.floor((v+0.5)*h/model.height));
            glyph+=mask[yy*width+xx]?"1":"0";
        }
        var scores={};
        model.digits.forEach(function(t){
            if(Math.abs(t.w-w)>3 || Math.abs(t.h-h)>2) return;
            var union=0,intersection=0;
            for(var k=0;k<glyph.length;k++) {
                if(glyph[k]==="1" || t.mask[k]==="1")union++;
                if(glyph[k]==="1" && t.mask[k]==="1")intersection++;
            }
            var score=union?intersection/union:0;
            scores[t.digit]=Math.max(scores[t.digit]||0,score);
        });
        var ranked=Object.keys(scores).map(function(d){return {digit:d,score:scores[d]};})
            .sort(function(a,b){return b.score-a.score;});
        if(!ranked.length || ranked[0].score<0.80 ||
            (ranked.length>1 && ranked[0].score-ranked[1].score<0.07))
            throw new Error("PRICE_DIGIT_UNCERTAIN: "+index);
        text+=ranked[0].digit;
    });
    if(!/^(?:[1-9]\d{0,2}(?:,\d{3})+|[1-9]\d{0,8})$/.test(text))
        throw new Error("PRICE_NUMBER_FORMAT_UNKNOWN: "+text);
    return Number(text.replace(/,/g,""));
};
