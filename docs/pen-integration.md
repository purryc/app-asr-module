# 独立笔输入模块

`pen-android.aar` 仅提供 `PenInput` 与不可变的 `PenSample`。不包含语音、模型、麦克风权限、界面、文件日志或全局输入监听。未来应用可以完全不引入 ASR。

## 接入

```kotlin
dependencies {
    implementation(files("libs/pen-android.aar"))
}
```

在你自己的 View 里把事件交给适配器：

```java
private final PenInput pen = new PenInput(sample -> {
    float x = sample.value(PenSample.Axis.X);
    float y = sample.value(PenSample.Axis.Y);
    boolean hasTilt = sample.isDeclared(PenSample.Axis.TILT);
    // 在自己的画布/选区模型中处理；不要直接假定所有设备都上报全部轴。
});
@Override public boolean onHoverEvent(MotionEvent event) {
    return pen.handle(event) || super.onHoverEvent(event);
}
@Override public boolean onTouchEvent(MotionEvent event) {
    return pen.handle(event) || super.onTouchEvent(event);
}
```

导入 `io.github.purryc.pen.*`。回调在调用 handle 的线程同步发生，通常是 View 主线程。不要在回调里做阻塞 IO；如果异步存储，PenSample 已复制原始值，不引用会被系统回收的 MotionEvent。

## 样本字段

| 字段 | 含义 |
|---|---|
| X / Y | View 局部坐标，像素 |
| PRESSURE | Android 返回的压力值，保留原始范围 |
| TILT / ORIENTATION | Android 返回的弧度；显示角度时自行转换 |
| DISTANCE | 原始驱动单位，不能默认当作毫米 |
| `isDeclared(axis)` | 该设备/来源是否声明该轴；不代表实测变化或有效精度 |
| `hover` | 悬浮事件；触屏样本为 false |
| `measurement` | 适合统计的事件；排除 up/exit/cancel 等边界 |
| `historyIndex` | 历史样本下标，当前样本为 -1 |
| deviceId/source/toolType/pointerId/buttons/action | 路由、工具和按钮信息 |
| eventTimeMillis | Android 事件时间，单调时钟域，不能当作 UTC |

只接收 stylus/eraser，忽略手指和鼠标。保留 MotionEvent 历史点和 pointerId；宿主需要支持多笔/多指时按 ID 管理独立路径。`PenDiagnosticsView` 是可选示例视图，只示范单条笔迹，不是完整绘图引擎。

## 联想平板上已知的硬件边界

早期 Lenovo Pen Lab 对 Y700/TB321FU 的实体笔观察到悬浮 X/Y、倾斜和接触压力变化。距离只出现悬浮 1、接触 0，未证明连续高度；方向轴虽然声明，实测未证明变化。这是该设备的历史物理观察，不应推广到所有平板。

本次模块化后的自动测试仅使用合成 MotionEvent 验证手指过滤、历史顺序、坐标和未声明轴；没有用合成事件证明真实悬浮能力。

## 与 ASR 组合

由宿主业务层保存选区/笔动作，再决定何时调用 ASR。笔模块不得在内部启动麦克风，ASR 模块也不读取笔位置。宿主可以把“选区 ID”和“ASR 会话 ID”关联起来，但这属于应用交互逻辑，不属于这两个基础模块。

## 其他平台

笔模块当前只实现 Android。iPad/Windows/鸿蒙需要分别把系统笔事件映射到等价的数据结构，并逐设备确认 hover、tilt、pressure、distance。ASR 的跨平台适配不自动带来跨平台笔支持。
