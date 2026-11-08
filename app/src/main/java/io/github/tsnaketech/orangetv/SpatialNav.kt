package io.github.tsnaketech.orangetv

import org.json.JSONObject

/**
 * Remote-control navigation injected into the page: the arrows move from one clickable element
 * to the nearest one in the chosen direction, OK clicks it.
 * The script installs only once per page (window.__tvnav guard).
 *
 * shortcut: elements inside iframes are not reachable; switch to cursor mode (Menu) in that case.
 */
class SpatialNav(site: Site) {
    // The channel ± buttons are hidden: the remote's channel keys replace them.
    private val css = listOfNotNull(site.nextChannel, site.previousChannel)
        .joinToString(",") { "button[aria-label=\"$it\"]" }
        .let { if (it.isEmpty()) site.css else "$it{display:none!important}${site.css}" }

    private val SCRIPT = """
(function(){
if(window.__tvnav)return;
var CSS=${JSONObject.quote(css)},PLAY=new RegExp(${JSONObject.quote(site.playLabel)},'i'),
  CLOSE=new RegExp(${JSONObject.quote(site.closeLabel)},'i'),
  NEXT=${JSONObject.quote(site.nextChannel.orEmpty())},PREV=${JSONObject.quote(site.previousChannel.orEmpty())},
  CAR_PREV=${JSONObject.quote(site.carouselPrev)},CAR_NEXT=${JSONObject.quote(site.carouselNext)};
var SEL='a[href],button,input:not([type=hidden]),select,textarea,summary,[role=button],[role=link],[role=tab],[role=menuitem],[role=option],[role=checkbox],[role=switch],[tabindex]:not([tabindex="-1"]),[onclick]'+
  ${JSONObject.quote(if (site.clickable.isEmpty()) "" else "," + site.clickable)};
var cur=null,opener=null;
var st=document.createElement('style');
// Thin white outline following the element's rounding, dark outline to stand out on light backgrounds, light halo.
st.textContent='.__tvf{outline:3px solid rgba(255,255,255,.95)!important;outline-offset:4px!important;'+
  'box-shadow:0 0 0 4px rgba(0,0,0,.35),0 0 12px 0 rgba(255,255,255,.2)!important}'+CSS;
(document.head||document.documentElement).appendChild(st);

// Button name as a user sees it; some sites only put it in an icon's alt text.
function label(e){
  var i=e.querySelector&&e.querySelector('img[alt]');
  return (e.getAttribute('aria-label')||e.title||e.textContent||'').trim()||(i?i.alt:'');
}

function shown(el,r){
  if(r.width<4||r.height<4)return false;
  var s=getComputedStyle(el);
  if(s.visibility==='hidden'||+s.opacity===0||s.pointerEvents==='none')return false;
  var cx=r.left+r.width/2,cy=r.top+r.height/2;
  if(cx<0||cy<0||cx>=innerWidth||cy>=innerHeight)return !clipped(el,cx,cy); // off screen: overlap cannot be tested
  var t=document.elementFromPoint(cx,cy);
  return !!t&&(t===el||el.contains(t)||t.contains(el));
}
// Hidden by an overflow:hidden ancestor (carousel items parked outside their row): scrolling never reveals them.
// Scrollable (auto/scroll) ancestors are left alone: scrollIntoView reaches their items.
function clipped(el,cx,cy){
  for(var p=el.parentElement;p&&p!==document.body;p=p.parentElement){
    var s=getComputedStyle(p);
    if(!/hidden|clip/.test(s.overflowX+s.overflowY))continue;
    var q=p.getBoundingClientRect();
    if(cx<q.left||cx>q.right||cy<q.top||cy>q.bottom)return true;
  }
  return false;
}
function candidates(){
  var set=new Set(document.querySelectorAll(SEL));
  // Modern sites often make div "buttons": spot them by their hand cursor.
  var ptr=new Set(),all=document.body?document.body.getElementsByTagName('*'):[];
  for(var i=0;i<all.length;i++){
    var e=all[i];
    if(getComputedStyle(e).cursor==='pointer'){ptr.add(e);if(!ptr.has(e.parentElement))set.add(e);}
  }
  var out=[];
  set.forEach(function(e){
    if(e.disabled)return;
    var r=e.getBoundingClientRect();
    if(!shown(e,r))return;
    // A child covering the same area as its clickable parent is a duplicate.
    for(var p=e.parentElement;p;p=p.parentElement){
      if(set.has(p)){var q=p.getBoundingClientRect();
        if(Math.abs(q.left-r.left)<3&&Math.abs(q.top-r.top)<3&&Math.abs(q.width-r.width)<3&&Math.abs(q.height-r.height)<3)return;
        break;}
    }
    out.push({e:e,r:r});
  });
  return out;
}
function gap(a1,a2,b1,b2){return Math.max(0,a1-b2,b1-a2);}
function inView(r){return r.top>=0&&r.left>=0&&r.bottom<=innerHeight&&r.right<=innerWidth;}
// Rounds the frame of square-cornered elements; follows the shape of a round child (circular button
// in a square wrapper). The original rounding is restored on deselection.
function round(el,on){
  if(!on){if(el.__tvr!==undefined){el.style.borderRadius=el.__tvr;delete el.__tvr;}return;}
  if(parseFloat(getComputedStyle(el).borderTopLeftRadius)>0)return;
  var c=el.firstElementChild,cr=c&&getComputedStyle(c).borderTopLeftRadius;
  el.__tvr=el.style.borderRadius;
  el.style.borderRadius=cr&&parseFloat(cr)>0?cr:'10px';
}
function select(el){
  if(cur){cur.classList.remove('__tvf');round(cur,false);}
  cur=el;el.classList.add('__tvf');round(el,true);
  if(!inView(el.getBoundingClientRect()))el.scrollIntoView({block:'center',inline:'center'});
  el.dispatchEvent(new MouseEvent('mouseover',{bubbles:true}));
}
function scrollable(el,v){
  for(;el&&el!==document.body;el=el.parentElement){
    var s=getComputedStyle(el),o=v?s.overflowY:s.overflowX;
    if((o==='auto'||o==='scroll')&&(v?el.scrollHeight>el.clientHeight:el.scrollWidth>el.clientWidth))return el;
  }
  return document.scrollingElement||document.documentElement;
}
function move(dir,retry){
  if(cur&&!cur.isConnected)cur=null;
  // On the progress bar (full width), left/right seek back/forward 10 s instead of navigating.
  if(cur&&(dir==='left'||dir==='right')&&(cur.type==='range'||cur.getAttribute('role')==='slider')&&videoMode()){
    seek(dir==='left'?-10:10);return;
  }
  var list=candidates(),best=null,bs=Infinity;
  if(!cur){
    // In the player, start on Play/Pause rather than on "Retour".
    if(videoMode())list.forEach(function(c){
      if(!best&&PLAY.test(label(c.e)))best=c.e;
    });
    if(!best)list.forEach(function(c){if(inView(c.r)){var s=c.r.top*2+c.r.left;if(s<bs){bs=s;best=c.e;}}});
  }else{
    var f=cur.getBoundingClientRect();
    list.forEach(function(c){
      if(c.e===cur||c.e.contains(cur)||cur.contains(c.e))return;
      var r=c.r,main,cross;
      if(dir==='right'){main=r.left-f.right;cross=gap(r.top,r.bottom,f.top,f.bottom);}
      else if(dir==='left'){main=f.left-r.right;cross=gap(r.top,r.bottom,f.top,f.bottom);}
      else if(dir==='down'){main=r.top-f.bottom;cross=gap(r.left,r.right,f.left,f.right);}
      else{main=f.top-r.bottom;cross=gap(r.left,r.right,f.left,f.right);}
      if(main<-4)return;
      // Left/right stay on the same "row": at the end of a row, do not jump elsewhere.
      if((dir==='left'||dir==='right')&&cross>innerHeight/3)return;
      // Edge-to-edge distance, with sideways offset weighing a bit more than forward distance.
      var m=Math.max(main,0),s=Math.sqrt(m*m+cross*cross*2.25);
      if(s<bs){bs=s;best=c.e;}
    });
  }
  if(best){select(best);return;}
  // End of a carousel row: slide it with its own arrow, then move again once the new items are in place.
  var car=(dir==='left'?CAR_PREV:dir==='right'?CAR_NEXT:'');
  if(car&&cur&&!retry){
    for(var p=cur.parentElement;p;p=p.parentElement){
      var a=p.querySelector(car);
      if(!a)continue;
      // Only the arrow of the current row (same height band), never one of another carousel.
      var ar=a.getBoundingClientRect(),cr=cur.getBoundingClientRect();
      if(ar.bottom<cr.top||ar.top>cr.bottom)break;
      a.click();setTimeout(function(){move(dir,true);},450);return;
    }
  }
  // Nothing in that direction: scroll the area containing the current element.
  var v=dir==='up'||dir==='down',sign=(dir==='up'||dir==='left')?-1:1;
  var box=scrollable(cur,v);
  box.scrollBy(v?0:sign*innerWidth*0.6,v?sign*innerHeight*0.6:0);
}
// A video covers more than half the screen: we are in the player.
function videoMode(){
  return [].some.call(document.querySelectorAll('video'),function(v){
    var r=v.getBoundingClientRect();
    return Math.max(0,Math.min(r.right,innerWidth)-Math.max(r.left,0))*Math.max(0,Math.min(r.bottom,innerHeight)-Math.max(r.top,0))>innerWidth*innerHeight/2;
  });
}
// In live, the duration is infinite: stay within the seekable window (the site's time-shift).
function seek(sec){
  var v=document.querySelector('video');
  if(!v||!v.seekable.length)return false;
  var lo=v.seekable.start(0),hi=v.seekable.end(v.seekable.length-1);
  v.currentTime=Math.min(hi,Math.max(lo,v.currentTime+sec));
  return true;
}
// Back key: first closes an open panel (settings, channel list…) via its
// "Fermer" / "Masquer…" button. That button may be invisible (screen readers only):
// in that case check that its container is shown.
function closeOverlay(){
  var b=[].slice.call(document.querySelectorAll('button,[role=button],a')).filter(function(e){
    if(!CLOSE.test(label(e)))return false;
    var r=e.getBoundingClientRect();
    if(r.width>2&&r.height>2)return inView(r)&&shown(e,r);
    var p=e.parentElement&&e.parentElement.getBoundingClientRect();
    return !!p&&p.width>50&&p.height>20&&p.bottom>0&&p.top<innerHeight&&p.right>0&&p.left<innerWidth;
  })[0];
  var o=opener;
  if(!b){
    // Dropdown menu (e.g. "Gérer mon compte"): click its expanded button again to collapse it.
    b=[].slice.call(document.querySelectorAll('[aria-expanded="true"]')).filter(function(e){
      var r=e.getBoundingClientRect();return inView(r)&&shown(e,r);
    })[0];
    if(!b)return false;
    o=b;
  }
  b.click();
  setTimeout(function(){
    if(cur&&!cur.isConnected)cur=null;
    if(o&&o.isConnected)select(o); // go back to the button that opened the panel
  },300);
  return true;
}
function zap(up){
  var l=up?NEXT:PREV,b=l&&[].slice.call(document.querySelectorAll('button[aria-label]')).filter(function(e){
    return e.getAttribute('aria-label')===l;
  })[0];
  if(b)b.click();
  return !!b;
}
function center(){
  if(!cur||!cur.isConnected)return null;
  opener=cur;
  var r=cur.getBoundingClientRect();
  if(!inView(r)){cur.scrollIntoView({block:'center',inline:'center'});r=cur.getBoundingClientRect();}
  return [(r.left+r.width/2)/innerWidth,(r.top+r.height/2)/innerHeight];
}
window.__tvnav={move:move,center:center,videoMode:videoMode,seek:seek,closeOverlay:closeOverlay,zap:zap,
  current:function(){return cur;}};
})();
"""

    /** Installs the script (and its styles) without doing anything else. */
    val install: String get() = SCRIPT

    fun move(dir: String) ="$SCRIPT;window.__tvnav.move('$dir')"

    /** Returns "[fx,fy]" (relative 0..1 position of the selected element) or "null". */
    fun center() = "$SCRIPT;window.__tvnav.center()"

    /** Returns "true" if a video fills most of the screen (player open). */
    fun videoMode() = "$SCRIPT;window.__tvnav.videoMode()"

    fun seek(seconds: Int) = "$SCRIPT;window.__tvnav.seek($seconds)"

    /** Returns "true" if a panel was closed. */
    fun closeOverlay() = "$SCRIPT;window.__tvnav.closeOverlay()"

    fun zap(up: Boolean) ="$SCRIPT;window.__tvnav.zap($up)"
}
