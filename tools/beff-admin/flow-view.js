/**
 * Figma 플로우차트(flow.json)를 그대로 다시 그리고, 상자를 누르면 화면 바로 가기를 부른다.
 * flow.json은 Figma 섹션의 노드를 평평하게 편 것이다: b = 상자(프레임 · 사각형), t = 글자, v = 화살표(벡터 경로).
 * 페타 mobile/admin/flow-view.ts와 같은 방식이다. 한쪽을 고치면 다른 쪽도 맞춘다.
 *
 *   createFlowView(container, flow, { boxes, onPick })
 *   boxes: { [Figma 상자 이름]: { variants: [{ label, ...payload }], hint?, go? } }
 *   onPick(variant) — 상자(변형이 하나) 또는 메뉴 항목을 눌렀을 때
 *   반환값.setCurrent(boxName | null) — 지금 화면 상자를 강조한다
 */
export function createFlowView(container, flow, { boxes, onPick, onHint }) {
  container.classList.add('flow-wrap')
  const stage = document.createElement('div')
  stage.className = 'flow-stage'
  stage.style.width = `${flow.w}px`
  stage.style.height = `${flow.h}px`

  const svgNS = 'http://www.w3.org/2000/svg'
  const svg = document.createElementNS(svgNS, 'svg')
  svg.setAttribute('width', flow.w)
  svg.setAttribute('height', flow.h)
  svg.classList.add('flow-lines')
  const defs = document.createElementNS(svgNS, 'defs')
  svg.append(defs)
  const markers = new Map()
  const marker = (color) => {
    if (markers.has(color)) return markers.get(color)
    const id = `arrow-${markers.size}`
    const m = document.createElementNS(svgNS, 'marker')
    m.setAttribute('id', id)
    m.setAttribute('viewBox', '0 0 10 10')
    m.setAttribute('refX', '9')
    m.setAttribute('refY', '5')
    m.setAttribute('markerWidth', '6')
    m.setAttribute('markerHeight', '6')
    m.setAttribute('orient', 'auto-start-reverse')
    const p = document.createElementNS(svgNS, 'path')
    p.setAttribute('d', 'M 1 1 L 9 5 L 1 9')
    p.setAttribute('fill', 'none')
    p.setAttribute('stroke', color)
    p.setAttribute('stroke-width', '1.6')
    p.setAttribute('stroke-linecap', 'round')
    p.setAttribute('stroke-linejoin', 'round')
    m.append(p)
    defs.append(m)
    markers.set(color, id)
    return id
  }

  const hot = new Map() // 상자 이름 → 요소
  const jumpTarget = (name) => {
    // 'jump A2→B-1 홈' → 'B-1' 로 시작하는 상자
    const to = name.split('→')[1]?.trim()
    const code = to?.split(' ')[0]
    if (!code) return null
    return Object.keys(boxes).find((k) => k === to || k.startsWith(code + ' ') || k.startsWith(code + '~') || k.startsWith(code + ' ·')) ?? null
  }

  for (const n of flow.nodes) {
    if (n.k === 'v') {
      const p = document.createElementNS(svgNS, 'path')
      p.setAttribute('d', n.d)
      p.setAttribute('transform', `translate(${n.x} ${n.y})`)
      p.setAttribute('fill', 'none')
      p.setAttribute('stroke', n.c ?? '#59666f')
      p.setAttribute('stroke-width', n.sw ?? 2)
      p.setAttribute('stroke-linejoin', 'round')
      if (n.dash) p.setAttribute('stroke-dasharray', n.dash.join(' '))
      if (n.a1) p.setAttribute('marker-end', `url(#${marker(n.c ?? '#59666f')})`)
      if (n.a0) p.setAttribute('marker-start', `url(#${marker(n.c ?? '#59666f')})`)
      svg.append(p)
      continue
    }
    const el = document.createElement('div')
    el.style.left = `${n.x}px`
    el.style.top = `${n.y}px`
    el.style.width = `${n.w}px`
    el.style.height = `${n.h}px`
    if (n.k === 'b') {
      el.className = 'flow-box'
      const layers = [...n.f].reverse()
      if (layers.length) {
        el.style.background = layers.length === 1
          ? layers[0]
          : layers.slice(0, -1).map((c) => `linear-gradient(${c}, ${c})`).join(', ') + ', ' + layers[layers.length - 1]
      }
      if (n.s) el.style.border = `${n.sw}px ${n.dash ? 'dashed' : 'solid'} ${n.s}`
      el.style.borderRadius = `${Math.min(n.r, n.h / 2)}px`
      const name = n.nm
      const key = boxes[name] ? name : name.startsWith('jump ') ? jumpTarget(name) : null
      if (key && boxes[key]) {
        el.classList.add('flow-hot')
        if (name.startsWith('jump ')) el.classList.add('flow-jump')
        else hot.set(name, el)
        const spec = boxes[key]
        el.title = spec.hint ?? (spec.variants?.length > 1 ? `${spec.variants.length}가지 상태` : spec.variants?.[0]?.label ?? '')
        if (!name.startsWith('jump ') && spec.variants?.length > 1) el.dataset.count = spec.variants.length
        if (!spec.variants?.length && spec.hint) el.classList.add('flow-hint')
        el.addEventListener('click', (e) => {
          e.stopPropagation()
          if (name.startsWith('jump ')) {
            const v = spec.variants?.[0] ?? (spec.go && { label: key, ...spec.go })
            if (v) onPick(v)
            return
          }
          open(el, spec)
        })
      }
    } else {
      el.className = 'flow-text'
      el.textContent = n.s
      el.style.fontSize = `${n.fs}px`
      el.style.fontWeight = n.fw
      el.style.color = n.c
      el.style.textAlign = n.al === 'c' ? 'center' : n.al === 'r' ? 'right' : 'left'
      if (n.ar === 'WIDTH_AND_HEIGHT') el.style.whiteSpace = 'pre'
    }
    stage.append(el)
  }
  // 화살표는 상자 위에 그려야 꺾인 선이 상자 뒤로 숨지 않는다. 클릭은 상자가 받는다(pointer-events: none).
  stage.append(svg)

  const menu = document.createElement('div')
  menu.className = 'flow-menu'
  menu.hidden = true
  container.append(stage, menu)
  document.addEventListener('click', () => (menu.hidden = true))

  function open(el, spec) {
    const variants = spec.variants ?? []
    if (variants.length === 1 && !spec.hint) {
      onPick(variants[0])
      return
    }
    if (!variants.length) {
      onHint?.(spec)
      return
    }
    menu.replaceChildren()
    if (spec.hint) {
      const p = document.createElement('p')
      p.textContent = spec.hint
      menu.append(p)
    }
    for (const v of variants) {
      const b = document.createElement('button')
      b.type = 'button'
      b.textContent = v.label
      b.addEventListener('click', (e) => {
        e.stopPropagation()
        menu.hidden = true
        onPick(v)
      })
      menu.append(b)
    }
    const r = el.getBoundingClientRect()
    const c = container.getBoundingClientRect()
    menu.style.left = `${r.left - c.left + container.scrollLeft}px`
    menu.style.top = `${r.bottom - c.top + container.scrollTop + 6}px`
    menu.hidden = false
  }

  let scale = 1
  function fit(mode = 'fit') {
    scale = mode === 'fit' ? Math.min(1, (container.clientWidth - 2) / flow.w) : Number(mode)
    stage.style.transform = `scale(${scale})`
    stage.style.marginBottom = `${flow.h * (scale - 1)}px`
    stage.style.marginRight = `${flow.w * (scale - 1)}px`
  }
  let mode = 'fit'
  new ResizeObserver(() => fit(mode)).observe(container)

  return {
    zoom(m) {
      mode = m
      fit(m)
    },
    setCurrent(names) {
      const list = Array.isArray(names) ? names : [names]
      for (const [k, el] of hot) el.classList.toggle('flow-current', list.includes(k))
    },
  }
}

export const FLOW_CSS = `
.flow-wrap { position: relative; overflow: auto; background: #fff; border: 1px solid var(--line); border-radius: 14px; }
.flow-stage { position: relative; transform-origin: 0 0; }
.flow-stage > div { position: absolute; box-sizing: border-box; }
.flow-lines { position: absolute; left: 0; top: 0; pointer-events: none; overflow: visible; }
.flow-text { pointer-events: none; line-height: 1.2; white-space: pre-wrap; font-family: Pretendard, 'Apple SD Gothic Neo', sans-serif; }
.flow-hot { cursor: pointer; transition: box-shadow .15s, transform .15s; }
.flow-hot:hover { box-shadow: 0 0 0 6px rgba(0, 131, 127, .25); }
.flow-hot[data-count]::after {
  content: attr(data-count); position: absolute; right: -14px; bottom: -14px; min-width: 34px; height: 34px; padding: 0 8px;
  border-radius: 17px; background: #1d2327; color: #fff; font: 700 20px/34px Pretendard, sans-serif; text-align: center;
}
.flow-hint { opacity: .75; }
.flow-current { box-shadow: 0 0 0 10px rgba(0, 131, 127, .9), 0 0 0 22px rgba(0, 131, 127, .2) !important; }
.flow-menu {
  position: absolute; z-index: 5; display: flex; flex-direction: column; gap: 4px; min-width: 200px; max-width: 320px; padding: 8px;
  background: #fff; border: 1px solid var(--line); border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,.15);
}
.flow-menu[hidden] { display: none; }
.flow-menu p { margin: 2px 4px 6px; font-size: 12.5px; color: var(--sub); line-height: 1.45; }
.flow-menu button { padding: 8px 10px; border: 0; border-radius: 8px; background: transparent; font: inherit; text-align: left; cursor: pointer; }
.flow-menu button:hover { background: var(--teal-soft, #eee); }
`
