// Timestamps travel through the app as epoch milliseconds so they survive
// JSON persistence; they only become Dates at the moment they are rendered.
export function formatTime(ms) {
  return new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}
