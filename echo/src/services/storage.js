import AsyncStorage from "@react-native-async-storage/async-storage";

// Tiny JSON key-value wrapper. Persistence is a convenience, never a
// requirement: if the native module is unavailable (bare Node test runner,
// a stripped build, a device with a full disk) we degrade to an in-process
// map so the app keeps working for the session instead of crashing.
const mem = new Map();

export async function loadJSON(key, fallback = null) {
  try {
    const raw = await AsyncStorage.getItem(key);
    if (raw == null) return mem.has(key) ? mem.get(key) : fallback;
    return JSON.parse(raw);
  } catch (e) {
    return mem.has(key) ? mem.get(key) : fallback;
  }
}

export async function saveJSON(key, value) {
  mem.set(key, value);
  try {
    await AsyncStorage.setItem(key, JSON.stringify(value));
  } catch (e) {
    // in-memory copy above is the fallback
  }
}

export async function removeKey(key) {
  mem.delete(key);
  try {
    await AsyncStorage.removeItem(key);
  } catch (e) {
    // nothing to do
  }
}
