package com.beff.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 1113: 시설 약도를 Figma 「지도 (선택 시설 강조)」처럼 그리는 페이지.
 * 카카오 지도는 바탕 색 · 글자를 바꿀 수 없어서, 색을 직접 정할 수 있는 MapLibre GL JS(벡터 지도) + OpenFreeMap(OSM, 키 없음)으로 그린다.
 * - 바탕 #E3EFE1 · 공원 #D2E7CE · 길 흰색 · 물 #C6DCE7 · 건물 #DCE5DD(Figma 약도 색). 가게 · 지명 글자는 그리지 않는다(Figma와 같음).
 * - 스카이뷰는 Esri World Imagery 항공사진.
 * - 페이지 함수는 카카오 페이지와 같다: focusK(k) · resetFocus() · recenter() · zoom(d) · setType(t), 마커 목록 M({k, c, el}).
 *   그래서 FacilityMapCard의 poiStyled(마커 그림 · 이름 알약 CSS)를 그대로 덧입힌다.
 */
object StyledMapPage {
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
        val near = JSONObject().apply {
            plan.pois.groupBy { it.kind }.forEach { (k, ps) ->
                put(k.key, JSONArray().apply { ps.sortedBy { distanceKm(plan.start, it.point) }.take(3).forEach { put(arr(it.point)) } })
            }
        }
        val data = JSONObject().put("start", arr(plan.start)).put("pois", pois).put("routes", routes).put("near", near)
            .put("fromParking", plan.startLabel.startsWith("주차장"))
        return PAGE.replace("__DATA__", data.toString())
    }


    /** 1113: G-5 근처 주차장 지도(카카오 → Figma식). 목적지 이름 알약 + 주차장 P 마커, sel(i)로 고른 주차장을 키운다. [css]는 마커 CSS(poiCss). */
    fun parkingHtml(destLat: Double, destLng: Double, name: String, spots: List<Pair<Double, Double>>, css: String): String {
        val data = JSONObject().put("dest", JSONArray().put(destLat).put(destLng)).put("name", name)
            .put("spots", JSONArray().apply { spots.forEach { put(JSONArray().put(it.first).put(it.second)) } })
        val style = PAGE.substringAfter("var STYLE=").substringBefore("\nfunction el(")
        return """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/maplibre-gl@$VER/dist/maplibre-gl.css">
<style>html,body,#map{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#E3EFE1}$css
.pk{transform:none}.pk.on{width:72px;height:72px}.pk.on i{width:40px;height:40px}
.dest{transform:none}
.maplibregl-ctrl-attrib{font-size:10px}.maplibregl-ctrl-bottom-right{right:auto;left:96px}
.maplibregl-ctrl-scale{border-color:#252B31;color:#252B31;background:rgba(255,255,255,.7);font:600 11px sans-serif}</style></head><body><div id="map"></div>
<script src="https://unpkg.com/maplibre-gl@$VER/dist/maplibre-gl.js"></script>
<script>
var D=$data, map=null, P=[], PAD={top:110,right:110,bottom:60,left:60};
function fail(m){try{Android.onError(String(m))}catch(e){}}
window.onerror=function(m){fail(m)};
function lnglat(p){return [p[1],p[0]]}
var STYLE=$style
function wrap(e){var w=document.createElement('div'); w.appendChild(e); return w}
function init(){
  if(!window.maplibregl){fail('maplibre unavailable');return}
  map=new maplibregl.Map({container:'map',style:STYLE,center:lnglat(D.dest),zoom:15,maxZoom:19,attributionControl:{compact:true},dragRotate:false,scrollZoom:false,fadeDuration:0});
  map.touchZoomRotate.disableRotation();
  map.addControl(new maplibregl.ScaleControl({maxWidth:90,unit:'metric'}),'bottom-left');
  map.on('load',function(){
    var de=document.createElement('div'); de.className='dest'; de.textContent=D.name;
    var dw=wrap(de); dw.style.zIndex=5; new maplibregl.Marker({element:dw,anchor:'bottom'}).setLngLat(lnglat(D.dest)).addTo(map);
    D.spots.forEach(function(s){var el=document.createElement('div'); el.className='pk'; el.setAttribute('data-k','parking'); el.appendChild(document.createElement('i'));
      var w=wrap(el); w.style.zIndex=2; new maplibregl.Marker({element:w,anchor:'center'}).setLngLat(lnglat(s)).addTo(map); P.push({el:el,w:w})});
    fit();
    var at=document.querySelector('.maplibregl-ctrl-attrib'); if(at){at.classList.remove('maplibregl-compact-show'); at.removeAttribute('open')}
    try{Android.onReady()}catch(e){}
  });
}
function fit(){var b=new maplibregl.LngLatBounds(); b.extend(lnglat(D.dest)); D.spots.forEach(function(s){b.extend(lnglat(s))}); map.fitBounds(b,{padding:PAD,maxZoom:17,duration:0})}
function sel(i){if(!map)return; P.forEach(function(p,j){p.el.className='pk'+(j===i?' on':''); p.w.style.zIndex=j===i?6:2}); var s=D.spots[i]; if(s&&!map.getBounds().contains(lnglat(s)))map.easeTo({center:lnglat(s),duration:300})}
function recenter(){fit()}
function zoom(d){if(!map)return; if(d<0)map.zoomIn({duration:200}); else map.zoomOut({duration:200})}
function setType(t){if(!map||!map.getLayer('sky'))return; map.setLayoutProperty('sky','visibility',t==='sky'?'visible':'none')}
window.addEventListener('load',function(){try{init()}catch(e){fail(e)}});
</script></body></html>
"""
    }

    private const val VER = "4.7.1"

    private val PAGE = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/maplibre-gl@$VER/dist/maplibre-gl.css">
<style>
html,body,#map{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#E3EFE1;touch-action:none}
.poi{border-radius:50%;display:flex;align-items:center;justify-content:center;transition:width .2s,height .2s}
.poi.on{width:58px;height:58px;border-width:3px}
.poi.on i{width:32px;height:32px}
.hz{width:26px;height:26px;border-radius:7px;background:#FFF4E5;border:2.5px solid #E8590C;display:flex;align-items:center;justify-content:center;font:700 14px sans-serif;color:#E8590C;box-shadow:0 1px 4px rgba(0,0,0,.25)}
.hz.on{background:#E8590C;color:#fff;border-color:#fff}
.me{width:24px;height:24px;border-radius:50%;background:#1E66F5;border:4px solid #fff;box-shadow:0 0 0 7px rgba(30,102,245,.25)}
.arrow{width:0;height:0;border-top:16px solid transparent;border-bottom:16px solid transparent;border-left:26px solid #00837F}
.maplibregl-ctrl-attrib{font-size:10px}
.maplibregl-ctrl-bottom-right{right:auto;left:96px}
.maplibregl-ctrl-scale{border-color:#252B31;color:#252B31;background:rgba(255,255,255,.7);font:600 11px sans-serif}
</style></head><body><div id="map"></div>
<script src="https://unpkg.com/maplibre-gl@$VER/dist/maplibre-gl.js"></script>
<script>
var D=__DATA__, map=null, TEAL='#00837F', M=[], route=null, arrowM=null, pts=[];
// 안쪽 여백(CSS px) — 위: 지도 종류 토글, 오른쪽: 확대 · 원위치 버튼, 아래: 이름 알약.
var PAD={top:110,right:110,bottom:110,left:50};
function fail(m){try{Android.onError(String(m))}catch(e){}}
window.onerror=function(m){fail(m)};
function lnglat(p){return [p[1],p[0]]}
var STYLE={version:8,
 sources:{
  omt:{type:'vector',url:'https://tiles.openfreemap.org/planet'},
  sky:{type:'raster',tiles:['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'],tileSize:256,maxzoom:19,attribution:'Esri'}
 },
 layers:[
  {id:'bg',type:'background',paint:{'background-color':'#E3EFE1'}},
  {id:'green',type:'fill',source:'omt','source-layer':'landcover',filter:['in',['get','class'],['literal',['grass','wood','farmland']]],paint:{'fill-color':'#D2E7CE'}},
  {id:'park',type:'fill',source:'omt','source-layer':'park',paint:{'fill-color':'#D2E7CE'}},
  {id:'landuse',type:'fill',source:'omt','source-layer':'landuse',filter:['in',['get','class'],['literal',['cemetery','pitch','playground','garden']]],paint:{'fill-color':'#D2E7CE'}},
  {id:'water',type:'fill',source:'omt','source-layer':'water',paint:{'fill-color':'#C6DCE7'}},
  {id:'building',type:'fill',source:'omt','source-layer':'building',minzoom:14,paint:{'fill-color':'#E6EEE6','fill-outline-color':'#D5E1D5'}},
  {id:'path',type:'line',source:'omt','source-layer':'transportation',filter:['in',['get','class'],['literal',['path','track']]],
   layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':'#FFFFFF','line-width':['interpolate',['linear'],['zoom'],15,1.5,19,5]}},
  {id:'minor',type:'line',source:'omt','source-layer':'transportation',filter:['in',['get','class'],['literal',['minor','service']]],
   layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':'#FFFFFF','line-width':['interpolate',['linear'],['zoom'],14,2,19,14]}},
  {id:'major',type:'line',source:'omt','source-layer':'transportation',filter:['in',['get','class'],['literal',['motorway','trunk','primary','secondary','tertiary']]],
   layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':'#FFFFFF','line-width':['interpolate',['linear'],['zoom'],12,3,19,30]}},
  {id:'sky',type:'raster',source:'sky',layout:{visibility:'none'}}
 ]};
function el(cls){var d=document.createElement('div'); d.className=cls; return d}
function marker(e,p,z){var w=document.createElement('div'); w.appendChild(e); var m=new maplibregl.Marker({element:w,anchor:'center'}).setLngLat(lnglat(p)).addTo(map); w.style.zIndex=z; return m}
function zi(m,z){if(m.m)m.m.getElement().style.zIndex=z}
function init(){
  if(!window.maplibregl){fail('maplibre unavailable');return}
  map=new maplibregl.Map({container:'map',style:STYLE,center:lnglat(D.start),zoom:16,maxZoom:19.5,
    attributionControl:{compact:true},dragRotate:false,pitchWithRotate:false,scrollZoom:false,fadeDuration:0});
  map.touchZoomRotate.disableRotation();
  map.addControl(new maplibregl.ScaleControl({maxWidth:90,unit:'metric'}),'bottom-left');
  D.pois.forEach(function(q){
    var e=el(q.c?'hz':'poi'); e.textContent=q.g;
    if(D.fromParking&&q.k==='parking'&&Math.hypot((q.p[0]-D.start[0])*111000,(q.p[1]-D.start[1])*88000)<30) e.style.display='none';
    M.push({k:q.k,c:q.c,el:e,p:q.p,m:null});
  });
  map.on('load',function(){
    map.addSource('route',{type:'geojson',data:{type:'FeatureCollection',features:[]}});
    map.addLayer({id:'route',type:'line',source:'route',layout:{'line-cap':'round','line-join':'round'},
      paint:{'line-color':TEAL,'line-width':10}},'sky');
    M.forEach(function(m){m.m=marker(m.el,m.p,m.c?3:2)});
    // 출발점: 주차장에서 출발하면 Figma처럼 주차 마커(P), 아니면 파란 점.
    var s=D.fromParking?el('poi start'):el('me');
    if(D.fromParking){s.setAttribute('data-k','parking'); s.appendChild(document.createElement('i'))}
    marker(s,D.start,9);
    arrowM=new maplibregl.Marker({element:el('arrow'),anchor:'center',rotationAlignment:'viewport'}).setLngLat(lnglat(D.start));
    setPts(initialPts()); fit();
    map.on('move',placeArrow);
    var at=document.querySelector('.maplibregl-ctrl-attrib'); if(at){at.classList.remove('maplibregl-compact-show'); at.removeAttribute('open')}
    try{Android.onReady()}catch(e){}
  });
  map.on('error',function(e){console.log('map error '+(e&&e.error&&e.error.message))});
}
function setPts(list){pts=list}
function initialPts(){var l=[D.start]; for(var k in D.routes){var p=D.routes[k].path; l.push(p[p.length-1])} if(l.length<2){for(var n in D.near){if(n!=='stairs'&&n!=='slope')l=l.concat(D.near[n].slice(0,1))}} return l}
function clearRoute(){route=null; if(map.getSource('route'))map.getSource('route').setData({type:'FeatureCollection',features:[]}); if(arrowM)arrowM.remove()}
function nearestOf(k){var best=-1,bd=1e9; D.pois.forEach(function(q,i){if(q.k!==k)return; var d=Math.pow(q.p[0]-D.start[0],2)+Math.pow(q.p[1]-D.start[1],2); if(d<bd){bd=d;best=i}}); return best}
function focusK(k){
  if(!map)return; clearRoute();
  if(!D.routes[k]&&!D.near[k]&&(k==='changing'||k==='nursing')&&(D.routes.toilet||D.near.toilet)) k='toilet';
  if(!D.routes[k]&&!D.near[k]){resetFocus();return}
  var r=D.routes[k], list=[D.start], hz=r?r.hz:[];
  // 이름 알약은 길이 닿는 한 곳(없으면 가장 가까운 한 곳)만. 나머지 마커는 Figma처럼 그대로 둔다.
  var on=r?r.t:nearestOf(k);
  M.forEach(function(m,i){
    if(m.c){var o=hz.indexOf(i)>=0; m.el.className='hz'+(o?' on':''); zi(m,o?7:3); return}
    var o=i===on; m.el.className='poi'+(o?' on':''); zi(m,o?6:2)});
  if(r){
    route=r.path.map(lnglat);
    map.getSource('route').setData({type:'Feature',geometry:{type:'LineString',coordinates:route}});
    list=list.concat(r.path); if(r.t>=0) list.push(D.pois[r.t].p);
    arrowM.addTo(map);
  } else if(D.near[k]) list=list.concat(D.near[k].slice(0,1));
  setPts(list); fit();
}
function resetFocus(){if(!map)return; clearRoute(); M.forEach(function(m){m.el.className=m.c?'hz':'poi'; zi(m,m.c?3:2)}); setPts(initialPts()); fit()}
/** 화살표: 선 끝(시설 마커) 50px 앞에 선 방향으로 놓는다 — 마커에 가리지 않게. */
function placeArrow(){
  if(!route||route.length<2)return;
  var px=route.map(function(c){return map.project(c)}), need=58, i=px.length-1, tot=0;
  for(var j=1;j<px.length;j++)tot+=Math.hypot(px[j].x-px[j-1].x,px[j].y-px[j-1].y);
  arrowM.getElement().style.visibility=tot<90?'hidden':'visible';
  while(i>0){var a=px[i-1],b=px[i],d=Math.hypot(b.x-a.x,b.y-a.y); if(d>=need){var t=(d-need)/d, x=a.x+(b.x-a.x)*t, y=a.y+(b.y-a.y)*t;
      arrowM.setLngLat(map.unproject([x,y])); arrowM.setRotation(Math.atan2(b.y-a.y,b.x-a.x)*180/Math.PI); return}
    need-=d; i--}
  arrowM.setLngLat(route[0]);
}
function fit(){
  if(!map||!pts.length)return;
  var b=new maplibregl.LngLatBounds(); pts.forEach(function(p){b.extend(lnglat(p))});
  map.fitBounds(b,{padding:PAD,maxZoom:19,duration:0});
  placeArrow();
}
function recenter(){fit()}
function zoom(d){if(!map)return; if(d<0)map.zoomIn({duration:200}); else map.zoomOut({duration:200})}
function setType(t){if(!map||!map.getLayer('sky'))return; map.setLayoutProperty('sky','visibility',t==='sky'?'visible':'none'); document.getElementById('map').classList.toggle('sky',t==='sky')}
window.addEventListener('load',function(){try{init()}catch(e){fail(e)}});
</script></body></html>
"""
}
