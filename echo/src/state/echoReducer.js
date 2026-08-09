// Capture state machine.
//
//   idle ──CAPTURE_START──> analyzing ──CAPTURE_SUCCESS──> result
//     ^                        │  │
//     │                        │  └────CAPTURE_FAILURE───> error
//     └───CAPTURE_CANCEL───────┘
//
// `result` and `error` are terminal until the next CAPTURE_START — a result
// stays on screen so the user can come back to it and replay it.
//
// Every transition out of `analyzing` carries the `gen` of the run that
// produced it. A run whose gen no longer matches was cancelled or superseded,
// so its late result is dropped instead of overwriting a newer one — the bug
// you only see on a slow link, when an abandoned capture lands after the user
// has already moved on.

export const initialEchoState = {
  status: "idle", // idle | analyzing | result | error
  result: null, // { expression, pct }
  error: null, // spoken error string
  gen: 0,
};

export function echoReducer(state, action) {
  switch (action.type) {
    case "CAPTURE_START":
      return { ...state, status: "analyzing", error: null, gen: action.gen };

    case "CAPTURE_SUCCESS":
      if (action.gen !== state.gen) return state;
      return { ...state, status: "result", result: action.result, error: null };

    case "CAPTURE_FAILURE":
      if (action.gen !== state.gen) return state;
      return { ...state, status: "error", error: action.error };

    case "CAPTURE_CANCEL":
      return { ...state, status: "idle", error: null, gen: action.gen };

    default:
      return state;
  }
}

// What the header pill shows. `error` reads READY because the app *is* ready
// to try again — the failure itself is spoken and shown on Home.
export const STATUS_BADGE = {
  idle: "READY",
  analyzing: "ANALYZING",
  result: "RESULT",
  error: "READY",
};
