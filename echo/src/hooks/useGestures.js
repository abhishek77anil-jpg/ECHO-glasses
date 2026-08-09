import { useMemo } from "react";
import { Directions, Gesture } from "react-native-gesture-handler";

/*
  The whole screen is one gesture surface. Five gestures, composed so they
  cannot fight each other:

    Gesture.Exclusive(doubleTap, singleTap)  double-tap wins; a single tap
                                             only resolves once the
                                             double-tap window has passed
    Gesture.Race(everything else)            first to recognise takes it

  runOnJS(true) keeps every callback on the JS thread, so no Reanimated
  worklets are involved and the app stays Expo Go compatible with zero babel
  configuration.

  Handlers are read through a ref so the gestures are built once and never
  capture a stale closure — rebuilding GestureDetector's gesture on every
  render drops in-flight touches.
*/
export default function useGestures(handlersRef) {
  return useMemo(() => {
    const doubleTap = Gesture.Tap()
      .numberOfTaps(2)
      .maxDuration(300)
      .runOnJS(true)
      .onEnd((_e, ok) => {
        if (ok) handlersRef.current.onPrimary();
      });

    const singleTap = Gesture.Tap()
      .numberOfTaps(1)
      .maxDuration(300)
      .runOnJS(true)
      .onEnd((_e, ok) => {
        if (ok) handlersRef.current.onWhereAmI();
      });

    const twoFingerDouble = Gesture.Tap()
      .numberOfTaps(2)
      .minPointers(2)
      .maxDuration(350)
      .runOnJS(true)
      .onEnd((_e, ok) => {
        if (ok) handlersRef.current.onRepeat();
      });

    const longPress = Gesture.LongPress()
      .minDuration(600)
      .runOnJS(true)
      .onStart(() => handlersRef.current.onHelp());

    const flingLeft = Gesture.Fling()
      .direction(Directions.LEFT)
      .runOnJS(true)
      .onEnd(() => handlersRef.current.onSwipe("next"));

    const flingRight = Gesture.Fling()
      .direction(Directions.RIGHT)
      .runOnJS(true)
      .onEnd(() => handlersRef.current.onSwipe("prev"));

    return Gesture.Race(
      twoFingerDouble,
      longPress,
      flingLeft,
      flingRight,
      Gesture.Exclusive(doubleTap, singleTap)
    );
  }, [handlersRef]);
}
