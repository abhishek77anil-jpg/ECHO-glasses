// Live awareness — the ambient channel. Scripted for the demo, but shaped
// exactly like the real thing will be: a session you start and stop, that
// pushes events at you as they happen.
//
// HARDWARE SEAM: replace the scripted timer with the glasses' continuous
// scene stream. Keep createLiveSession()'s start/stop contract and the event
// shape { label, kind, text, time } and the UI needs no changes.
//
// kind drives both the colour of the left rule and the haptic:
//   "arrive"  someone entered      green
//   "leave"   someone left         amber
//   "attn"    scene / speaker      blue

const PEOPLE = ["Aarav", "Meera", "Rohan", "Sana", "Vikram"];

export const LIVE_SCRIPT = [
  {
    label: "Room scan",
    kind: "attn",
    text: `In this room: ${PEOPLE.slice(0, 3).join(", ")}. ${PEOPLE[0]} is closest, on your left.`,
  },
  { label: "Speaker", kind: "attn", text: `${PEOPLE[1]} is speaking.` },
  { label: "Arrived", kind: "arrive", text: `${PEOPLE[3]} just arrived, near the door.` },
  { label: "Speaker", kind: "attn", text: `${PEOPLE[3]} is speaking now.` },
  {
    label: "Waiting on you",
    kind: "attn",
    text: `${PEOPLE[0]} is facing you. They seem to be waiting for you to respond.`,
  },
  { label: "Left", kind: "leave", text: `${PEOPLE[2]} left the room.` },
];

export function createLiveSession({
  onEvent,
  firstDelayMs = 1200,
  minGapMs = 6000,
  jitterMs = 3000,
} = {}) {
  let timer = null;
  let idx = 0;
  let running = false;

  const schedule = (ms) => {
    if (!running) return;
    timer = setTimeout(tick, ms);
  };

  const tick = () => {
    if (!running) return;
    const ev = LIVE_SCRIPT[idx % LIVE_SCRIPT.length];
    idx += 1;
    onEvent({ ...ev, time: Date.now() });
    schedule(minGapMs + Math.random() * jitterMs);
  };

  return {
    start() {
      if (running) return;
      running = true;
      schedule(firstDelayMs);
    },
    stop() {
      running = false;
      if (timer) clearTimeout(timer);
      timer = null;
    },
    get running() {
      return running;
    },
  };
}
