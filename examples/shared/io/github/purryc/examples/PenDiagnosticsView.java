package io.github.purryc.examples;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import io.github.purryc.pen.*;
import java.util.*;

/** Optional demo view; production hosts normally consume PenInput with their own UI. */
public final class PenDiagnosticsView extends View {
  final Paint paint = new Paint(3);
  final Path ink = new Path();
  final float[][] min = new float[2][6], max = new float[2][6];
  final int[] counts = new int[2];
  final float[] values = new float[6];
  final boolean[] declared = new boolean[6];
  final String[] names = {"X", "Y", "压力", "倾斜", "方向", "距离"};
  final float density = getResources().getDisplayMetrics().density;
  final PenInput input = new PenInput(this::accept);
  float x, y;
  boolean visible, contact, drawing;
  int deviceId = -1, source, buttons, samples;
  String deviceName = "等待手写笔", action = "尚未收到笔事件", logState = "";

  public PenDiagnosticsView(Context context) {
    super(context);
    resetStats();
    setFocusable(true);
  }

  public void clearInk() {
    ink.rewind();
    invalidate();
  }

  void resetStats() {
    for (int m = 0; m < 2; m++) {
      Arrays.fill(min[m], Float.POSITIVE_INFINITY);
      Arrays.fill(max[m], Float.NEGATIVE_INFINITY);
      counts[m] = 0;
    }
  }

  public String capabilities() {
    StringBuilder out = new StringBuilder();
    for (int id : InputDevice.getDeviceIds()) {
      InputDevice d = InputDevice.getDevice(id);
      if (d == null) continue;
      out.append(d.getName()).append("\n");
      for (InputDevice.MotionRange r : d.getMotionRanges())
        out.append(MotionEvent.axisToString(r.getAxis()))
            .append(" [")
            .append(r.getMin())
            .append(", ")
            .append(r.getMax())
            .append("]\n");
    }
    return out.toString();
  }

  @Override
  public boolean onHoverEvent(MotionEvent e) {
    return input.handle(e) || super.onHoverEvent(e);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    return input.handle(e) || super.onTouchEvent(e);
  }

  void accept(PenSample s) {
    if (deviceId != s.deviceId || source != s.source) {
      deviceId = s.deviceId;
      source = s.source;
      resetStats();
      drawing = false;
    }
    samples++;
    deviceName = s.deviceName;
    buttons = s.buttons;
    action = MotionEvent.actionToString(s.action);
    int mode = s.hover ? 0 : 1;
    for (PenSample.Axis axis : PenSample.Axis.values()) {
      int k = axis.ordinal();
      values[k] = s.value(axis);
      declared[k] = s.isDeclared(axis);
      if (s.measurement) {
        min[mode][k] = Math.min(min[mode][k], values[k]);
        max[mode][k] = Math.max(max[mode][k], values[k]);
      }
    }
    if (s.measurement) counts[mode]++;
    x = values[0];
    y = values[1];
    visible =
        s.action != MotionEvent.ACTION_UP
            && s.action != MotionEvent.ACTION_POINTER_UP
            && s.action != MotionEvent.ACTION_CANCEL
            && s.action != MotionEvent.ACTION_HOVER_EXIT;
    contact = visible && !s.hover;
    if (s.action == MotionEvent.ACTION_DOWN || s.action == MotionEvent.ACTION_POINTER_DOWN)
      drawing = false;
    if (!s.hover && s.measurement) {
      if (!drawing) {
        ink.moveTo(x, y);
        drawing = true;
      } else ink.lineTo(x, y);
    }
    if (!visible || s.hover) drawing = false;
    postInvalidateOnAnimation();
  }

  void text(Canvas c, String s, float x, float y, float size, int color) {
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(color);
    paint.setTextSize(size * density);
    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    c.drawText(s, x, y, paint);
  }

  String f(float v) {
    return String.format(Locale.US, "%.3f", v);
  }

  String range(int mode, int k) {
    return counts[mode] == 0 ? "—" : f(min[mode][k]) + " … " + f(max[mode][k]);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float w = getWidth(), height = getHeight(), d = density;
    c.drawColor(Color.WHITE);
    int dark = Color.rgb(31, 34, 43),
        muted = Color.rgb(110, 115, 130),
        accent = Color.rgb(104, 80, 229);
    boolean wide = w / d > 740;
    float panel = wide ? Math.min(400 * d, w * .42f) : w;
    float left = wide ? w - panel : 0, top = wide ? 0 : height * .47f;
    float paperRight = wide ? left : w, paperBottom = wide ? height : top;
    paint.setColor(Color.rgb(234, 236, 244));
    paint.setStrokeWidth(d);
    for (float gx = 28 * d; gx < paperRight; gx += 28 * d)
      for (float gy = 105 * d; gy < paperBottom; gy += 28 * d) c.drawCircle(gx, gy, d, paint);
    c.save();
    c.clipRect(0, 86 * d, paperRight, paperBottom);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(2.4f * d);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setColor(dark);
    c.drawPath(ink, paint);
    c.restore();
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(Color.rgb(247, 248, 252));
    c.drawRect(left, top, w, height, paint);
    text(c, "笔数据实验台", 24 * d, 36 * d, 22, dark);
    text(
        c,
        android.os.Build.MODEL + "  /  " + (visible ? (contact ? "接触" : "悬浮") : "待机"),
        24 * d,
        64 * d,
        12,
        muted);
    float px = left + 24 * d, py = top + 36 * d;
    text(c, "实时数据", px, py, 18, dark);
    py += 26 * d;
    String shortName = deviceName.length() > 32 ? deviceName.substring(0, 32) : deviceName;
    text(c, shortName, px, py, 11, muted);
    py += 32 * d;
    for (int k = 0; k < 6; k++) {
      String v =
          samples == 0
              ? "—"
              : (k == 3 || k == 4 ? f((float) Math.toDegrees(values[k])) + "°" : f(values[k]));
      String flag = samples == 0 ? "" : declared[k] ? "" : " · 轴未声明";
      text(c, names[k] + (k == 5 ? "（原始值）" : "") + "   " + v + flag, px, py, 14, dark);
      py += 20 * d;
      text(c, "悬浮 " + range(0, k) + "   接触 " + range(1, k), px, py, 10, muted);
      py += 30 * d;
    }
    text(c, "悬浮样本 " + counts[0] + "   接触样本 " + counts[1], px, py, 12, accent);
    py += 23 * d;
    text(c, "按键 " + buttons + "  ·  " + logState, px, py, 11, muted);
    py += 22 * d;
    text(c, action, px, py, 10, muted);
    if (visible) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(1.6f * d);
      paint.setColor(accent);
      float radius = contact ? 5 * d : 16 * d;
      c.drawCircle(x, y, radius, paint);
      c.drawLine(x - 6 * d, y, x + 6 * d, y, paint);
      c.drawLine(x, y - 6 * d, x, y + 6 * d, paint);
      if (declared[3] && declared[4]) {
        float length = (float) Math.sin(values[3]) * 44 * d;
        c.drawLine(
            x,
            y,
            x + (float) Math.sin(values[4]) * length,
            y - (float) Math.cos(values[4]) * length,
            paint);
      }
      paint.setStyle(Paint.Style.FILL);
    }
  }
}
