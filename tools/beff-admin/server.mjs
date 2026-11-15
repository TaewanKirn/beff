#!/usr/bin/env node
/**
 * 베프 플로우 점검 페이지. 의존성 없는 Node 서버(개발 전용).
 *
 *   node tools/beff-admin/server.mjs        → http://127.0.0.1:5174
 *
 * 버튼을 누르면 adb로 에뮬레이터의 베프에 화면 점검 명령(AppViewModel.debugJump)을 보낸다.
 * `am start -f 0x20000 --es debug <대상>`: 켜져 있으면 앞으로 불러 전달하고, 꺼져 있으면 켜면서 전달한다.
 * 지금 화면은 앱이 남기는 `BeffDebug: state {...}` 로그를 `adb logcat`으로 읽어 보여 준다.
 * 차량에는 아무것도 보내지 않는다(기어 덮어쓰기도 앱 안에서만).
 */
import { spawn, execFile } from 'node:child_process'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const PORT = Number(process.env.PORT ?? 5174)
const PKG = 'com.beff.app'
const ACTIVITY = `${PKG}/.MainActivity`
const DIR = path.dirname(fileURLToPath(import.meta.url))
const ADB = process.env.ADB ?? findAdb()
/** 이 폴더에서 내보내는 파일. flow.json은 Figma 「1112 검증 › 8. 전체 화면 플로우」를 펴서 뽑은 것이다. */
const STATIC = {
  '/index.html': 'text/html; charset=utf-8',
  '/flow-view.js': 'text/javascript; charset=utf-8',
  '/flow.json': 'application/json; charset=utf-8',
}
/** debugJump 대상 이름: home_trip, unload:tour-128611 … */
const TARGET = /^[A-Za-z0-9_:-]{1,60}$/

function findAdb() {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? path.join(os.homedir(), 'Library/Android/sdk')
  const candidate = path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb')
  return fs.existsSync(candidate) ? candidate : 'adb'
}

/** adb shell은 인자를 이어 붙여 기기 셸에서 다시 해석하므로 하나씩 작은따옴표로 감싼다. */
const quote = (s) => `'${String(s).replaceAll("'", `'\\''`)}'`

function adb(args, timeout = 8000) {
  return new Promise((resolve) => {
    execFile(ADB, args, { timeout }, (error, stdout, stderr) =>
      resolve({ ok: !error, out: `${stdout}${stderr}`.trim() }),
    )
  })
}

const shell = (...args) => adb(['shell', args.map(quote).join(' ')])

let lastCommand = null

// ---------------- 에뮬레이터 위치 ----------------
// 베프의 도착 판정은 FusedLocation(= 에뮬레이터 GPS)이 목적지 150m 안에 들어와야 한다. `adb emu geo fix`가 그대로 반영된다.
// 점검 목적지(서울숲, tour-128611)의 좌표는 앱 데이터에서 읽는다. 앱에는 아무것도 보내지 않는다.
const DEST_ID = 'tour-128611'
/** 에뮬레이터의 원래 위치(성남 수정구). AppViewModel의 `arrive` 점검 대상과 같다. */
const HOME_GEO = { lat: 37.45, lng: 127.1 }

function destGeo() {
  try {
    const file = path.join(DIR, '..', '..', 'beff', 'src', 'main', 'assets', 'places.json')
    const data = JSON.parse(fs.readFileSync(file, 'utf-8'))
    const p = (Array.isArray(data) ? data : data.places ?? []).find((x) => x.id === DEST_ID)
    return p && typeof p.lat === 'number' && typeof p.lng === 'number' ? { lat: p.lat, lng: p.lng } : null
  } catch {
    return null
  }
}

/** 앱이 지금 정한 목적지 · 첫 경유지 좌표(상태 로그 destGeo · stopGeos). 아직 못 받았으면 null. */
function appGeo(target) {
  if (target === 'geo_dest') return appState?.destGeo ?? null
  if (target === 'geo_stop') return appState?.stopGeos?.[0] ?? null
  return null
}

async function moveGeo(target) {
  // 앱이 정한 장소를 그대로 따라간다. 목적지는 앱이 아직 모르면 점검 기본 목적지(서울숲)로 대신한다.
  const pos = target === 'geo_home' ? HOME_GEO : (appGeo(target) ?? (target === 'geo_dest' ? destGeo() : null))
  if (!pos) return { ok: false, out: target === 'geo_stop' ? '앱에 정해진 경유지가 없습니다' : '목적지 좌표를 찾지 못했습니다' }
  // 에뮬레이터 콘솔의 geo fix는 「경도 위도」 순서다.
  const result = await adb(['emu', 'geo', 'fix', String(pos.lng), String(pos.lat)])
  const ok = result.ok && /OK/.test(result.out)
  lastCommand = { target, query: null, at: Date.now(), ok }
  return { ...lastCommand, out: result.out }
}

// ---------------- 가상 콘솔: 기어 · 속도 ----------------
// 차량 쪽 기어 · 속도는 Pleos 가상 콘솔(에뮬레이터 :8088)로 바꾼다. 콘솔 페이지를 브라우저로 열어 두어야 응답한다(없으면 emulator_inactive).
// 콘솔이 안 되면 앱 안에서만 기어를 덮어쓰는 gear_p · gear_d로 대신한다(계기판 속도는 안 바뀐다).
const VC = 'http://127.0.0.1:8088'
const DRIVE_SPEED = 60

let vcOpenedAt = 0

/** 콘솔 페이지를 브라우저로 연다. 새 탭이 쌓이지 않게 30초 안에는 다시 열지 않는다. */
function openVcPage() {
  if (Date.now() - vcOpenedAt < 30_000) return
  vcOpenedAt = Date.now()
  execFile(process.platform === 'darwin' ? 'open' : process.platform === 'win32' ? 'cmd' : 'xdg-open',
    process.platform === 'win32' ? ['/c', 'start', '', `${VC}/`] : [`${VC}/`], () => {})
}

async function vcCall(pathAndQuery) {
  const res = await fetch(`${VC}${pathAndQuery}`, { method: 'PUT', signal: AbortSignal.timeout(3000) })
  const body = await res.json().catch(() => ({}))
  return { ok: res.ok && !body.error, inactive: body.error === 'emulator_inactive' }
}

/**
 * 가상 콘솔 호출. 안 되면 adb 포워딩을 다시 잡고, 페이지가 없으면(emulator_inactive · 연결 실패) 브라우저로 열어 기다린 뒤 한 번 더 시도한다.
 * 콘솔은 브라우저 페이지가 열려 있을 때만 응답한다(계기판 장면 · 차량 신호 브리지가 그 페이지 안에서 돈다).
 */
async function vcPut(pathAndQuery) {
  try {
    await adb(['forward', 'tcp:8088', 'tcp:8088'])
    let r = await vcCall(pathAndQuery).catch(() => ({ ok: false, inactive: true }))
    if (!r.ok && r.inactive) {
      openVcPage()
      await new Promise((resolve) => setTimeout(resolve, 6000))
      r = await vcCall(pathAndQuery).catch(() => ({ ok: false }))
    }
    return r.ok
  } catch {
    return false
  }
}

/**
 * vc_drive: 기어 D + 속도 올리기 + ADAS 켜기 · vc_park: 속도 0 + 기어 P + ADAS 대기. 콘솔이 되면 앱은 실제 기어를 따르게(gear_auto) 풀고, 안 되면 앱 안에서만 덮어쓴다.
 * ADAS는 콘솔 /vehicle/driving-mode의 full_autonomous(계기판 핸들 아이콘이 켜지고 설정 속도가 뜬다). 주행 중에는 ADAS를 켜고 가는 것으로 한다(사용자 결정, 2026-11-13).
 */
async function moveVehicle(target) {
  const drive = target === 'vc_drive'
  const gearOk = await vcPut(`/vehicle/gear?gear=${drive ? 'Drive' : 'Park'}`)
  const speedOk = gearOk
    ? await vcPut(`/vehicle/controls?speed=${drive ? DRIVE_SPEED : 0}&accelerator=${drive ? 1 : 0}&brake=${drive ? 0 : 1}`)
    : false
  // ADAS 설정 속도는 주행 속도와 같게(km/h → m/s). 정차하면 대기(ready)로 돌린다.
  const adasOk = gearOk
    ? await vcPut(`/vehicle/driving-mode?ad_mode_state=${drive ? 'full_autonomous' : 'ready'}&target_speed_mps=${(DRIVE_SPEED / 3.6).toFixed(3)}`)
    : false
  const sent = await deliver({ target: gearOk ? 'gear_auto' : drive ? 'gear_d' : 'gear_p' })
  lastCommand = { target, query: null, at: Date.now(), ok: sent.ok }
  return { ...lastCommand, out: gearOk ? `가상 콘솔 ${drive ? 'D' : 'P'}${speedOk ? ' · 속도' : ''}${adasOk ? (drive ? ' · ADAS 켬' : ' · ADAS 대기') : ''}` : '가상 콘솔이 안 돼 앱 안에서만 덮어썼습니다 (콘솔 페이지를 열어 주세요)' }
}

async function deliver({ target, query }) {
  if (target === 'vc_drive' || target === 'vc_park') return moveVehicle(target)
  if (target === 'geo_dest' || target === 'geo_stop' || target === 'geo_home') return moveGeo(target)
  const extras = []
  if (target) extras.push('--es', 'debug', target)
  if (query) extras.push('--es', 'query', query)
  // FLAG_ACTIVITY_REORDER_TO_FRONT(0x20000): 떠 있는 MainActivity를 앞으로 부르고 onNewIntent로 전달한다.
  // 꺼져 있으면 같은 명령으로 켜진다. 런처 인텐트나 플래그 없는 am start는 standard 화면을 하나 더 쌓으므로 쓰지 않는다.
  const result = await shell('am', 'start', '-n', ACTIVITY, '-f', '0x00020000', ...extras)
  const ok = result.ok && !/Error/.test(result.out)
  lastCommand = { target: target ?? null, query: query ?? null, at: Date.now(), ok }
  return { ...lastCommand, out: result.out }
}

// ---------------- 지금 화면: logcat ----------------

let appState = null
let appStateAt = null
let logcat = null

function startLogcat() {
  // -T 1: 마지막 한 줄부터. 앱이 다시 켜지면 첫 화면 로그가 바로 들어온다.
  logcat = spawn(ADB, ['logcat', '-v', 'raw', '-T', '1', 'BeffDebug:I', '*:S'])
  let buffer = ''
  logcat.stdout.on('data', (chunk) => {
    buffer += chunk.toString('utf-8')
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      const i = line.indexOf('state {')
      if (i < 0) continue
      try {
        appState = JSON.parse(line.slice(i + 6))
        appStateAt = Date.now()
      } catch {
        // 잘린 줄은 버린다
      }
    }
  })
  logcat.on('exit', () => {
    logcat = null
    setTimeout(startLogcat, 2000) // 에뮬레이터가 꺼졌다 켜져도 다시 붙는다
  })
  logcat.on('error', () => {})
}

async function status() {
  const devices = (await adb(['devices'])).out
  const device = devices.split('\n').slice(1).some((l) => /\tdevice$/.test(l.trim()) || l.includes('\tdevice'))
  const running = device ? (await shell('pidof', PKG)).out.length > 0 : false
  return { device, running, app: appState, appAgeMs: appStateAt && Date.now() - appStateAt, command: lastCommand }
}

// ---------------- HTTP ----------------

function send(res, code, body, type = 'application/json; charset=utf-8') {
  res.writeHead(code, { 'Content-Type': type, 'Cache-Control': 'no-store' })
  res.end(typeof body === 'string' ? body : JSON.stringify(body))
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = ''
    req.on('data', (c) => {
      raw += c
      if (raw.length > 4096) req.destroy()
    })
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch (e) {
        reject(e)
      }
    })
  })
}

const server = http.createServer(async (req, res) => {
  const url = req.url?.split('?')[0]
  try {
    const file = STATIC[url === '/' ? '/index.html' : url]
    if (req.method === 'GET' && file) {
      send(res, 200, fs.readFileSync(path.join(DIR, url === '/' ? 'index.html' : url.slice(1)), 'utf-8'), file)
      return
    }
    if (req.method === 'GET' && url === '/api/status') {
      send(res, 200, await status())
      return
    }
    if (req.method === 'POST' && url === '/api/jump') {
      const body = await readJson(req)
      const target = typeof body.target === 'string' ? body.target : undefined
      const query = typeof body.query === 'string' ? body.query.trim().slice(0, 40) : undefined
      if ((!target && !query) || (target && !TARGET.test(target))) {
        send(res, 400, { error: '대상이 잘못되었습니다' })
        return
      }
      send(res, 200, await deliver({ target, query: query || undefined }))
      return
    }
    send(res, 404, { error: '없는 경로입니다' })
  } catch {
    send(res, 400, { error: '요청을 처리하지 못했습니다' })
  }
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`베프 플로우 점검: http://127.0.0.1:${PORT}  (adb: ${ADB})`)
  startLogcat()
})

process.on('SIGINT', () => {
  logcat?.kill()
  process.exit(0)
})
