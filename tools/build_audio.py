"""Original Fruit Battle PCM assets. Standard library only; no external samples.

Run: python tools/build_audio.py
All musical phrases and effect envelopes below are authored for this project.
"""
from pathlib import Path
import array
import hashlib
import math
import random
import wave

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/fruit"
RATE = 22050
random.seed(4217)


def midi(n):
    return 440 * 2 ** ((n - 69) / 12)


def tone(buffer, start, duration, frequency, gain=.2, shape="triangle", end=None):
    begin, count = int(start * RATE), int(duration * RATE)
    phase = 0.0
    for k in range(count):
        index = begin + k
        if index >= len(buffer):
            break
        t = k / RATE
        f = frequency if end is None else frequency + (end - frequency) * k / max(1, count)
        phase = (phase + f / RATE) % 1
        if shape == "noise":
            value = random.uniform(-1, 1)
        elif shape == "pulse":
            value = .6 if phase < .25 else -.2
        elif shape == "sine":
            value = math.sin(phase * math.tau)
        else:
            value = 1 - 4 * abs(phase - .5)
        envelope = min(1, t / .008) * min(1, (duration - t) / .025)
        if shape == "noise":
            envelope *= (1 - k / max(1, count)) ** 2
        buffer[index] += value * gain * max(0, envelope)


def save(relative, samples):
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    peak = max(.75, max(abs(v) for v in samples))
    pcm = array.array("h", [round(max(-1, min(1, v / peak * .65)) * 32767) for v in samples])
    with wave.open(str(path), "wb") as output:
        output.setparams((1, 2, RATE, len(pcm), "NONE", "not compressed"))
        output.writeframes(pcm.tobytes())


def phrase(name, notes, beat=.09, noise=False):
    data = [0.0] * int((len(notes) * beat + .12) * RATE)
    for i, note in enumerate(notes):
        tone(data, i * beat, beat * 1.1, midi(note), .32, "triangle")
        tone(data, i * beat, beat * .6, midi(note) * 2, .10, "pulse")
    if noise:
        tone(data, 0, .18, 50, .2, "noise")
    save("sfx/" + name + ".wav", data)


for name, notes, beat in [
    ("click", [79], .035), ("confirm", [67, 74], .08),
    ("deny", [50, 45], .09), ("match-found", [60, 64, 67, 76], .10),
    ("countdown", [72], .07), ("go", [72, 79, 84], .09),
    ("catch-1", [79, 84], .055), ("catch-2", [81, 86], .055),
    ("catch-3", [76, 83], .055), ("wrong", [48, 43], .07),
    ("bomb-warning", [84, 72, 84], .08), ("combo", [72, 76, 79, 84], .07),
    ("freeze", [88, 83, 76, 71], .07), ("freeze-end", [71, 76, 83], .055),
    ("magnet", [60, 67, 74, 81, 88], .045), ("magnet-end", [81, 74, 67], .05),
    # Short C7/G7 bell-like cue; intentionally distinct from catches and skills.
    ("target", [96, 103], .045), ("time-warning", [67, 67, 79], .09),
    ("win", [60, 64, 67, 72, 76, 79, 84], .15),
    ("lose", [67, 64, 62, 55], .21), ("draw", [60, 67, 64, 72], .17),
]:
    phrase(name, notes, beat)

explosion = [0.0] * int(.65 * RATE)
tone(explosion, 0, .6, 80, .65, "noise")
tone(explosion, 0, .28, 160, .3, "sine", 35)
save("sfx/bomb-hit.wav", explosion)


def music(name, bpm, busy):
    beat = 60 / bpm
    bars = 16
    data = [0.0] * int(bars * 4 * beat * RATE)
    # C / Am / F / G. Original pentatonic phrases leave room for gameplay cues.
    roots = [48, 45, 41, 43]
    phrases = [
        [72, 76, 79, 76, 74, 72, 67, 69],
        [69, 72, 76, 79, 76, 72, 69, 67],
        [65, 69, 72, 77, 76, 72, 69, 72],
        [67, 71, 74, 79, 77, 74, 71, 67],
    ]
    for bar in range(bars):
        root = roots[bar % 4]
        for b in range(4):
            start = (bar * 4 + b) * beat
            tone(data, start, beat * .74, midi(root - 12), .16, "triangle")
            tone(data, start + beat * .5, beat * .22, midi(root + 7), .09, "pulse")
            if busy or b % 2:
                tone(data, start, .045, 100, .035, "noise")
            if busy and b % 2 == 0:
                tone(data, start, .1, 120, .09, "sine", 48)
        melody = phrases[bar % 4]
        for n, note in enumerate(melody):
            if not busy and n in (3, 7):
                continue
            start = (bar * 4 + n * .5) * beat
            tone(data, start, beat * (.34 if busy else .42), midi(note), .15, "triangle")
            if busy and bar >= 8:
                tone(data, start, beat * .24, midi(note + 12), .035, "pulse")
    save("music/" + name + ".wav", data)


music("menu", 108, False)
music("game", 144, True)
paths = sorted((ROOT / "sfx").glob("*.wav")) + sorted((ROOT / "music").glob("*.wav"))
manifest = "\n".join(hashlib.sha256(p.read_bytes()).hexdigest() + "  " + str(p.relative_to(ROOT)) for p in paths)
(ROOT / "audio-sha256.txt").write_text(manifest + "\n", encoding="utf-8")
print(f"Generated {len(paths)} original PCM WAV assets ({sum(p.stat().st_size for p in paths):,} bytes)")
