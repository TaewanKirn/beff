package com.beff.app.data

import com.beff.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * 카카오맵 JavaScript API를 WebView에 띄울 페이지를 만든다.
 * - 카카오맵 Android SDK는 ARM 네이티브 라이브러리만 있어 x86_64 에뮬레이터에서 못 돈다(2.15.2 기준, 2026-11-09 확인). 그래서 JS API를 쓴다.
 * - 인증키는 BuildConfig.KAKAO_JS_KEY(빌드 때 환경변수/local.properties에서 주입). 없으면 [enabled]가 false이고 WebView를 띄우지 않는다.
 * - JS 키는 카카오 개발자 콘솔의 플랫폼 > Web 사이트 도메인에 [BASE_URL]이 등록돼 있어야 동작한다.
 *   WebView는 이 주소에 접속하지 않는다(페이지는 앱이 직접 넣고, 주소는 카카오가 확인하는 출처로만 쓰인다). 콘솔이 localhost를 받지 않아 도메인 형식으로 둔다.
 * - 지도 이미지는 저장하지 않는다. 카카오 로고·저작권 표기는 지도 안에 카카오가 직접 그린다.
 */
object KakaoMapPage {
    const val BASE_URL = "https://taewankirn.github.io/"
    const val ATTRIBUTION = "지도: 카카오"

    val enabled: Boolean get() = BuildConfig.KAKAO_JS_KEY.isNotBlank()

    /**
     * [start] 출발점, [path] 동선(첫·끝 구간은 보행로 밖 직선 연결이라 점선), [dest] 도착 화장실, [others] 다른 화장실.
     * 페이지는 준비되면 Android.onReady(), 실패하면 Android.onError(메시지)를 부른다. 지도 종류는 setType('road'|'sky'), 확대 · 축소는 zoom(-1|1)로 바꾼다.
     */
    fun html(start: GeoPoint, path: List<GeoPoint>?, dest: GeoPoint?, others: List<GeoPoint>, stepAt: List<Int> = emptyList()): String {
        fun arr(p: GeoPoint) = JSONArray().put(p.lat).put(p.lng)
        fun arrs(ps: List<GeoPoint>) = JSONArray().apply { ps.forEach { put(arr(it)) } }
        val data = JSONObject()
            .put("start", arr(start))
            .put("path", path?.let { arrs(it) } ?: JSONArray())
            .put("dest", dest?.let { arr(it) } ?: JSONObject.NULL)
            .put("others", arrs(others))
            .put("steps", JSONArray().apply { stepAt.forEach { put(it) } })
        return PAGE.replace("__KEY__", BuildConfig.KAKAO_JS_KEY).replace("__DATA__", data.toString())
    }

    /**
     * 도착 화면 시설 지도. 시설 마커를 모두 깔고, focusK('elevator') 하면 그 종류 마커만 크게 · 틸로 바꾸고 기준점에서의 경로선을 그려 가운데로 맞춘다.
     * resetFocus()는 처음 상태로. 확대 · 지도 종류는 [html] 페이지와 같다(zoom, setType).
     */
    fun facilityHtml(plan: FacilityPlan): String {
        fun arr(p: GeoPoint) = JSONArray().put(p.lat).put(p.lng)
        val pois = JSONArray().apply {
            plan.pois.forEach { put(JSONObject().put("k", it.kind.key).put("g", it.kind.glyph).put("c", it.kind.caution).put("p", arr(it.point))) }
        }
        val routes = JSONObject().apply {
            plan.routes.forEach { (k, r) ->
                put(
                    k.key,
                    JSONObject().put("direct", r.direct).put("t", plan.pois.indexOf(r.target))
                        .put("path", JSONArray().apply { r.path.forEach { put(arr(it)) } })
                        .put("hz", JSONArray().apply { r.cautions.forEach { c -> put(plan.pois.indexOf(c)) } }),
                )
            }
        }
        // 경로가 없는 종류(기준점이 바로 그 시설인 주차 등)는 가까운 3곳을 보이게 맞춘다.
        val near = JSONObject().apply {
            plan.pois.groupBy { it.kind }.forEach { (k, ps) ->
                put(k.key, JSONArray().apply { ps.sortedBy { distanceKm(plan.start, it.point) }.take(3).forEach { put(arr(it.point)) } })
            }
        }
        val data = JSONObject().put("start", arr(plan.start)).put("pois", pois).put("routes", routes).put("near", near)
        return FACILITY_PAGE.replace("__KEY__", BuildConfig.KAKAO_JS_KEY).replace("__DATA__", data.toString())
    }

    private val FACILITY_PAGE = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
html,body,#map{margin:0;padding:0;width:100%;height:100%;overflow:hidden}
.poi,.hz,.me,.dot,.wc,.wc2{transform:scale(var(--inv,1))}
.poi{width:30px;height:30px;border-radius:50%;background:#fff;border:2.5px solid #5B6670;display:flex;align-items:center;justify-content:center;font:700 15px sans-serif;color:#2B3137;box-shadow:0 1px 4px rgba(0,0,0,.25);transition:all .25s}
.poi.on{width:56px;height:56px;background:#008E8C;border:4px solid #fff;color:#fff;font-size:28px;box-shadow:0 6px 16px rgba(0,0,0,.4)}
.poi.dim{opacity:.35;transform:scale(calc(var(--inv,1)*.8))}
.hz{width:26px;height:26px;border-radius:7px;background:#FFF4E5;border:2.5px solid #E8590C;display:flex;align-items:center;justify-content:center;font:700 14px sans-serif;color:#E8590C;box-shadow:0 1px 4px rgba(0,0,0,.25);transition:all .25s}
.hz.on{background:#E8590C;color:#fff;border-color:#fff}
.me{width:24px;height:24px;border-radius:50%;background:#1E66F5;border:4px solid #fff;box-shadow:0 0 0 7px rgba(30,102,245,.25)}
${com.beff.app.data.MapTiles.CSS}</style></head><body>${com.beff.app.data.MapTiles.SVG}<div id="map"></div>
<script>
var D=__DATA__, map=null, TEAL='#008E8C', M=[], R=[], bounds=null, pts=[], extra=1, MAX_EXTRA=2;
function fail(m){try{Android.onError(String(m))}catch(e){}}
window.onerror=function(m){fail(m)};
function ll(p){return new kakao.maps.LatLng(p[0],p[1])}
function line(ps,w,c,style,arrow,z){var l=new kakao.maps.Polyline({map:map,path:ps.map(ll),strokeWeight:w,strokeColor:c,strokeOpacity:1,strokeStyle:style,endArrow:!!arrow,zIndex:z}); R.push(l)}
function setPts(list){bounds=new kakao.maps.LatLngBounds(); pts=[]; list.forEach(function(p){var q=ll(p); bounds.extend(q); pts.push(q)})}
function initialPts(){var l=[D.start]; for(var k in D.routes){var p=D.routes[k].path; l.push(p[p.length-1])} if(l.length<2){for(var n in D.near){if(n!=='stairs'&&n!=='slope')l=l.concat(D.near[n].slice(0,1))}} return l}
function init(){
  map=new kakao.maps.Map(document.getElementById('map'),{center:ll(D.start),level:3,draggable:true,zoomable:true,scrollwheel:false,disableDoubleClickZoom:false,keyboardShortcuts:false});
  D.pois.forEach(function(q){
    var el=document.createElement('div'); el.className=q.c?'hz':'poi'; el.textContent=q.g;
    var ov=new kakao.maps.CustomOverlay({map:map,position:ll(q.p),content:el,xAnchor:0.5,yAnchor:0.5,zIndex:q.c?3:2});
    M.push({k:q.k,c:q.c,ov:ov,el:el});
  });
  var me=document.createElement('div'); me.className='me';
  new kakao.maps.CustomOverlay({map:map,position:ll(D.start),content:me,xAnchor:0.5,yAnchor:0.5,zIndex:9});
  setPts(initialPts()); fit();
  window.addEventListener('resize',function(){map.relayout(); fit()});
  try{Android.onReady()}catch(e){}
}
function clearRoute(){R.forEach(function(l){l.setMap(null)}); R=[]}
function focusK(k){
  if(!map)return; clearRoute();
  // 위치가 없는 시설: 기저귀 교환대 · 수유실은 대개 화장실 안이라 화장실을 대신 보여 준다.
  if(!D.routes[k]&&!D.near[k]&&(k==='changing'||k==='nursing')&&(D.routes.toilet||D.near.toilet)) k='toilet';
  // 그래도 없으면 점 하나(출발점)로 끝까지 확대하지 않고 처음 범위 그대로, 마커도 흐리게 하지 않는다(1113).
  if(!D.routes[k]&&!D.near[k]){resetFocus();return}
  var r=D.routes[k], list=[D.start], hz=r?r.hz:[];
  // 주의 표시(계단 · 경사)는 흐리게 하지 않고, 이 경로 위의 것만 크게 한다.
  M.forEach(function(m,i){
    if(m.c){var o=hz.indexOf(i)>=0; m.el.className='hz'+(o?' on':''); m.ov.setZIndex(o?7:3); return}
    // 1113: 이름 알약은 길이 닿는 한 곳(없으면 가장 가까운 한 곳)만. 같은 종류 다른 곳은 보통 크기로 둔다.
    var on=r?(i===r.t):(i===nearestOf(k)); var same=m.k===k;
    m.el.className='poi'+(on?' on':(same?'':' dim')); m.ov.setZIndex(on?6:(same?2:1))});
  if(r){
    var p=r.path;
    if(r.direct){line(p,10,'#fff','shortdash',false,3); line(p,5,TEAL,'shortdash',true,4)}
    else{line(p,13,'#fff','solid',false,3); line(p,7,TEAL,'solid',true,4)}
    list=list.concat(p);
    if(r.t>=0) list.push(D.pois[r.t].p);
  } else if(D.near[k]) list=list.concat(D.near[k].slice(0,1));
  setPts(list); fit();
}
function nearestOf(k){var best=-1,bd=1e9; D.pois.forEach(function(q,i){if(q.k!==k)return; var d=Math.pow(q.p[0]-D.start[0],2)+Math.pow(q.p[1]-D.start[1],2); if(d<bd){bd=d;best=i}}); return best}
function resetFocus(){if(!map)return; clearRoute(); M.forEach(function(m){m.el.className=m.c?'hz':'poi'; m.ov.setZIndex(m.c?3:2)}); setPts(initialPts()); fit()}
// 1113: 안쪽 여백(CSS px) — 위: 지도 종류 토글, 오른쪽: 확대 · 원위치 버튼, 아래: 이름 알약.
var PAD={t:110,r:110,b:110,l:50};
function inView(){var el=document.getElementById('map'),w=el.clientWidth,h=el.clientHeight,pr=map.getProjection();
  for(var i=0;i<pts.length;i++){var c=pr.containerPointFromCoords(pts[i]); if(c.x<PAD.l||c.x>w-PAD.r||c.y<PAD.t||c.y>h-PAD.b)return false} return true}
function anchor(){if(!bounds)return map.getCenter(); var sw=bounds.getSouthWest(),ne=bounds.getNorthEast(); return new kakao.maps.LatLng((sw.getLat()+ne.getLat())/2,(sw.getLng()+ne.getLng())/2)}
function fit(){
  if(!bounds)return;
  extra=1; applyExtra();
  map.setBounds(bounds,PAD.t,PAD.r,PAD.b,PAD.l);
  // 안쪽 사각형에 다 들어가는 가장 가까운 단계까지 확대한다(걸어서 갈 거리면 1~2단계까지).
  while(map.getLevel()>1){var l=map.getLevel(); map.setLevel(l-1,{anchor:anchor()}); if(!inView()){map.setLevel(l,{anchor:anchor()}); break}}
  // 안쪽 사각형의 가운데에 오도록 살짝 옮긴다.
  var el=document.getElementById('map'), pr=map.getProjection(), c=pr.containerPointFromCoords(anchor());
  var cx=(PAD.l+el.clientWidth-PAD.r)/2, cy=(PAD.t+el.clientHeight-PAD.b)/2;
  map.setCenter(pr.coordsFromContainerPoint(new kakao.maps.Point(el.clientWidth/2-(cx-c.x), el.clientHeight/2-(cy-c.y))));
}
function applyExtra(){document.getElementById('map').style.transform='scale('+extra+')';document.documentElement.style.setProperty('--inv',1/extra)}
/** 원위치: 끌거나 확대한 지도를 처음 보이던 범위(지금 고른 시설 포함)로 되돌린다. */
function recenter(){if(!map)return; extra=1; applyExtra(); fit()}
function zoom(d){
  if(!map)return;
  if(d<0){ if(map.getLevel()>1) map.setLevel(map.getLevel()-1,{anchor:anchor()}); else extra=Math.min(MAX_EXTRA,extra*1.25) }
  else { if(extra>1.001) extra=Math.max(1,extra/1.25); else if(map.getLevel()<8) map.setLevel(map.getLevel()+1,{anchor:anchor()}) }
  applyExtra();
}
function whenSized(cb){var el=document.getElementById('map'); if(el.clientWidth>0&&el.clientHeight>0) cb(); else setTimeout(function(){whenSized(cb)},50)}
function setType(t){${com.beff.app.data.MapTiles.SKY_TOGGLE}if(map)map.setMapTypeId(t==='sky'?kakao.maps.MapTypeId.HYBRID:kakao.maps.MapTypeId.ROADMAP)}
var s=document.createElement('script');
s.src='https://dapi.kakao.com/v2/maps/sdk.js?appkey=__KEY__&autoload=false';
s.onerror=function(){fail('sdk load failed')};
s.onload=function(){if(!window.kakao||!kakao.maps||!kakao.maps.load){fail('sdk unavailable (key/domain)');return} kakao.maps.load(function(){whenSized(init)})};
document.head.appendChild(s);
</script></body></html>
""".trimIndent()

    private val PAGE = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
html,body,#map{margin:0;padding:0;width:100%;height:100%;overflow:hidden}
.poi,.hz,.me,.dot,.wc,.wc2{transform:scale(var(--inv,1))}
.dot{width:19px;height:19px;border-radius:50%;background:#008E8C;border:3.5px solid #fff}
.wc{width:19px;height:19px;background:#131417;border:3px solid #fff}
.wc2{width:7px;height:7px;background:#8A969C;border:1.5px solid #fff}
.me{width:22px;height:22px;border-radius:50%;background:#1E66F5;border:4px solid #fff;box-shadow:0 0 0 6px rgba(30,102,245,.25)}
${com.beff.app.data.MapTiles.CSS}</style></head><body>${com.beff.app.data.MapTiles.SVG}<div id="map"></div>
<script>
var D=__DATA__, map=null, TEAL='#008E8C';
function fail(m){try{Android.onError(String(m))}catch(e){}}
window.onerror=function(m){fail(m)};
function ll(p){return new kakao.maps.LatLng(p[0],p[1])}
function mark(p,cls,z){new kakao.maps.CustomOverlay({map:map,position:ll(p),content:'<div class="'+cls+'"></div>',xAnchor:0.5,yAnchor:0.5,zIndex:z})}
function line(ps,w,c,style,arrow,z){new kakao.maps.Polyline({map:map,path:ps.map(ll),strokeWeight:w,strokeColor:c,strokeOpacity:1,strokeStyle:style,endArrow:!!arrow,zIndex:z})}
function init(){
  map=new kakao.maps.Map(document.getElementById('map'),{center:ll(D.start),level:3,draggable:true,zoomable:true,scrollwheel:false,disableDoubleClickZoom:false,keyboardShortcuts:false});
  D.others.forEach(function(p){mark(p,'wc2',1)});
  var P=D.path, b=new kakao.maps.LatLngBounds();
  function add(p){var q=ll(p); b.extend(q); pts.push(q)}
  add(D.start);
  if(P.length>1){
    P.forEach(add);
    if(P.length>2){var s=P.slice(1,P.length-1); line(s,13,'#fff','solid',false,2); line(s,7,TEAL,'solid',true,3);}
    [[P[0],P[1]],[P[P.length-2],P[P.length-1]]].forEach(function(seg){line(seg,10,'#fff','shortdash',false,2); line(seg,5,TEAL,'shortdash',false,3)});
  }
  if(D.dest){mark(D.dest,'wc',4); add(D.dest)}
  mark(D.start,'dot',5);
  bounds=b; fit();
  // WebView 크기가 나중에 정해지거나 바뀌면 다시 맞춘다.
  window.addEventListener('resize',function(){map.relayout(); fit()});
  try{Android.onReady()}catch(e){}
}
var bounds=null, pts=[], extra=1, MAX_EXTRA=2;
function inView(){var mb=map.getBounds(); for(var i=0;i<pts.length;i++){if(!mb.contain(pts[i]))return false} return true}
// LatLngBounds에는 중심 함수가 없어 두 모서리로 구한다.
function anchor(){if(!bounds)return map.getCenter(); var sw=bounds.getSouthWest(),ne=bounds.getNorthEast(); return new kakao.maps.LatLng((sw.getLat()+ne.getLat())/2,(sw.getLng()+ne.getLng())/2)}
// 동선이 다 보이는 가장 가까운 단계로 맞춘다. 카카오 최대 확대(1단계)에서도 동선이 작으면 화면을 키운다(최대 2배, 타일이 조금 흐려진다).
function fit(){
  if(!bounds)return;
  map.setBounds(bounds,40,40,40,40);
  while(map.getLevel()>1){var l=map.getLevel(); map.setLevel(l-1,{anchor:anchor()}); if(!inView()){map.setLevel(l,{anchor:anchor()}); break}}
  map.setCenter(anchor());
  extra=1;
  if(map.getLevel()===1){
    var pr=map.getProjection(),x0=1e9,x1=-1e9,y0=1e9,y1=-1e9;
    pts.forEach(function(p){var c=pr.containerPointFromCoords(p); x0=Math.min(x0,c.x); x1=Math.max(x1,c.x); y0=Math.min(y0,c.y); y1=Math.max(y1,c.y)});
    var el=document.getElementById('map');
    var k=Math.min((el.clientWidth-80)/Math.max(x1-x0,1),(el.clientHeight-80)/Math.max(y1-y0,1));
    extra=Math.max(1,Math.min(MAX_EXTRA,k));
  }
  applyExtra();
}
function applyExtra(){document.getElementById('map').style.transform='scale('+extra+')';document.documentElement.style.setProperty('--inv',1/extra)}
// 확대(d<0) · 축소(d>0). 1단계 아래로는 화면 키우기로 이어 간다.
/** 원위치: 끌거나 확대한 지도를 처음 보이던 범위(지금 고른 시설 포함)로 되돌린다. */
function recenter(){if(!map)return; extra=1; applyExtra(); fit()}
function zoom(d){
  if(!map)return;
  if(d<0){ if(map.getLevel()>1) map.setLevel(map.getLevel()-1,{anchor:anchor()}); else extra=Math.min(MAX_EXTRA,extra*1.25) }
  else { if(extra>1.001) extra=Math.max(1,extra/1.25); else if(map.getLevel()<8) map.setLevel(map.getLevel()+1,{anchor:anchor()}) }
  applyExtra();
}
// 지도 칸 크기가 0이면(WebView 배치 전) 잡힐 때까지 기다렸다가 그린다.
function whenSized(cb){var el=document.getElementById('map'); if(el.clientWidth>0&&el.clientHeight>0) cb(); else setTimeout(function(){whenSized(cb)},50)}
// 하차 시뮬레이션: 파란 점이 동선을 따라 걸어간다. 단계 꼭짓점(D.steps)을 지날 때마다 Android.onSimStep(번호), 끝나면 Android.onSimDone().
var sim=null;
function hav(a,b){var R=6371000,d=Math.PI/180,x=Math.pow(Math.sin((b[0]-a[0])*d/2),2)+Math.cos(a[0]*d)*Math.cos(b[0]*d)*Math.pow(Math.sin((b[1]-a[1])*d/2),2);return 2*R*Math.asin(Math.sqrt(x))}
function stopSim(){if(sim){cancelAnimationFrame(sim.raf);sim.ov.setMap(null);sim=null}}
function simulate(){
  if(!map||D.path.length<2)return; stopSim();
  var P=D.path,cum=[0]; for(var i=1;i<P.length;i++)cum.push(cum[i-1]+hav(P[i-1],P[i]));
  var total=cum[cum.length-1], dur=Math.min(30000,Math.max(12000,D.steps.length*3500));
  var el=document.createElement('div'); el.className='me';
  var ov=new kakao.maps.CustomOverlay({map:map,position:ll(P[0]),content:el,xAnchor:0.5,yAnchor:0.5,zIndex:9});
  var next=0,t0=performance.now();
  function frame(now){
    var f=Math.min(1,(now-t0)/dur),dd=f*total;
    while(next<D.steps.length&&cum[D.steps[next]]<=dd+0.01){try{Android.onSimStep(next)}catch(e){} next++}
    var k=1; while(k<cum.length-1&&cum[k]<dd)k++;
    var seg=cum[k]-cum[k-1],r=seg>0?Math.min(1,(dd-cum[k-1])/seg):0;
    ov.setPosition(new kakao.maps.LatLng(P[k-1][0]+(P[k][0]-P[k-1][0])*r,P[k-1][1]+(P[k][1]-P[k-1][1])*r));
    if(f<1)sim.raf=requestAnimationFrame(frame); else{try{Android.onSimDone()}catch(e){}}
  }
  sim={ov:ov,raf:requestAnimationFrame(frame)};
}
function setType(t){${com.beff.app.data.MapTiles.SKY_TOGGLE}if(map)map.setMapTypeId(t==='sky'?kakao.maps.MapTypeId.HYBRID:kakao.maps.MapTypeId.ROADMAP)}
var s=document.createElement('script');
s.src='https://dapi.kakao.com/v2/maps/sdk.js?appkey=__KEY__&autoload=false';
s.onerror=function(){fail('sdk load failed')};
s.onload=function(){if(!window.kakao||!kakao.maps||!kakao.maps.load){fail('sdk unavailable (key/domain)');return} kakao.maps.load(function(){whenSized(init)})};
document.head.appendChild(s);
</script></body></html>
""".trimIndent()
}

/** 웹 메르카토르 전역 픽셀 좌표(줌 z, 타일 256px 기준). 지도 없이 그리는 OSM 도식에 쓴다. */
object Mercator {
    const val MAX_ZOOM = 19

    fun worldX(lng: Double, z: Int): Double = (lng + 180.0) / 360.0 * 256.0 * (1 shl z)

    fun worldY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat)
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * 256.0 * (1 shl z)
    }

    /** 줌 z에서 1픽셀이 나타내는 미터(위도 lat 기준). */
    fun metersPerPixel(lat: Double, z: Int): Double = 156543.03392 * cos(Math.toRadians(lat)) / (1 shl z)
}
