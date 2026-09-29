package io.github.purryc.demo.pen;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import io.github.purryc.examples.PenDiagnosticsView;

public final class MainActivity extends Activity {
  PenDiagnosticsView pen;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xffffffff);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    pen = new PenDiagnosticsView(this);
    root.addView(pen, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout bar = new LinearLayout(this);
    bar.setBackgroundColor(0xff18181c);
    button(bar, "清空笔迹", () -> pen.clearInk());
    button(
        bar,
        "设备能力",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("输入能力")
                .setMessage(pen.capabilities())
                .setPositiveButton("关闭", null)
                .show());
    root.addView(bar);
    setContentView(root);
  }

  void button(LinearLayout bar, String title, Runnable action) {
    Button b = new Button(this);
    b.setText(title);
    b.setTextColor(0xffffffff);
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff18181c));
    b.setOnClickListener(v -> action.run());
    bar.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
  }
}
