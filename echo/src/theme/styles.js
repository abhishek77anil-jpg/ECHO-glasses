import { StyleSheet } from "react-native";
import C from "./colors";

// Every touch target here is >= 56dp on its short edge. That is the single
// most important visual rule in this app — do not shrink them.
export const st = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.bg },

  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 24,
    paddingTop: 8,
  },
  logo: { color: C.text, fontSize: 22, fontWeight: "800", letterSpacing: 4 },
  badge: {
    borderWidth: 1,
    borderColor: C.border,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 4,
  },
  badgeTxt: { color: C.text2, fontSize: 12, fontWeight: "600", letterSpacing: 1 },

  center: { flex: 1, alignItems: "center", justifyContent: "center", gap: 28, padding: 24 },
  capture: {
    width: 280,
    height: 280,
    borderRadius: 140,
    backgroundColor: C.surface,
    borderWidth: 3,
    borderColor: "#2A2A2A",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
  },
  captureTxt: { color: C.text, fontSize: 26, fontWeight: "800", letterSpacing: 4 },
  captureSub: { color: C.text2, fontSize: 14 },
  status: { color: C.text2, fontSize: 16, textAlign: "center", maxWidth: 300, lineHeight: 24 },
  errText: { color: C.danger, fontWeight: "700" },

  pad: { paddingHorizontal: 24, paddingTop: 12 },
  card: {
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.border,
    borderRadius: 20,
    padding: 24,
    marginBottom: 16,
  },
  kicker: { color: C.text2, fontSize: 12, letterSpacing: 2, fontWeight: "600" },
  big: { color: C.text, fontSize: 34, fontWeight: "800", marginTop: 4 },
  confbar: {
    height: 10,
    borderRadius: 999,
    backgroundColor: C.surface2,
    borderWidth: 1,
    borderColor: C.border,
    marginTop: 8,
    overflow: "hidden",
  },
  conffill: { height: "100%", backgroundColor: C.text, borderRadius: 999 },

  bigBtn: {
    minHeight: 64,
    borderRadius: 16,
    backgroundColor: C.surface,
    borderWidth: 1.5,
    borderColor: C.border,
    alignItems: "center",
    justifyContent: "center",
    padding: 14,
    marginBottom: 14,
  },
  bigBtnPrimary: { backgroundColor: C.text, borderColor: C.text },
  bigBtnDanger: { borderColor: C.danger },
  bigBtnTxt: { color: C.text, fontSize: 19, fontWeight: "700" },
  bigBtnTxtDanger: { color: C.danger },

  listTitle: { color: C.text2, fontSize: 13, letterSpacing: 2, fontWeight: "600", marginBottom: 14 },
  item: {
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.border,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
  },
  itemT: { color: C.text, fontSize: 17, fontWeight: "700" },
  itemD: { color: C.text2, fontSize: 14, marginTop: 2 },
  empty: { color: C.text2, textAlign: "center", marginTop: 48, fontSize: 16 },
  note: { color: C.focus, fontSize: 14, lineHeight: 20, marginBottom: 16 },

  setting: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.border,
    borderRadius: 16,
    padding: 14,
    marginBottom: 12,
  },
  stepBtn: {
    width: 56,
    height: 56,
    borderRadius: 14,
    backgroundColor: C.surface2,
    borderWidth: 1.5,
    borderColor: C.border,
    alignItems: "center",
    justifyContent: "center",
  },
  stepTxt: { color: C.text, fontSize: 26, fontWeight: "700" },

  nav: {
    flexDirection: "row",
    borderTopWidth: 1,
    borderTopColor: C.border,
    paddingHorizontal: 8,
    paddingVertical: 8,
    gap: 4,
  },
  navBtn: {
    flex: 1,
    minHeight: 56,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderColor: "transparent",
  },
  navBtnActive: { backgroundColor: C.surface, borderColor: C.border },
  navTxt: { color: C.text2, fontSize: 13, fontWeight: "700" },
});

export default st;
