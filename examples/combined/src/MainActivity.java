package io.github.purryc.demo.combined;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import io.github.purryc.asr.ui.SpeechPanel;
import io.github.purryc.examples.PenDiagnosticsView;

public final class MainActivity extends Activity {
  SpeechPanel speech;
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
    speech = new SpeechPanel(this);
    button(bar, "语音转写", () -> speech.show());
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

  @Override
  protected void onResume() {
    super.onResume();
    speech.onResume();
  }

  @Override
  protected void onPause() {
    speech.onPause();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    speech.destroy();
    super.onDestroy();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == SpeechPanel.MIC_REQUEST) speech.permissionResult(results);
  }
}
