package io.github.purryc.pen;

/** Immutable raw Android pen sample; angles are radians, positions are local pixels. */
public final class PenSample {
  public enum Axis {
    X,
    Y,
    PRESSURE,
    TILT,
    ORIENTATION,
    DISTANCE
  }

  public final long eventTimeMillis;
  public final int deviceId, source, toolType, pointerId, buttons, action, historyIndex;
  public final boolean hover, measurement;
  public final String deviceName;
  private final float[] values;
  private final boolean[] declared;

  PenSample(
      long time,
      int deviceId,
      int source,
      int toolType,
      int pointerId,
      int buttons,
      int action,
      int historyIndex,
      boolean hover,
      boolean measurement,
      String deviceName,
      float[] values,
      boolean[] declared) {
    this.eventTimeMillis = time;
    this.deviceId = deviceId;
    this.source = source;
    this.toolType = toolType;
    this.pointerId = pointerId;
    this.buttons = buttons;
    this.action = action;
    this.historyIndex = historyIndex;
    this.hover = hover;
    this.measurement = measurement;
    this.deviceName = deviceName;
    this.values = values.clone();
    this.declared = declared.clone();
  }

  public float value(Axis axis) {
    return values[axis.ordinal()];
  }

  /** A declared range does not prove changing values or usable precision on hardware. */
  public boolean isDeclared(Axis axis) {
    return declared[axis.ordinal()];
  }
}
