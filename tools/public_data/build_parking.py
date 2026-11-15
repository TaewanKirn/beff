"""전국주차장정보표준데이터(수집 원본 parking_all.json) → 앱용 parking.json (서울·경기·인천, 장애인 전용주차구역 보유 'Y'만).

  python tools/public_data/build_parking.py

- 장애인 전용주차구역 보유 여부는 Y/N/빈칸이다. 빈칸(절반 이상)은 '없다'가 아니라 '모른다'이므로 후보에 넣지 않는다.
- 면수는 주차장 전체 구획 수(prkcmprt)다. 장애인 면수는 원본에 없다.
"""
import json
import os
from datetime import date

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "data", "public-raw", "parking_all.json")
OUT = os.path.join(ROOT, "beff", "src", "main", "assets", "parking.json")
SOURCE = "전국주차장정보표준데이터(공공데이터포털)"


def region(x):
    a = x.get("rdnmadr") or x.get("lnmadr") or ""
    for k in ("서울", "인천", "경기"):
        if a.startswith(k):
            return k
    return None


def main():
    rows = json.load(open(SRC, encoding="utf-8"))
    out = []
    for x in rows:
        r = region(x)
        if not r or x.get("pwdbsPpkZoneYn") != "Y":
            continue
        try:
            lat, lng = float(x["latitude"]), float(x["longitude"])
        except (KeyError, ValueError, TypeError):
            continue
        spaces = str(x.get("prkcmprt", "")).strip()
        o, c = (x.get("weekdayOperOpenHhmm") or "").strip(), (x.get("weekdayOperColseHhmm") or "").strip()
        out.append({
            "id": f"park-{x.get('prkplceNo')}",
            "name": (x.get("prkplceNm") or "").strip(),
            "region": r,
            "address": (x.get("rdnmadr") or x.get("lnmadr") or "").strip(),
            "lat": lat,
            "lng": lng,
            "spaces": int(spaces) if spaces.isdigit() else None,
            "hours": f"{o}~{c}" if o and c else None,
            "type": (x.get("prkplceSe") or "").strip() or None,
            "reference": (x.get("referenceDate") or "").strip() or None,
        })
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"source": SOURCE, "built": date.today().isoformat(), "count": len(out), "spots": out},
                  f, ensure_ascii=False, separators=(",", ":"))
    print(f"parking.json: {len(out)}곳 → {os.path.relpath(OUT, ROOT)} ({round(os.path.getsize(OUT) / 1024)}KB)")


if __name__ == "__main__":
    main()
