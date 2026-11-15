"""서울·경기·인천 무장애 장소 수집 (한국관광공사 무장애 여행정보 KorWithService2).

단계:
  python tools/public_data/tour_region.py list              # 지역별 장소 목록 수집 (호출 소수)
  python tools/public_data/tour_region.py details 800       # 시설 상세를 최대 800건 받는다. 이어받기 가능
  python tools/public_data/tour_region.py stats             # 지금까지 받은 양과 분포

- 인증키는 collect.py와 같은 방식(환경변수 DATA_GO_KR_KEY)으로만 읽는다. 출력·저장 파일에 키를 남기지 않는다.
- 상세는 data/public-raw/tour_detail/{contentid}.json 에 한 건씩 저장하므로, 일일 호출 한도에 걸리거나
  중간에 끊겨도 다음 실행이 받은 것을 건너뛰고 이어서 받는다.
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import collect as C  # noqa: E402

REGIONS = {"서울": "11", "인천": "28", "경기": "41"}   # 법정동 시도 코드(ldongCode2로 확인)
COMMON = {"MobileOS": "ETC", "MobileApp": "Beff", "_type": "json"}
LIST_DIR = C.RAW
DETAIL_DIR = os.path.join(C.RAW, "tour_detail")


def call(op, **params):
    status, body = C.get(f"{C.TOUR_BASE}/{op}", {**COMMON, **params})
    try:
        data = json.loads(body)
    except json.JSONDecodeError:
        return status, None, C.redact(body[:300]).replace(chr(10), " ")
    return status, data, None


def header(data):
    h = (data.get("response") or {}).get("header") or {}
    if h:
        return h.get("resultCode"), h.get("resultMsg")
    # 인증·한도 오류는 다른 형식으로 온다: {"OpenAPI_ServiceResponse": {"cmmMsgHeader": {...}}}
    e = (data.get("OpenAPI_ServiceResponse") or {}).get("cmmMsgHeader") or {}
    return e.get("returnReasonCode"), e.get("returnAuthMsg") or e.get("errMsg")


def items_of(data):
    body = (data.get("response") or {}).get("body") or {}
    items = body.get("items")
    if isinstance(items, dict):
        items = items.get("item", [])
    return body, (items if isinstance(items, list) else ([items] if items else []))


def collect_lists():
    for name, code in REGIONS.items():
        out, page = [], 1
        while True:
            status, data, err = call("areaBasedList2", lDongRegnCd=code, numOfRows=500, pageNo=page)
            if data is None:
                print(f"{name}: 중단 HTTP {status} {err}")
                break
            rc, msg = header(data)
            if rc != "0000":
                print(f"{name}: 중단 {rc} {msg}")
                break
            body, items = items_of(data)
            out += items
            total = int(body.get("totalCount") or 0)
            print(f"{name} 페이지 {page}: +{len(items)} (누적 {len(out)}/{total})")
            if not items or len(out) >= total:
                break
            page += 1
            time.sleep(0.2)
        path = os.path.join(LIST_DIR, f"tour_list_{code}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False)
        print("저장:", os.path.relpath(path, C.ROOT))


def load_all_places():
    places = []
    for name, code in REGIONS.items():
        path = os.path.join(LIST_DIR, f"tour_list_{code}.json")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                for it in json.load(f):
                    it["_region"] = name
                    places.append(it)
    return places


def collect_details(budget):
    os.makedirs(DETAIL_DIR, exist_ok=True)
    places = load_all_places()
    if not places:
        sys.exit("먼저 list 단계를 실행하세요.")
    # 베프에 중요한 분류부터: 관광지(12) · 문화시설(14) · 레포츠(28) → 음식점(39) · 쇼핑(38) · 숙박(32) · 행사(15)
    order = {"12": 0, "14": 1, "28": 2, "39": 3, "38": 4, "32": 5, "15": 6}
    places.sort(key=lambda p: order.get(str(p.get("contenttypeid")), 9))
    todo = [p for p in places if not os.path.exists(os.path.join(DETAIL_DIR, f"{p['contentid']}.json"))]
    print(f"전체 {len(places)}건 중 상세 미수집 {len(todo)}건, 이번 예산 {budget}건")
    done = 0
    for p in todo[:budget]:
        status, data, err = call("detailWithTour2", contentId=p["contentid"])
        if data is None:
            print(f"중단: HTTP {status} {err}")
            break
        rc, msg = header(data)
        if rc != "0000":
            print(f"중단: {rc} {msg}" + ("  → 일일 한도(1,000건). 내일 같은 명령으로 이어받기" if rc == "22" else ""))
            break
        with open(os.path.join(DETAIL_DIR, f"{p['contentid']}.json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        done += 1
        if done % 100 == 0:
            print(f"  {done}건 완료")
        time.sleep(0.1)
    print(f"이번에 받은 상세: {done}건")


def stats():
    places = load_all_places()
    have = set(os.listdir(DETAIL_DIR)) if os.path.isdir(DETAIL_DIR) else set()
    by_type, by_region = {}, {}
    for p in places:
        by_type[p.get("contenttypeid")] = by_type.get(p.get("contenttypeid"), 0) + 1
        by_region[p["_region"]] = by_region.get(p["_region"], 0) + 1
    print("지역별:", by_region)
    print("분류(contenttypeid)별:", dict(sorted(by_type.items(), key=lambda x: -x[1])))
    print(f"상세 수집 {len(have)}/{len(places)}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "stats"
    if cmd == "list":
        collect_lists()
    elif cmd == "details":
        collect_details(int(sys.argv[2]) if len(sys.argv) > 2 else 300)
    elif cmd == "stats":
        stats()
    else:
        sys.exit(__doc__)
