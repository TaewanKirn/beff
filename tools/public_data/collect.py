"""공공데이터 수집 스크립트 (data.go.kr).

- 인증키는 환경변수 DATA_GO_KR_KEY(Decoding 키)에서만 읽는다. 코드·출력·파일에 키 값을 쓰지 않는다.
  현재 프로세스에 없으면 Windows 사용자 환경변수(HKCU\\Environment)에서 읽는다(setx 직후 재시작 전에도 동작).
- 원본 응답은 data/public-raw/ 아래에 그대로 저장한다(키는 저장 전에 가린다).
- 사용: python tools/public_data/collect.py probe      # 호출 가능 여부와 필드명 확인
        python tools/public_data/collect.py parking    # 전국주차장정보표준데이터 전체 수집
        python tools/public_data/collect.py tour       # 무장애 여행정보(관광공사) 키워드 수집
        python tools/public_data/collect.py nanum      # 무장애나눔길 조성정보(산림복지진흥원, 2025-12-31 기준)
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RAW = os.path.join(ROOT, "data", "public-raw")

PARKING_URL = "https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api"   # 전국주차장정보표준데이터 [미확인: 첫 호출로 확인]
TOUR_BASE = "https://apis.data.go.kr/B551011/KorWithService2"              # 한국관광공사 무장애 여행 정보
# 한국산림복지진흥원 무장애나눔길 조성정보: 파일데이터지만 odcloud API가 연도별로 따로 있다.
# 명세(키 불필요): https://infuser.odcloud.kr/oas/docs?namespace=15063418/v1  (연도별 uddi 식별자 목록)
NANUM_URL = "https://api.odcloud.kr/api/15063418/v1/uddi:c24b527b-0529-4e1f-a680-851a5bff88e3"  # 2025-12-31 기준


def load_key() -> str:
    key = os.environ.get("DATA_GO_KR_KEY")
    if not key and sys.platform == "win32":
        try:
            import winreg
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as k:
                key, _ = winreg.QueryValueEx(k, "DATA_GO_KR_KEY")
        except OSError:
            key = None
    if not key:
        sys.exit("DATA_GO_KR_KEY 환경변수가 없습니다. setx DATA_GO_KR_KEY \"Decoding 키\" 후 새 터미널에서 다시 실행하세요.")
    # Encoding 키가 들어온 경우 Decoding으로 되돌려 이중 인코딩을 막는다.
    return urllib.parse.unquote(key) if "%" in key else key


KEY = load_key()


def redact(text: str) -> str:
    for v in {KEY, urllib.parse.quote(KEY, safe=""), urllib.parse.quote_plus(KEY)}:
        text = text.replace(v, "[키]")
    return text


def get(url: str, params: dict, retries: int = 2):
    """GET 호출. (상태코드, 본문 문자열) 반환. 키는 params에 넣어 한 번만 인코딩한다."""
    q = urllib.parse.urlencode({"serviceKey": KEY, **params})
    full = f"{url}?{q}"
    for attempt in range(retries + 1):
        try:
            req = urllib.request.Request(full, headers={"User-Agent": "beff-collector/0.1"})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status, r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", "replace")
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt == retries:
                return 0, f"연결 실패: {redact(str(e))}"
            time.sleep(1.5)


def save(name: str, text: str):
    os.makedirs(RAW, exist_ok=True)
    path = os.path.join(RAW, name)
    with open(path, "w", encoding="utf-8") as f:
        f.write(redact(text))
    return os.path.relpath(path, ROOT)


def summarize(label: str, status: int, body: str):
    """응답 요약: 상태, 결과코드/메시지, 필드명만 출력한다(데이터 값·키는 출력하지 않음)."""
    print(f"[{label}] HTTP {status}, {len(body)}자")
    try:
        data = json.loads(body)
    except json.JSONDecodeError:
        print("  JSON이 아님. 앞부분:", redact(body[:200]).replace("\n", " "))
        return None
    hdr = (data.get("response") or {}).get("header") or data.get("header") or {}
    if hdr:
        print("  결과:", hdr.get("resultCode"), hdr.get("resultMsg"))
    return data


def items_of(data):
    b = (data.get("response") or {}).get("body") or data.get("body") or {}
    it = b.get("items")
    if isinstance(it, dict):
        it = it.get("item", [])
    return b, (it if isinstance(it, list) else ([it] if it else []))


def probe():
    # 1) 전국주차장정보표준데이터
    st, body = get(PARKING_URL, {"pageNo": 1, "numOfRows": 3, "type": "json"})
    print("저장:", save("parking_probe.json", body))
    d = summarize("주차장", st, body)
    if d:
        b, items = items_of(d)
        print("  총건수:", b.get("totalCount"), "| 표본 필드:", sorted(items[0].keys()) if items else "없음")

    # 2) 한국관광공사 무장애 여행 정보: 키워드 → 상세
    common = {"MobileOS": "ETC", "MobileApp": "Beff", "_type": "json"}
    st, body = get(f"{TOUR_BASE}/searchKeyword2", {**common, "keyword": "국립세종수목원", "numOfRows": 5, "pageNo": 1})
    print("저장:", save("tour_search_probe.json", body))
    d = summarize("무장애 키워드조회", st, body)
    if d:
        b, items = items_of(d)
        print("  총건수:", b.get("totalCount"), "| 표본 필드:", sorted(items[0].keys()) if items else "없음")
        if items:
            cid = items[0].get("contentid")
            for op in ("detailWithTour2", "detailCommon2"):
                st2, body2 = get(f"{TOUR_BASE}/{op}", {**common, "contentId": cid})
                print("저장:", save(f"tour_{op}_probe.json", body2))
                d2 = summarize(op, st2, body2)
                if d2:
                    _, it2 = items_of(d2)
                    print("  표본 필드:", sorted(it2[0].keys()) if it2 else "없음")


def collect_parking(page_size: int = 1000, max_pages: int = 200):
    out, page, total = [], 1, None
    while page <= max_pages:
        st, body = get(PARKING_URL, {"pageNo": page, "numOfRows": page_size, "type": "json"})
        d = json.loads(body) if st == 200 else None
        if not d:
            print(f"중단: 페이지 {page} HTTP {st}", redact(body[:160]))
            break
        b, items = items_of(d)
        total = int(b.get("totalCount") or 0)
        out += items
        print(f"페이지 {page}: +{len(items)} (누적 {len(out)}/{total})")
        if not items or len(out) >= total:
            break
        page += 1
        time.sleep(0.3)
    print("저장:", save("parking_all.json", json.dumps(out, ensure_ascii=False)))


def collect_nanum(per_page: int = 1000):
    """odcloud는 page/perPage 파라미터를 쓰고 결과는 data 배열, 전체 건수는 totalCount."""
    out, page = [], 1
    while True:
        st, body = get(NANUM_URL, {"page": page, "perPage": per_page, "returnType": "JSON"})
        d = json.loads(body) if st == 200 and body.lstrip().startswith("{") else None
        if not d or "data" not in d:
            print(f"중단: HTTP {st}", redact(body[:200]).replace(chr(10), " "))
            break
        out += d["data"]
        total = d.get("totalCount") or d.get("matchCount") or 0
        print(f"페이지 {page}: +{len(d['data'])} (누적 {len(out)}/{total})")
        if not d["data"] or len(out) >= total:
            break
        page += 1
        time.sleep(0.3)
    print("저장:", save("nanum_20251231.json", json.dumps(out, ensure_ascii=False)))


def collect_tour(keywords):
    common = {"MobileOS": "ETC", "MobileApp": "Beff", "_type": "json"}
    result = {}
    for kw in keywords:
        st, body = get(f"{TOUR_BASE}/searchKeyword2", {**common, "keyword": kw, "numOfRows": 10, "pageNo": 1})
        d = json.loads(body) if st == 200 else None
        _, items = items_of(d) if d else (None, [])
        print(f"'{kw}': {len(items)}건")
        for it in items[:3]:
            cid = it.get("contentid")
            wt = get(f"{TOUR_BASE}/detailWithTour2", {**common, "contentId": cid})[1]
            result.setdefault(kw, []).append({"search": it, "withTour": json.loads(wt) if wt.startswith("{") else wt})
            time.sleep(0.2)
    print("저장:", save("tour_keywords.json", json.dumps(result, ensure_ascii=False)))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "probe"
    if cmd == "probe":
        probe()
    elif cmd == "parking":
        collect_parking()
    elif cmd == "nanum":
        collect_nanum()
    elif cmd == "tour":
        collect_tour(sys.argv[2:] or ["국립세종수목원"])
    else:
        sys.exit(__doc__)
