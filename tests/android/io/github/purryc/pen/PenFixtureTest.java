package io.github.purryc.pen;

import android.app.Instrumentation;
import android.os.Bundle;
import android.view.*;
import java.util.*;

/** Synthetic events verify filtering/history only; they do not prove hardware capabilities. */
public final class PenFixtureTest extends Instrumentation {
  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle out = new Bundle();
    try {
      List<PenSample> received = new ArrayList<>();
      PenInput pen = new PenInput(received::add);
      MotionEvent finger = MotionEvent.obtain(1, 2, MotionEvent.ACTION_MOVE, 1, 2, 0);
      if (pen.handle(finger) || !received.isEmpty()) throw new AssertionError("Finger accepted");
      finger.recycle();
      MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
      p.id = 0;
      p.toolType = MotionEvent.TOOL_TYPE_STYLUS;
      MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
      c.x = 30;
      c.y = 40;
      c.pressure = .5f;
      c.setAxisValue(MotionEvent.AXIS_TILT, .3f);
      MotionEvent e =
          MotionEvent.obtain(
              1,
              2,
              MotionEvent.ACTION_MOVE,
              1,
              new MotionEvent.PointerProperties[] {p},
              new MotionEvent.PointerCoords[] {c},
              0,
              0,
              1,
              1,
              -9999,
              0,
              InputDevice.SOURCE_STYLUS,
              0);
      c.x = 50;
      e.addBatch(3, new MotionEvent.PointerCoords[] {c}, 0);
      if (!pen.handle(e) || received.size() != 2) throw new AssertionError("History missing");
      if (received.get(0).value(PenSample.Axis.X) != 30
          || received.get(1).value(PenSample.Axis.X) != 50)
        throw new AssertionError("Wrong coordinates");
      if (received.get(1).isDeclared(PenSample.Axis.DISTANCE))
        throw new AssertionError("Invented unsupported axis");
      e.recycle();
      out.putString(
          "result", "finger filtering, history ordering, coordinates and undeclared axis passed");
      finish(-1, out);
    } catch (Throwable t) {
      out.putString("failure", t.toString());
      finish(0, out);
    }
  }
}
