package io.github.purryc.pen;

import android.view.InputDevice;
import android.view.MotionEvent;
import java.util.Objects;

/** Feed only events delivered to your own View. No permissions, storage or ASR dependency. */
public final class PenInput {
  public interface Listener {
    void onSample(PenSample sample);
  }

  private static final int[] AXES = {
    MotionEvent.AXIS_X,
    MotionEvent.AXIS_Y,
    MotionEvent.AXIS_PRESSURE,
    MotionEvent.AXIS_TILT,
    MotionEvent.AXIS_ORIENTATION,
    MotionEvent.AXIS_DISTANCE
  };
  private final Listener listener;

  public PenInput(Listener listener) {
    this.listener = Objects.requireNonNull(listener);
  }

  public boolean handle(MotionEvent event) {
    int action = event.getActionMasked();
    boolean handled = false;
    if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
      return accept(event, event.getActionIndex());
    for (int i = 0; i < event.getPointerCount(); i++) handled = accept(event, i) || handled;
    return handled;
  }

  private boolean accept(MotionEvent e, int i) {
    int tool = e.getToolType(i);
    if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return false;
    int action = e.getActionMasked();
    boolean hover =
        action == MotionEvent.ACTION_HOVER_ENTER
            || action == MotionEvent.ACTION_HOVER_MOVE
            || action == MotionEvent.ACTION_HOVER_EXIT;
    boolean measurement =
        action == MotionEvent.ACTION_MOVE
            || action == MotionEvent.ACTION_HOVER_MOVE
            || action == MotionEvent.ACTION_DOWN
            || action == MotionEvent.ACTION_POINTER_DOWN;
    InputDevice device = e.getDevice();
    boolean[] declared = new boolean[AXES.length];
    for (int k = 0; k < AXES.length; k++)
      declared[k] = device != null && device.getMotionRange(AXES[k], e.getSource()) != null;
    for (int h = 0; h < e.getHistorySize(); h++)
      emit(e, i, h, hover, measurement, device, declared);
    emit(e, i, -1, hover, measurement, device, declared);
    return true;
  }

  private void emit(
      MotionEvent e,
      int i,
      int history,
      boolean hover,
      boolean measurement,
      InputDevice device,
      boolean[] declared) {
    float[] values = new float[AXES.length];
    for (int k = 0; k < AXES.length; k++)
      values[k] =
          history < 0 ? e.getAxisValue(AXES[k], i) : e.getHistoricalAxisValue(AXES[k], i, history);
    listener.onSample(
        new PenSample(
            history < 0 ? e.getEventTime() : e.getHistoricalEventTime(history),
            e.getDeviceId(),
            e.getSource(),
            e.getToolType(i),
            e.getPointerId(i),
            e.getButtonState(),
            e.getActionMasked(),
            history,
            hover,
            measurement,
            device == null ? "Unknown pen" : device.getName(),
            values,
            declared));
  }
}
