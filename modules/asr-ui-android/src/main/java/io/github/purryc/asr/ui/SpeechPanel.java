package io.github.purryc.asr.ui;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import io.github.purryc.asr.*;
import io.github.purryc.asr.android.AndroidAsrEngine;

public final class SpeechPanel {
  public static final int MIC_REQUEST = 410;
  final Activity activity;
  final SpeechSession session = new SpeechSession();
  final SpeechDrafts drafts = new SpeechDrafts();
  final AndroidAsrEngine offline;
  AlertDialog dialog;
  TextView status, partial;
  EditText transcript;
  Button toggle;
  ProgressBar meter;
  boolean recognizedText;
  String savedText = "";
  boolean resumed, pendingStart;
  int token;

  public SpeechPanel(Activity activity) {
    this.activity = activity;
    offline = new AndroidAsrEngine(activity);
  }

  public void show() {
    if (dialog != null && dialog.isShowing()) return;
    LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setFocusableInTouchMode(true);
    int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
    content.setPadding(pad, pad / 2, pad, pad / 2);
    status = new TextView(activity);
    status.setTextSize(14);
    status.setText("中英文 · 实时转写");
    content.addView(status);
    meter = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
    meter.setMax(100);
    content.addView(meter);
    partial = new TextView(activity);
    partial.setTextSize(18);
    partial.setTextColor(0xff6850e5);
    partial.setMinLines(2);
    content.addView(partial);
    transcript = new EditText(activity);
    transcript.setTextSize(18);
    transcript.setMinLines(3);
    transcript.setMaxLines(6);
    transcript.setHint("转写文字");
    transcript.setText(savedText);
    transcript.setGravity(android.view.Gravity.TOP);
    transcript.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    content.addView(transcript, new LinearLayout.LayoutParams(-1, -2));
    toggle = new Button(activity);
    toggle.setText("开始转写");
    toggle.setOnClickListener(
        v -> {
          if (session.state() == SpeechSession.State.LISTENING) {
            session.processing(token);
            status.setText("正在整理文字…");
            toggle.setText("取消等待");
            offline.stop();
          } else if (session.state() == SpeechSession.State.PROCESSING) {
            cancel();
            status.setText("已停止，文字已保留");
          } else requestStart();
        });
    content.addView(toggle);
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(content);
    dialog =
        new AlertDialog.Builder(activity)
            .setTitle("语音转写")
            .setView(scroll)
            .setPositiveButton("完成", null)
            .create();
    dialog.setOnDismissListener(
        d -> {
          savedText = transcript.getText().toString();
          pendingStart = false;
          cancel();
        });
    dialog.show();
  }

  void requestStart() {
    if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      pendingStart = true;
      activity.requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
      return;
    }
    start();
  }

  public void permissionResult(int[] result) {
    if (dialog == null || !dialog.isShowing()) {
      pendingStart = false;
      return;
    }
    if (result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED) {
      if (resumed && pendingStart) {
        pendingStart = false;
        start();
      }
    } else {
      pendingStart = false;
      status.setText("请允许麦克风权限后重试");
      new AlertDialog.Builder(activity)
          .setMessage("需要麦克风权限才能转写")
          .setPositiveButton(
              "应用设置",
              (d, w) ->
                  activity.startActivity(
                      new Intent(
                          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          Uri.parse("package:" + activity.getPackageName()))))
          .setNegativeButton("取消", null)
          .show();
    }
  }

  void start() {
    if (!resumed || dialog == null || !dialog.isShowing()) return;
    cancel();
    recognizedText = false;
    token = session.begin();
    final int current = token;
    status.setText("正在准备离线模型…");
    toggle.setText("停止转写");
    android.util.Log.i("AppAsr", "offline start token=" + current);
    offline.start(
        new AsrListener() {
          public void onReady() {
            if (session.accepts(current)) {
              status.setText("正在聆听 · 中英文");
              android.util.Log.i("AppAsr", "offline ready token=" + current);
            }
          }

          public void onLevel(float rms) {
            if (session.accepts(current)) meter.setProgress(Math.min(100, Math.round(rms * 500)));
          }

          public void onPartial(int id, String text) {
            if (session.accepts(current)) {
              drafts.update(id, text);
              partial.setText(drafts.text());
            }
          }

          public void onFinal(int id, String text) {
            if (!session.accepts(current)) return;
            drafts.finish(id);
            partial.setText(drafts.text());
            if (text.isEmpty()) return;
            recognizedText = true;
            transcript.append((transcript.length() == 0 ? "" : "\n") + text);
            if (!transcript.hasFocus()) transcript.setSelection(transcript.length());
            savedText = transcript.getText().toString();
            android.util.Log.i("AppAsr", "offline segment chars=" + text.length());
          }

          public void onDone() {
            if (session.finish(current)) {
              toggle.setText("开始转写");
              partial.setText("");
              meter.setProgress(0);
              status.setText(recognizedText ? "已完成 · 本机离线识别" : "未识别到语音，请再试一次");
              android.util.Log.i("AppAsr", "offline done");
            }
          }

          public void onError(String message) {
            if (session.finish(current)) {
              toggle.setText("开始转写");
              partial.setText("");
              status.setText(message);
            }
          }
        });
  }

  void cancel() {
    drafts.clear();
    session.cancel();
    offline.cancel();
    if (toggle != null) toggle.setText("开始转写");
    if (partial != null) partial.setText("");
    if (meter != null) meter.setProgress(0);
  }

  public void onPause() {
    resumed = false;
    boolean wasActive = session.state() != SpeechSession.State.IDLE;
    cancel();
    if (wasActive) status.setText("转写已停止，文字已保留");
  }

  public void onResume() {
    resumed = true;
    if (pendingStart
        && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
      pendingStart = false;
      start();
    }
  }

  public void destroy() {
    pendingStart = false;
    cancel();
    if (dialog != null) dialog.dismiss();
    offline.close();
  }
}
