"""수집 원본(관광공사 무장애 여행정보) → 앱용 places.json.

  python tools/public_data/build_places.py        # beff/src/main/assets/places.json 생성

원칙(프로젝트 불변 원칙 4): 확인되지 않은 시설을 사실처럼 쓰지 않는다.
- 시설 칩은 원문에 "있음/가능/설치"가 분명하고 "없음/불가"가 없을 때만 켠다.
- 원문 문장은 access 에 그대로 보존한다(화면에서 근거를 보여 줄 수 있도록).
- 모든 장소에 출처·기준일을 붙인다.
"""
import json
import os
import re
import sys
from datetime import date

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RAW = os.path.join(ROOT, "data", "public-raw")
OUT = os.path.join(ROOT, "beff", "src", "main", "assets", "places.json")
REGIONS = {"서울": "11", "인천": "28", "경기": "41"}
KIND = {"12": "관광지", "14": "문화시설", "28": "레포츠", "39": "음식점", "38": "쇼핑", "32": "숙박", "15": "행사"}
SOURCE = "한국관광공사 무장애 여행정보(KorWithService2)"

NEG = re.compile(r"없음|없습니다|불가|미운영|미설치|해당\s*없음")


def has(text, *keys):
    """text에 keys 중 하나가 있고 부정 표현이 없으면 True."""
    if not text:
        return False
    if NEG.search(text):
        return False
    return any(k in text for k in keys)


POS = ("있음", "보유", "운영", "설치", "이용 가능", "주차 가능", "별도", "가능")


def facilities(d):
    """원문 문장에서 시설 칩을 뽑는다. 애매하면 켜지 않는다(없는 시설을 있다고 하는 것이 더 해롭다)."""
    f = []
    # 수유실: '수유실'이라는 말이 있고 부정이 없을 때
    lact = d.get("lactationroom") or ""
    if "수유" in lact and not NEG.search(lact):
        f.append("수유실")
    # 장애인 주차: 원문에 '장애인'이 있어야 한다('지상에 3면 있음'처럼 일반 주차 문구는 제외)
    park = d.get("parking") or ""
    if "장애인" in park and not NEG.search(park) and has(park, *POS):
        f.append("장애인 주차")
    # 장애인 화장실: '장애인' 또는 접근성 설비(손잡이·비상 호출벨)가 적힌 경우
    rest = d.get("restroom") or ""
    if not NEG.search(rest) and ("장애인" in rest or "손잡이" in rest or "비상" in rest) and has(rest, *POS):
        f.append("장애인 화장실")
    if has(d.get("elevator"), "있음", "설치", "운영"):
        f.append("엘리베이터")
    if "대여" in (d.get("wheelchair") or "") and "불가" not in (d.get("wheelchair") or ""):
        f.append("휠체어 대여")
    if "대여" in (d.get("stroller") or "") and "불가" not in (d.get("stroller") or ""):
        f.append("유모차 대여")
    entrance = " ".join(filter(None, [d.get("route"), d.get("exit")]))
    if "경사로" in entrance and not NEG.search(entrance):
        f.append("경사로")
    # 턱 없는 입구: '턱/단차가 없어'라고 분명히 적힌 경우만 ('접근 가능'만으로는 켜지 않는다)
    if re.search(r"(턱|단차)(이|가)?\s*없", entrance):
        f.append("턱 없는 입구")
    if has(d.get("babysparechair"), "있음", "구비", "비치"):
        f.append("유아 의자")
    if "기저귀" in " ".join(filter(None, [d.get("infantsfamilyetc"), d.get("handicapetc")])):
        f.append("기저귀 교환대")
    return f


ACCESS_KEYS = [
    "parking", "route", "exit", "elevator", "restroom", "lactationroom", "wheelchair", "stroller",
    "babysparechair", "infantsfamilyetc", "handicapetc", "publictransport",
]


def main():
    places, skipped = [], 0
    for region, code in REGIONS.items():
        lp = os.path.join(RAW, f"tour_list_{code}.json")
        if not os.path.exists(lp):
            continue
        for it in json.load(open(lp, encoding="utf-8")):
            cid = it["contentid"]
            dp = os.path.join(RAW, "tour_detail", f"{cid}.json")
            if not os.path.exists(dp):
                continue
            data = json.load(open(dp, encoding="utf-8"))
            body = (data.get("response") or {}).get("body") or {}
            items = body.get("items")
            d = (items.get("item") or [None])[0] if isinstance(items, dict) else None
            if not d:
                skipped += 1
                continue
            try:
                lat, lng = float(it["mapy"]), float(it["mapx"])
            except (KeyError, ValueError):
                skipped += 1
                continue
            fac = facilities(d)
            places.append({
                "id": f"tour-{cid}",
                "name": it.get("title", "").strip(),
                "region": region,
                "kind": KIND.get(str(it.get("contenttypeid")), "기타"),
                "address": " ".join(filter(None, [it.get("addr1"), it.get("addr2")])).strip(),
                "lat": lat,
                "lng": lng,
                "tel": it.get("tel") or None,
                "facilities": fac,
                "access": {k: d[k] for k in ACCESS_KEYS if d.get(k)},
                "source": SOURCE,
                "contentId": cid,
                "modified": (it.get("modifiedtime") or "")[:8] or None,
                "fetched": date.today().isoformat(),
            })
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"source": SOURCE, "built": date.today().isoformat(), "count": len(places), "places": places},
                  f, ensure_ascii=False, separators=(",", ":"))
    from collections import Counter
    c = Counter(x for p in places for x in p["facilities"])
    print(f"places.json: {len(places)}건 (상세 없음·좌표 없음으로 제외 {skipped}건) → {os.path.relpath(OUT, ROOT)}")
    print("시설 칩 분포:", dict(c.most_common()))
    print("크기:", round(os.path.getsize(OUT) / 1024), "KB")


if __name__ == "__main__":
    main()
