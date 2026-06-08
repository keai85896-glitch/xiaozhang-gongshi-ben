# 小账工时本

一个本地优先的 Android 记账与工时工资管理 App，基于 Kotlin + Jetpack Compose Material 3 开发。

## 功能概览

- 首页综合概览：汇总日常记账收入、工时实发工资、日常支出和综合结余。
- 工时管理：记录每日上班时间、休息时长、班次、加班、补贴和扣款。
- 工资计算：支持基本工资、正常工资、加班工资、补贴、奖金、扣款、社保、公积金、个税和实发工资。
- 工资周期：支持当前周期查看、左右切换往期周期和工资历史列表。
- 工时日历：支持农历/节日显示、漏记红色提示、点击日期直接编辑或补录。
- 日常记账：记录红包、转账、报销、买菜、吃饭等生活收入与支出流水。
- 数据备份：支持本地 JSON 导出与导入。
- 须知页：保留 App 使用说明。

## 当前统计口径

```text
综合收入 = 日常记账收入 + 工时实发工资
综合结余 = 日常记账收入 + 工时实发工资 - 日常记账支出
```

说明：

- 工时实发工资已经按工资规则计算社保、公积金和个税。
- 记账收入按日常实际到账金额统计，不重复计税。
- 首页展示的“工时工资到账/工资入账”属于自动汇总展示，不会重复写入真实记账流水。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Android SQLite 本地存储
- Gradle Kotlin DSL

## 构建方式

```bash
./gradlew assembleDebug --no-daemon
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 主要源码

```text
app/src/main/java/com/java/myapplication/MainActivity.kt
app/src/main/java/com/java/myapplication/LocalStore.kt
```

## 数据说明

本项目为本地应用，账目、工时、工资设置等数据默认保存在设备本地数据库中。建议定期使用右上角导出功能保存 JSON 备份。
