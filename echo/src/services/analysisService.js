// Mock AI analysis.
//
// THIS FILE IS THE HARDWARE SEAM. To go from demo to real ECHO glasses,
// replace the body of analyzePerson() with:
//
//   glasses camera (BLE control -> Wi-Fi Direct transfer) -> JPEG frame
//     -> expression model (relay API or on-device) -> AnalysisResult
//
// Keep the contract below and nothing else in the app has to change:
//   resolves { ok: true,  expression: string, confidence: 0..1 }
//   resolves { ok: false, error: "NO_PERSON" | "ANALYSIS_FAILED" }
//   rejects  CancelledError            when the caller aborts
//
// Honour `signal` in the real implementation too — the UI lets the user
// cancel an in-flight capture by double-tapping again, and a capture that
// ignores cancellation will hold the glasses session lease open.

export class CancelledError extends Error {
  constructor() {
    super("Analysis cancelled");
    this.name = "CancelledError";
  }
}

// Demo tuning. These are the odds the mock returns each outcome — the real
// pipeline has no equivalent, so they live here and nowhere else.
export const MOCK = {
  minLatencyMs: 1800,
  jitterMs: 800,
  successRate: 0.72,
  noPersonRate: 0.16, // remainder is ANALYSIS_FAILED
};

const SUCCESS = [
  { ok: true, expression: "Smiling", confidence: 0.87 },
  { ok: true, expression: "Neutral", confidence: 0.74 },
  { ok: true, expression: "Surprised", confidence: 0.81 },
  { ok: true, expression: "Focused", confidence: 0.69 },
];

function rollOutcome() {
  const r = Math.random();
  if (r < MOCK.successRate) return SUCCESS[Math.floor(Math.random() * SUCCESS.length)];
  if (r < MOCK.successRate + MOCK.noPersonRate) return { ok: false, error: "NO_PERSON" };
  return { ok: false, error: "ANALYSIS_FAILED" };
}

export function analyzePerson({ signal } = {}) {
  return new Promise((resolve, reject) => {
    if (signal && signal.aborted) {
      reject(new CancelledError());
      return;
    }

    const timer = setTimeout(() => {
      detach();
      resolve(rollOutcome());
    }, MOCK.minLatencyMs + Math.random() * MOCK.jitterMs);

    const onAbort = () => {
      clearTimeout(timer);
      detach();
      reject(new CancelledError());
    };

    function detach() {
      if (signal && signal.removeEventListener) signal.removeEventListener("abort", onAbort);
    }

    if (signal && signal.addEventListener) signal.addEventListener("abort", onAbort);
  });
}

// Spoken form of a result. Kept next to the result shape so the wording and
// the data can never drift apart.
export function speakResult(expression, pct) {
  return `The person appears to be ${expression.toLowerCase()}. Confidence ${pct} percent.`;
}

export function speakFailure(error) {
  return error === "NO_PERSON"
    ? "No person detected in front of you."
    : "I couldn't identify an expression. Please try again.";
}
