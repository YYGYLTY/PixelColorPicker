# PixelColorPicker — 开发文档（v2 重制版）

> 由 Operit AI 与用户共同完成的产品重制。本文档记录产品、设计、技术、协议与进度。

## 一、产品

### 定位
PixelColorPicker 是一个**本地、离线**的图片像素化工具：
把任意图片裁剪并转换为指定尺寸（1～999）的像素画，查看每一格颜色，
并生成与旧版兼容的「18 色板码」（一键复制 / 按列复制）。

### 用户核心场景
1. 选一张图 → 裁成画布比例 → 生成像素画 → 复制色板码去别处使用
2. 手动绘制空白像素画 → 导出 PNG

### 当前功能（v2.0 已发布）
- 首页：画布尺寸（1-999 任意输入 + 常用尺寸）、展示框（发光边框 + 开场动效）
- 展示框：点击选图 → 按画布比例裁剪 → 动画适应图片比例；拖拽调整（平移 / 双指缩放）
- 微调控制：放大 / 缩小 / 四向平移 / 顺逆时针旋转 / 重置；生成后实时重新像素化
- 算法选择：Nearest / Bilinear / Bicubic / Lanczos / Average（默认 Nearest）+ **自定义算法**
  （采样内核 × 平滑程度 1~8×，弹窗内实时预览）
- 生成像素画：按画布尺寸重采样；网格显示开关；保存 W×H PNG
- 新建空白画布：任意尺寸像素编辑器（画笔/橡皮/吸管/填充/撤销/重做/清空/导出）+ 历史记录
- 色板页：双向自由滚动网格（虚拟化绘制），点击格子复制 HEX，同色计数（后台统计）
- 编码页：按列复制色板码（16 色 + 2 白），与旧版 `PaletteExporter.exportColumn` 兼容
- 关于页（含本地统计）；语言切换（中文 / English / 日本語）；深色 / 浅色主题
- 检查更新：启动时读取仓库 `version.json`，静默对比 versionCode
- 本地统计：累计生成次数（仅本机 SharedPreferences，不上传）

### 未来可考虑
- 色板页缩放查看、编码页一键复制全部列
- 工程文件保存 / 最近作品
- 颜色减色（调色板量化）与抖动（Bayer / Floyd-Steinberg）

## 二、设计

### UI/UX 决策
- 深色为主基调 + 紫青强调色，与像素画工具的气质一致
- 「会发光的细边框」：`glowBorder` 修饰符用多层渐隐描边模拟光晕（兼容所有 API）
- 底部导航做成悬浮胶囊 + 半透明渐变（毛玻璃观感）+ 选中缩放动画
- 展示框空状态：欢迎文字淡入上移；有图后展示框比例做动画过渡
- 图标：全部为本次重制**手绘**的圆角线性图标（`AppIcons`），不使用旧版图标
- 交互原则：所有可点元素必须有反馈；所有按钮必须真实工作（无假功能）

## 三、技术

### 技术栈
- Kotlin + Jetpack Compose + Material 3（单 Activity）
- ViewModel（AndroidViewModel）+ Compose 状态
- 无网络、无多余权限（照片选择器 = 系统 Photo Picker）

### 架构
```
com.pixelcolorpicker
├── MainActivity.kt         // 导航（Tab + 全屏子页）、语言、图片选择器
├── codec/PaletteCodec.kt   // 18 色协议（纯 Kotlin，可单测）
├── image/
│   ├── BitmapLoader.kt     // 加载/EXIF/采样
│   └── Pixelator.kt        // 像素化引擎（5 算法 + 变换采样）
├── model/PixelArt.kt       // 可编辑画布（W×H，撤销/重做）+ PixelGrid
├── ui/
│   ├── MainViewModel.kt    // 全 App 状态与流程
│   ├── HomeScreen.kt       // 首页
│   ├── CropScreen.kt       // 裁剪（固定比例框 + 拖拽/缩放图片）
│   ├── EditorScreen.kt     // 空白画布编辑器
│   ├── PaletteScreen.kt    // 色板（虚拟化 2D 网格）
│   ├── CodeScreen.kt       // 编码（按列复制）
│   ├── AboutScreen.kt      // 关于
│   ├── components/         // PixelCanvas / ImageDisplayBox / ColorPickerPanel / BottomNav
│   ├── icons/AppIcons.kt   // 手绘图标集
│   └── theme/              // 颜色 / 主题 / glowBorder 修饰符
└── util/                   // PngExporter / ColorUtils / LocaleHelper
```

### 关键实现
- **像素化引擎**：对每个输出像素计算其源图覆盖范围（footprint），在范围内取 N×N 采样点，
  以对应算法（最近邻/双线性/双三次/Lanczos）取值后平均。缩小不丢细节、放大风格正确。
- **实时预览**：微调时防抖（140ms）重算**降采样预览**（≤512 边长）；完整分辨率网格按需计算
  （色板/编码/导出），避免 999×999 的实时重算卡顿。
- **色板虚拟化**：滚动占位 + 覆盖层 Canvas 只绘制可视格子，任意尺寸流畅。
- **大画布保护**：编辑器 >65536 像素时改用降采样位图绘制；撤销历史按 64MB 内存预算裁剪。

### 重要依赖
- Compose BOM、Material3、Activity Compose、Lifecycle ViewModel Compose、ExifInterface
- 无 Protobuf runtime（协议手写字节）

## 四、编码协议（「一键复制16色」/ 按列编码）

### 规则（与旧版源码 `PaletteExporter.java` 对齐）
1. 取该列 **16 个颜色**：
   - 高度 ≥16：均匀取样 16 个像素（高度 =16 时即原列）
   - 高度 <16：原色 + 白色补齐至 16
2. **追加 2 个白色**（0xFFFFFFFF），共 18 色
3. 每个颜色编码为 Protobuf 消息：
   - `r != 0` → `0x08` + varint(r)；`g != 0` → `0x10` + varint(g)；`b != 0` → `0x18` + varint(b)
   - 恒写 `0x20` + varint(255)（忽略原始 alpha）
4. 外层：固定 `0A 02 31 38`（"18"）+ 18 × (`0x12` + varint(len) + colorBytes)
5. `GZIPOutputStream` 压缩 → 标准 Base64（NO_WRAP，保留 `=`）
6. 复制到系统剪贴板

### 兼容性要求
- 画布高度 = 16 时，输出与旧版 **逐字节一致**（header 固定 "18"，16 色 + 2 白）

### 测试结果
- 单元测试 `PaletteCodecTest`：**14 个用例全部通过**
  - 纯黑/白/红/绿/蓝、混合颜色、列测试（第 0 列红 + 其余蓝 → 16 红 + 2 白）
  - alpha 忽略、header 固定、完整 GZIP+Base64 往返、按列编码 18 色
  - 列取样：16 高 = 原列 / 不足补白 / 32 高均匀取样
- 旧版源码参考：`/sdcard/AndroidIDEProjects/PixelColorPicker`（仅阅读，未修改）

## 五、开发进度

- [完成] 项目结构 / 主题 / 图标集
- [完成] 18 色协议编码器 + 14 项单元测试
- [完成] 首页（画布尺寸 1-999、展示框动画、微调、算法滑动选择、生成、保存）
- [完成] 裁剪页（画布比例裁剪框）
- [完成] 空白画布编辑器（添加图片到首页、导出 PNG）
- [完成] 色板页（虚拟化网格、点击复制）
- [完成] 编码页（按列复制，兼容旧版）
- [完成] 关于页 + 语言切换（中/英/日）+ 深色/浅色主题
- [完成] 自定义算法（内核 × 平滑程度，实时预览）
- [完成] 检查更新（version.json）+ 本地统计
- [完成] 性能优化：自适应采样（大画布提速约 9 倍）、预览复用、计数后台化
- [完成] 真机迭代打磨（裁剪、微调、动效、配色）
- [已发布] **v2.0**（APK + 源码 + version.json 上传 GitHub Releases）

## 六、已知问题

- GZIP 压缩层未与旧版做逐字节实测对照（旧版工程仅确认了算法逻辑；
  同为 Java `GZIPOutputStream`，理论上一致；高度 ≠16 的取样规则为合理扩展）
- Release 目前使用 debug 签名（与测试版保持覆盖安装兼容）；如需上架应用商店需换正式签名
  （注意：换签名后已安装的 debug 版本无法直接覆盖更新，需卸载重装）
- 检查更新依赖仓库根目录的 `version.json`；未上传时静默失败（不影响使用）

## 七、下一步

- 依据用户反馈继续打磨（手感、动效、配色）
- 颜色减色 + 抖动（像素画风格的下一步）
- 如需更严格兼容：用旧版对同一图生成输出做逐字节比对
