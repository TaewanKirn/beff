"""장소 주변 OSM 화장실 · 보행로를 받아 앱용 도식 지도 JSON(assets/osm/<장소id>.json)으로 저장한다.

사용: python tools/public_data/build_osm_walkmap.py tour-128611 [반경m=500]

- 인증키 없음. Overpass API(공개 서버)를 쓴다. 서버 부하를 줄이려고 장소 1곳씩, 결과는 파일로 저장해 앱에 넣는다.
- 저장 내용: 화장실 좌표(OSM 태그 중 name · wheelchair · access 만), 보행로 선(좌표열).
  접근성(wheelchair)은 OSM에 비어 있는 곳이 많다. 비어 있으면 null로 두고 앱은 "확인 안 됨"으로 쓴다.
- 출처: © OpenStreetMap contributors (ODbL). 앱 화면에 표기한다. 가공 데이터의 공개 의무는 [확인 필요].
"""
import json
import sys
import time
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLACES = ROOT / "beff" / "src" / "main" / "assets" / "places.json"
OUT_DIR = ROOT / "beff" / "src" / "main" / "assets" / "osm"
ENDPOINT = "https://overpass-api.de/api/interpreter"
FOOT = "footway|path|pedestrian|steps|track|service|living_street|residential|cycleway"


def main() -> None:
    place_id = sys.argv[1]
    radius = int(sys.argv[2]) if len(sys.argv) > 2 else 500
    places = json.loads(PLACES.read_text(encoding="utf-8"))["places"]
    p = next(x for x in places if x["id"] == place_id)
    lat, lng = p["lat"], p["lng"]

    q = f"""[out:json][timeout:50];
(
  node["amenity"="toilets"](around:{radius},{lat},{lng});
  way["amenity"="toilets"](around:{radius},{lat},{lng});
  way["highway"~"^({FOOT})$"](around:{radius},{lat},{lng});
);
out tags geom;"""
    req = urllib.request.Request(
        ENDPOINT, data=urllib.parse.urlencode({"data": q}).encode(), headers={"User-Agent": "beff-research/0.1"}
    )
    with urllib.request.urlopen(req, timeout=90) as r:
        data = json.loads(r.read().decode("utf-8"))
    time.sleep(1)

    toilets, ways = [], []
    for e in data["elements"]:
        t = e.get("tags", {})
        if t.get("amenity") == "toilets":
            if e["type"] == "node":
                c = (e["lat"], e["lon"])
            else:  # 건물 형태 화장실은 외곽선 중심
                g = e["geometry"]
                c = (sum(a["lat"] for a in g) / len(g), sum(a["lon"] for a in g) / len(g))
            toilets.append({
                "lat": round(c[0], 7), "lng": round(c[1], 7),
                "name": t.get("name"), "wheelchair": t.get("wheelchair"), "access": t.get("access"),
            })
        elif e["type"] == "way" and "geometry" in e:
            ways.append({
                "kind": t.get("highway"),
                "pts": [[round(a["lat"], 7), round(a["lon"], 7)] for a in e["geometry"]],
            })

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    old = OUT_DIR / f"{place_id}.json"
    start = json.loads(old.read_text(encoding="utf-8")).get("start") if old.exists() else None  # 손으로 고른 시작점은 유지
    out = {
        "placeId": place_id, "center": {"lat": lat, "lng": lng}, "radiusM": radius,
        "source": "© OpenStreetMap contributors (ODbL), Overpass API", "fetched": date.today().isoformat(),
        "toilets": toilets, "ways": ways,
    }
    if start:
        out["start"] = start
    path = OUT_DIR / f"{place_id}.json"
    path.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{place_id}: 화장실 {len(toilets)} · 보행로 {len(ways)} → {path.relative_to(ROOT)} ({path.stat().st_size // 1024}KB)")


if __name__ == "__main__":
    main()
