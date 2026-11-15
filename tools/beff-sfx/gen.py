# 베프 효과음 합성 (직접 만든 소리 — 외부 음원 · 라이선스 없음)
#   python3 -I gen.py <출력 폴더>   → sfx_tap / sfx_toast / sfx_notify / sfx_warning .wav
#
# 1113 다시 만듦("효과음이 너무 구려"): 높고 날카로운 사인 「삑」(1.3~1.7kHz) 대신
#  - 낮은 음역(탭 ~520Hz, 알림 C5~G5) · 둥근 음색(마림바식: 위 배음은 빨리 사라짐)
#  - 부드러운 어택(3~10ms) · 저역 통과(날카로움 제거) · 짧은 잔향(작은 방)으로 차 안에서 거슬리지 않게
#  - 탭은 거의 안 들릴 만큼 작고 짧은 「톡」. 경고만 또렷하게(그래도 날카롭지 않게).
import numpy as np, wave, sys, os

SR = 44100
out = sys.argv[1]
rng = np.random.default_rng(7)


def t_of(dur):
    return np.arange(int(SR * dur)) / SR


def mallet(f, dur, decay, partials=((1, 1.0, 1.0), (4, 0.18, 0.25), (10, 0.05, 0.1)), attack=0.006):
    """마림바식 음: (배수, 세기, 감쇠 비율). 위 배음일수록 빨리 사라져 처음만 또렷하고 끝은 둥글다."""
    t = t_of(dur)
    s = np.zeros_like(t)
    for k, a, dk in partials:
        s += a * np.sin(2 * np.pi * f * k * t) * np.exp(-t / (decay * dk))
    return s * np.clip(t / attack, 0, 1) ** 2


def glass(f, dur, decay, attack=0.01):
    """유리잔식 음: 기음 + 살짝 어긋난 2 · 3배음(맥놀이)으로 맑고 부드럽게."""
    t = t_of(dur)
    s = (np.sin(2 * np.pi * f * t)
         + 0.35 * np.sin(2 * np.pi * f * 2.0 * 1.002 * t) * np.exp(-t / (decay * 0.5))
         + 0.12 * np.sin(2 * np.pi * f * 3.0 * 0.998 * t) * np.exp(-t / (decay * 0.3)))
    return s * np.exp(-t / decay) * np.clip(t / attack, 0, 1) ** 2


def lowpass(x, cutoff):
    """2차 저역 통과(1극 두 번)."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.asarray(x, dtype=float).copy()
    for _ in range(2):
        acc = 0.0
        for i in range(len(y)):
            acc = (1 - a) * y[i] + a * acc
            y[i] = acc
    return y


def room(x, length=0.35, mix=0.18):
    """작은 방 잔향: 지수 감쇠 노이즈와 컨볼루션."""
    n = int(SR * length)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (SR * length / 5))
    ir = lowpass(ir, 3500)
    ir /= np.sqrt(np.sum(ir ** 2))
    wet = np.convolve(x, ir)
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry * (1 - mix) + wet * mix


def place(buf, sig, at):
    i = int(SR * at)
    end = min(len(buf), i + len(sig))
    buf[i:end] += sig[: end - i]


def write(name, buf, peak):
    buf = buf / np.max(np.abs(buf)) * peak
    idx = np.where(np.abs(buf) > 0.002)[0]          # 끝의 무음은 잘라 짧게
    fade = int(SR * 0.02)
    if len(idx): buf = buf[: min(len(buf), idx[-1] + 1 + fade)]
    buf[-fade:] *= np.linspace(1, 0, fade)
    with wave.open(os.path.join(out, name), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(buf, -1, 1) * 32767).astype("<i2").tobytes())


# 탭: 낮고 짧은 나무 「톡」(약 40ms). 버튼마다 울리므로 가장 작고 둥글게.
b = np.zeros(int(SR * 0.06))
place(b, mallet(523, 0.05, 0.012, ((1, 1.0, 1.0), (3.9, 0.12, 0.3)), attack=0.003), 0)
write("sfx_tap.wav", lowpass(b, 2400), 0.22)

# 토스트(음성 안내 · 말풍선): 올라가는 두 음 마림바 G5 → C6.
b = np.zeros(int(SR * 0.5))
place(b, mallet(784, 0.35, 0.09), 0)
place(b, mallet(1046.5, 0.4, 0.11), 0.09)
write("sfx_toast.wav", room(lowpass(b, 4200), 0.3, 0.15), 0.4)

# 알림(도착 · 체크인 등): 유리잔 3음 C5 · E5 · G5 아르페지오 + 잔향.
b = np.zeros(int(SR * 1.1))
for i, f in enumerate((523.25, 659.25, 783.99)):
    place(b, glass(f, 0.9, 0.32), i * 0.12)
write("sfx_notify.wav", room(lowpass(b, 5000), 0.45, 0.22), 0.5)

# 경고(주의 · 확인 필요): 내려가는 두 음 E5 → B4 한 번. 배음을 조금 넣어 또렷하되 날카롭지 않게.
b = np.zeros(int(SR * 0.6))
w = ((1, 1.0, 1.0), (2, 0.35, 0.6), (3, 0.12, 0.4))
place(b, mallet(659.25, 0.25, 0.10, w, attack=0.005), 0)
place(b, mallet(493.88, 0.35, 0.13, w, attack=0.005), 0.16)
write("sfx_warning.wav", room(lowpass(b, 3800), 0.3, 0.12), 0.55)
