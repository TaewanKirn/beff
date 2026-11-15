// beff/src/main/assets/places.json 의 장소마다 대표 사진 주소(image)를 붙인다. (한국관광공사 무장애 여행정보 KorWithService2)
//
//   node tools/public_data/add_images.mjs
//
// - 지역 목록(areaBasedList2, 서울 · 인천 · 경기)에 들어 있는 firstimage(없으면 firstimage2)를 contentId로 맞춘다. 호출은 몇 번뿐이다.
// - 인증키는 collect.py와 같은 방식(환경변수 DATA_GO_KR_KEY, 없으면 Windows 사용자 환경변수)으로만 읽는다. 출력에 키를 남기지 않는다.
// - 사진 주소는 https로 바꿔 넣는다(앱은 평문 http를 막는다). 사진 파일 자체는 저장하지 않는다(앱이 실행 중에 받아 쓴다).
import { readFileSync, writeFileSync } from "node:fs";
import { execSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const PLACES = join(ROOT, "beff", "src", "main", "assets", "places.json");
const BASE = "https://apis.data.go.kr/B551011/KorWithService2/areaBasedList2";
const REGIONS = { 서울: "11", 인천: "28", 경기: "41" }; // tour_region.py와 같은 법정동 시도 코드

function loadKey() {
  let key = process.env.DATA_GO_KR_KEY;
  if (!key && process.platform === "win32") {
    try {
      const out = execSync('reg query "HKCU\\Environment" /v DATA_GO_KR_KEY', { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
      key = out.match(/DATA_GO_KR_KEY\s+REG_\w+\s+(\S+)/)?.[1];
    } catch { /* 없음 */ }
  }
  if (!key) {
    console.error("DATA_GO_KR_KEY 환경변수가 없어요. 공공데이터포털 인증키를 넣고 다시 실행하세요.");
    process.exit(1);
  }
  // 공공데이터포털 Encoding 키(%가 들어 있음)는 그대로, Decoding 키는 인코딩해서 쓴다(이중 인코딩 방지).
  return key.includes("%") ? key : encodeURIComponent(key);
}

const KEY = loadKey();
const redact = (s) => String(s).split(KEY).join("***");

async function page(code, pageNo) {
  const q = new URLSearchParams({ MobileOS: "ETC", MobileApp: "Beff", _type: "json", lDongRegnCd: code, numOfRows: "500", pageNo: String(pageNo) });
  const res = await fetch(`${BASE}?serviceKey=${KEY}&${q}`);
  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { throw new Error(`HTTP ${res.status} ${redact(text.slice(0, 200))}`); }
  const header = data?.response?.header;
  if (header?.resultCode !== "0000") throw new Error(`${header?.resultCode ?? "?"} ${header?.resultMsg ?? redact(text.slice(0, 200))}`);
  const body = data.response.body;
  let items = body.items?.item ?? [];
  if (!Array.isArray(items)) items = [items];
  return { items, total: Number(body.totalCount ?? 0) };
}

const images = new Map();
for (const [name, code] of Object.entries(REGIONS)) {
  let got = 0;
  for (let p = 1; ; p++) {
    const { items, total } = await page(code, p);
    for (const it of items) {
      const url = (it.firstimage || it.firstimage2 || "").trim();
      if (url) images.set(String(it.contentid), url.replace(/^http:\/\//, "https://"));
    }
    got += items.length;
    console.log(`${name} ${p}쪽: +${items.length} (${got}/${total})`);
    if (!items.length || got >= total) break;
    await new Promise((r) => setTimeout(r, 200));
  }
}

const doc = JSON.parse(readFileSync(PLACES, "utf8"));
let withImage = 0;
for (const pl of doc.places) {
  const url = images.get(String(pl.contentId));
  // 관광공사 사진이 있으면 그것을 쓰고(위키미디어 공용 사진보다 우선), 없으면 기존 값(공용 사진)을 그대로 둔다.
  if (url) { pl.image = url; pl.imageCredit = "사진 · 한국관광공사"; withImage++; }
}
writeFileSync(PLACES, JSON.stringify(doc), "utf8");
console.log(`사진 붙임: ${withImage}/${doc.places.length}곳 → ${PLACES}`);
