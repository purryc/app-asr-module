package io.github.purryc.demo.asr;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import io.github.purryc.asr.ui.SpeechPanel;

public final class MainActivity extends Activity {
  SpeechPanel speech;

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
    TextView title = new TextView(this);
    title.setText("语音转写");
    title.setTextSize(28);
    title.setGravity(android.view.Gravity.CENTER);
    root.addView(title, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout bar = new LinearLayout(this);
    bar.setBackgroundColor(0xff18181c);
    speech = new SpeechPanel(this);
    button(bar, "语音转写", () -> speech.show());
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
