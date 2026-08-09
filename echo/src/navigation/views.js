// The nav ring. Swiping left/right walks this array; the tab bar renders it.
// "result" is deliberately NOT a view — a result is a state of Home, so there
// is never a screen the user can land on that the swipe ring cannot reach.
export const VIEWS = ["home", "live", "history", "settings", "help"];

export const VIEW_LABEL = {
  home: "Home. Double tap to analyze.",
  live: "Live awareness. Double tap to start or stop.",
  history: "History.",
  settings: "Audio and haptics settings.",
  help: "Help. Long press anywhere also opens help.",
};

export const VIEW_TAB = {
  home: "Home",
  live: "Live",
  history: "History",
  settings: "Audio",
  help: "Help",
};

export const HELP_SPEECH =
  "Double tap the center of the screen to analyze. " +
  "Swipe left or right to move between sections. " +
  "Single tap tells you where you are. " +
  "Long press opens help. " +
  "Two finger double tap repeats the last result.";

export const GREETING =
  "ECHO ready. Double tap the center of the screen to analyze. Long press for help.";

export function nextView(view) {
  const i = VIEWS.indexOf(view);
  return VIEWS[(i + 1) % VIEWS.length];
}

export function prevView(view) {
  const i = VIEWS.indexOf(view);
  return VIEWS[(i - 1 + VIEWS.length) % VIEWS.length];
}
