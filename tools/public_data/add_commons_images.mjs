// beff/src/main/assets/places.json 의 장소에 위키미디어 공용(Wikimedia Commons)의 자유 라이선스 사진을 붙인다. 인증키가 필요 없다.
//
//   node tools/public_data/add_commons_images.mjs
//
// - 장소 좌표 반경 300m 안의 사진을 찾고(geosearch), 파일 이름에 장소 이름이 들어간 사진을 먼저, 없으면 120m 안의 가장 가까운 사진을 고른다.
// - 위키미디어 요청 한도 때문에 초당 1건으로 부른다(1000곳 약 17분). 끊기면 다시 실행하면 이어서 받는다. --refresh는 공용 사진을 다시 고른다.
// - 쓸 수 있는 라이선스만 고른다(CC0 · 퍼블릭 도메인 · CC BY · CC BY-SA). 작성자와 라이선스를 imageCredit에 넣어 화면에 표기한다.
// - 이미 관광공사 사진(add_images.mjs)이 붙은 곳은 건드리지 않는다. 사진 파일은 저장하지 않는다(앱이 실행 중에 받는다).
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const PLACES = join(ROOT, "beff", "src", "main", "assets", "places.json");
const API = "https://commons.wikimedia.org/w/api.php";
const UA = "BeffApp/0.1 (Pleos Connect accessibility trip app; data build script)";
const OK_LICENSE = /^(CC0|Public domain|PD|CC BY(-SA)? \d(\.\d)?)/i;
const SKIP_TITLE = /(map|지도|logo|로고|plan|diagram|도면|노선도|symbol|flag|sign\b)/i;

const norm = (s) => s.toLowerCase().replace(/[\s·()_\-.,]/g, "");
const strip = (html) => (html || "").replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const NEAR_M = 120;
function meters(la1, lo1, la2, lo2) {
  const r = 6371000, d = Math.PI / 180;
  const x = Math.sin((la2 - la1) * d / 2) ** 2 + Math.cos(la1 * d) * Math.cos(la2 * d) * Math.sin((lo2 - lo1) * d / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(x));
}
// --refresh: 앞서 받은 공용 사진을 지우고 새 기준으로 다시 고른다(관광공사 사진은 그대로).
if (process.argv.includes("--refresh")) {
  const d = JSON.parse(readFileSync(PLACES, "utf8"));
  for (const pl of d.places) if (String(pl.imageCredit || "").startsWith("Wikimedia")) { delete pl.image; delete pl.imageCredit; }
  writeFileSync(PLACES, JSON.stringify(d), "utf8");
}

async function find(place) {
  const q = new URLSearchParams({
    action: "query", format: "json", generator: "geosearch",
    ggscoord: `${place.lat}|${place.lng}`, ggsradius: "300", ggsnamespace: "6", ggslimit: "15",
    prop: "imageinfo|coordinates", iiprop: "url|extmetadata|mime", iiurlwidth: "800",
    iiextmetadatafilter: "LicenseShortName|Artist",
  });
  // 위키미디어는 빠르게 부르면 429(너무 많은 요청)를 준다. Retry-After만큼(없으면 점점 길게) 쉬고 다시 부른다.
  let res;
  for (let attempt = 0; ; attempt++) {
    res = await fetch(`${API}?${q}`, { headers: { "User-Agent": UA } });
    if (res.status !== 429 || attempt >= 5) break;
    await sleep((Number(res.headers.get("retry-after")) || 5 * (attempt + 1)) * 1000);
  }
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const pages = Object.values((await res.json()).query?.pages ?? {});
  const name = norm(place.name);
  const cands = pages.map((p) => {
    const ii = p.imageinfo?.[0];
    if (!ii || ii.mime !== "image/jpeg" || SKIP_TITLE.test(p.title)) return null;
    const license = strip(ii.extmetadata?.LicenseShortName?.value);
    if (!OK_LICENSE.test(license)) return null;
    const named = name.length >= 2 && norm(p.title).includes(name);
    const c = p.coordinates?.[0];
    const m = c ? meters(place.lat, place.lng, c.lat, c.lon) : Infinity;
    // 이름이 안 맞는 사진은 아주 가까울 때만 쓴다(옆 건물 · 주민센터 사진이 장소 사진처럼 보이지 않게).
    if (!named && m > NEAR_M) return null;
    return {
      url: ii.thumburl || ii.url,
      license,
      artist: strip(ii.extmetadata?.Artist?.value).slice(0, 40) || "작성자 미상",
      named,
      order: m,
    };
  }).filter(Boolean);
  cands.sort((a, b) => (b.named - a.named) || (a.order - b.order));
  return cands[0] ?? null;
}

const doc = JSON.parse(readFileSync(PLACES, "utf8"));
let added = 0, kept = 0, none = 0, failed = 0;
for (const [i, pl] of doc.places.entries()) {
  // 이미 사진이 있는 곳(관광공사 · 앞선 실행에서 받은 공용 사진)은 건너뛴다 — 끊겨도 이어서 받는다.
  if (pl.image) { kept++; continue; }
  if (pl.lat == null || pl.lng == null) { none++; continue; }
  try {
    const c = await find(pl);
    if (c) {
      pl.image = c.url.replace(/^http:\/\//, "https://");
      pl.imageCredit = `Wikimedia Commons · ${c.artist} · ${c.license}`;
      added++;
    } else { delete pl.image; delete pl.imageCredit; none++; }
  } catch (e) {
    failed++;
    console.warn(`${pl.name}: ${e.message}`);
  }
  if ((i + 1) % 50 === 0) {
    console.log(`${i + 1}/${doc.places.length} · 사진 ${added} · 없음 ${none} · 실패 ${failed}`);
    writeFileSync(PLACES, JSON.stringify(doc), "utf8");   // 중간 저장(끊겨도 받은 만큼 남는다)
  }
  await sleep(1000);   // 초당 1건(429 방지)
}
writeFileSync(PLACES, JSON.stringify(doc), "utf8");
console.log(`끝: 새 공용 사진 ${added} · 이미 있던 사진 ${kept} · 없음 ${none} · 실패 ${failed} → ${PLACES}`);
