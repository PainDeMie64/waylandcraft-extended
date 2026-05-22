#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "usage: $0 /tmp/waylandcraft-input-trace-*.java.jsonl [/tmp/waylandcraft-input-trace-*.native.jsonl]" >&2
  exit 2
fi

python3 - "$@" <<'PY'
import json
import sys
from collections import Counter, defaultdict

events = []
for path in sys.argv[1:]:
    with open(path, "r", encoding="utf-8") as fh:
        for line_no, line in enumerate(fh, 1):
            line = line.strip()
            if not line:
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError as exc:
                print(f"{path}:{line_no}: bad json: {exc}")
                continue
            event["_path"] = path
            event["_line"] = line_no
            event["_time"] = event.get("wall_nanos") or event.get("wall_millis", 0) * 1_000_000 or event.get("ts_nanos", 0)
            events.append(event)

events.sort(key=lambda event: (event.get("_time", 0), event.get("_path", ""), event.get("_line", 0)))
by_trace = defaultdict(list)
summary = Counter()
for event in events:
    by_trace[event.get("trace", 0)].append(event)
    summary[(event.get("layer", "?"), event.get("event_type", "?"))] += 1

def value(event, *names):
    for name in names:
        if name in event:
            return event[name]
    return ""

def has_kind(trace_events, *needles):
    return any(any(needle in event.get("event_type", "") for needle in needles) for event in trace_events)

def first(trace_events, *needles):
    for event in trace_events:
        if any(needle in event.get("event_type", "") for needle in needles):
            return event
    return None

def compact(event):
    keys = [
        "event_type", "key", "scancode", "state", "action", "button", "reason",
        "mode", "focus", "target", "x11_focus", "owner", "window", "surface",
        "surface_debug", "result", "serial",
    ]
    parts = [f"{key}={event[key]}" for key in keys if key in event]
    return " ".join(parts)

def line_ref(event):
    return f"{event.get('_path','?').split('/')[-1]}:{event.get('_line','?')}"

def diagnose(trace_events):
    if not trace_events:
        return "empty trace"
    if has_kind(trace_events, "world.key.pass", "minecraft.key.skip"):
        event = first(trace_events, "world.key.pass", "minecraft.key.skip")
        return f"Java did not forward: {compact(event)}"
    if has_kind(trace_events, "world.key.forward") and not has_kind(trace_events, "bridge.keyboardInput"):
        return "Java forwarded key, but bridge.keyboardInput was not reached"
    if has_kind(trace_events, "bridge.keyboardInput") and not has_kind(trace_events, "keyboardInput.entry"):
        return "Java bridge called keyboardInput, but JNI entry is missing"
    if has_kind(trace_events, "keyboardInput.entry") and has_kind(trace_events, "wl_keyboard.key_skip"):
        event = first(trace_events, "wl_keyboard.key_skip")
        return f"Native seat skipped key: {compact(event)}"
    if has_kind(trace_events, "keyboardInput.xwayland") and not has_kind(trace_events, "smithay-x11", "x11-record"):
        return "Native chose Xwayland path, but no downstream X11/Smithay evidence is in this trace id"
    if has_kind(trace_events, "delivered_key_press", "delivered_key_release"):
        event = first(trace_events, "delivered_key_press", "delivered_key_release")
        return f"X11 delivered key: {compact(event)}"
    if has_kind(trace_events, "wl_keyboard.key"):
        return "Wayland wl_keyboard sent the key; check app-side/X11 delivery next"
    if has_kind(trace_events, "pointerButton", "wl_pointer.button", "delivered_button"):
        return "Pointer trace; inspect route and X11 delivery lines"
    return "No obvious input forwarding conclusion"

print("Files:")
for path in sys.argv[1:]:
    print(f"  {path}")
print(f"Events: {len(events)}")
print()
print("Summary:")
for (layer, kind), count in sorted(summary.items(), key=lambda item: (-item[1], item[0])):
    print(f"  {count:6d}  {layer}:{kind}")

print()
print("Trace Timelines:")
for trace in sorted(t for t in by_trace if t):
    trace_events = by_trace[trace]
    if not any(word in (event.get("event_type", "") + event.get("layer", "")) for event in trace_events for word in ("key", "keyboard", "mouse", "pointer", "x11", "wm")):
        continue
    print(f"trace={trace} -> {diagnose(trace_events)}")
    for event in trace_events:
        print(f"  {line_ref(event):28s} {event.get('layer')}:{event.get('event_type')} {compact(event)}")

print()
print("Recent Focus/Grab/X11 Events:")
interesting = []
for event in events:
    text = f"{event.get('layer','')}:{event.get('event_type','')}"
    if any(token in text for token in ("focus", "grab", "lock", "x11", "keyboardInput", "wl_keyboard", "wl_pointer")):
        interesting.append(event)
for event in interesting[-160:]:
    print(f"  {line_ref(event):28s} {event.get('layer')}:{event.get('event_type')} {compact(event)}")
PY
