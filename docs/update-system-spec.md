# KonamikU 更新体系规格说明

> 目标读者：需要把这套更新机制移植到其他客户端（如 WPF 桌面应用）的开发者 / AI agent。
>
> 本文描述的是**线上真实行为**，不是设计意图。所有 JSON 示例均取自实际产物。

---

## 0. 一页速览

```
发布 tag (如 260743)
      │
      ├─→ GitHub Release 上传 app-release.apk
      │
      └─→ [GitHub Action] 生成 update.json + changelog-<tag>.json
                │
                └─→ 强推到 info 分支（orphan，单文件平铺在根目录）
                          │
                          ↓
              raw.githubusercontent.com/<repo>/info/update.json
                          │
                          ↓
                 [客户端] 拉取 → 比较 version_code → 展示 changelog → 下载 APK
```

三个核心设计决策，移植时值得保留：

1. **发布产物与客户端解耦** —— 客户端只认一个静态 JSON，换下载源/换仓库不用发新版。
2. **`version_code` 与 git tag 同构** —— tag 就是版本号，省掉映射表。
3. **changelog 用双向链表** —— 客户端按需翻页，不一次拉全部历史。

---

## 1. 数据契约

### 1.1 `update.json`（入口，唯一必需文件）

线上实际内容（分支 `info`，仓库根目录）：

```json
{
  "version_code": 260743,
  "version_name": "260743",
  "download_url": "https://github.com/Ra1nify/KonamikU/releases/download/260743/app-release.apk",
  "changelog_url": "https://raw.githubusercontent.com/Ra1nify/KonamikU/info/changelog-260743.json",
  "latest_changelog": "https://raw.githubusercontent.com/Ra1nify/KonamikU/info/changelog-260743.json"
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `version_code` | number（整数） | ✅ | 与客户端本地版本比较的唯一依据。**必须 > 0** |
| `version_name` | string | ✅ | 展示给用户 |
| `download_url` | string（绝对 URL） | ✅ | APK 直链 |
| `changelog_url` | string | ✅ | 当前版本 changelog |
| `latest_changelog` | string | ⬜ | 与 `changelog_url` 内容相同，客户端优先用它 |

> `latest_changelog` 和 `changelog_url` 目前由 Action 生成成同一个值，是历史遗留。客户端逻辑是「`latest_changelog` 非空则用它，否则用 `changelog_url`」。

### 1.2 `changelog-<tag>.json`（每版本一个）

```json
{
  "version_name": "260743",
  "date": "2026-07-24",
  "previous_changelog": "https://raw.githubusercontent.com/Ra1nify/KonamikU/info/changelog-260730.json",
  "next_changelog": null,
  "commits": [
    {
      "hash": "784570ebfd6b80c0bbb8380eb3c1714ccb8b9d43",
      "title": "better logic for status bar long press",
      "body": ""
    },
    {
      "hash": "\nb5a430d98a27c96ecd608accc0ca7cc2bc532b24",
      "title": "rename the hidden api",
      "body": "hidden api is just Native_Support_Felica, e.g device sold in japan\n"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `version_name` | string | tag 名 |
| `date` | string | `YYYY-MM-DD`，取该 tag 的提交时间（ISO 日期部分） |
| `previous_changelog` | string \| null | 上一个版本的**绝对 URL**；最早一版为 `null` |
| `next_changelog` | string \| null | 下一个版本的**绝对 URL**；最新一版为 `null` |
| `commits` | array | 该版本区间内的提交 |
| `commits[].hash` | string | 提交 SHA ⚠️ **见 §4.1，有已知缺陷** |
| `commits[].title` | string | 提交标题（`%s`） |
| `commits[].body` | string | 提交正文（`%b`），可能为空串 |

---

## 2. 服务端：GitHub Action 生成逻辑

文件：`.github/workflows/update-changelog.yml`

### 2.1 触发条件

```yaml
on:
  release:
    types: [published]     # 且 !prerelease（见下方 if）
  workflow_dispatch:
    inputs:
      tag: { description: 'Git tag', required: false, default: '' }
```

```yaml
jobs:
  build:
    if: ${{ !github.event.release.prerelease }}
```

- 只在**正式 release 发布**时触发；prerelease 直接跳过。
- 支持手动 dispatch，可传 tag；不传则取最新 tag。

### 2.2 Step 1：解析并校验版本号

```bash
if [ -n "$TAG_INPUT" ]; then TAG="$TAG_INPUT"
elif [ "${GITHUB_REF#refs/tags/}" != "${GITHUB_REF}" ]; then TAG="${GITHUB_REF#refs/tags/}"
else TAG=$(git tag --sort=-creatordate | head -1); fi

# 必须能解析出 tag
if [ -z "$TAG" ] || [ "$TAG" = "HEAD" ]; then exit 1; fi

# tag 必须是纯数字
if ! [[ "$TAG" =~ ^[0-9]+$ ]]; then
  echo "::error::Tag '$TAG' is not numeric..."
  exit 1
fi
```

**硬性约束**：`tag` 必须是纯数字。因为 `version_code` 直接取 tag，而不是单独的元数据文件。

### 2.3 Step 2：生成全部 changelog（链表回填）

这是整个流程里最巧妙也最脆弱的一步。

```bash
BASE="https://raw.githubusercontent.com/${REPO}/info"
# 只取纯数字 tag，按 committerdate 升序
TAGS=$(git for-each-ref --sort=committerdate refs/tags --format='%(refname:short)' \
       | grep -E '^[0-9]+$' || true)

PREV=""
for TAG in $TAGS; do
  if [ -z "$PREV" ]; then
    RANGE="$TAG"                      # 最早的版本：从根开始
  else
    RANGE="${PREV}..${TAG}"           # 中间版本：区间
    # 关键：回填上一版的 next_changelog
    jq ".next_changelog = \"${BASE}/changelog-${TAG}.json\"" \
       "/tmp/_info/changelog-${PREV}.json" > tmp || { echo "::error::"; exit 1; }
    mv tmp "/tmp/_info/changelog-${PREV}.json"
  fi

  # NUL 分隔提取全部提交
  git log "$RANGE" --reverse --format='%H%x00%s%x00%b%x00' > raw.txt
  jq -Rs '[split("\u0000") | .[0:-1] | _nwise(3) | {hash: .[0], title: .[1], body: .[2]}]' \
     raw.txt > commits.json

  PREV_LINK=""
  [ -n "$PREV" ] && PREV_LINK="${BASE}/changelog-${PREV}.json"

  jq -n --arg vn "$TAG" \
        --arg dt "$(git log -1 --format=%aI "$TAG" | cut -dT -f1)" \
        --arg prev "$PREV_LINK" \
        --argjson commits "$(cat commits.json)" \
        '{version_name: $vn, date: $dt,
          previous_changelog: (if $prev == "" then null else $prev end),
          next_changelog: null,
          commits: $commits}' > "/tmp/_info/changelog-${TAG}.json"

  PREV="$TAG"
done
```

**要点**：

- **每次都全量重建**——遍历所有数字 tag，从第一个开始重算。改历史 tag、删 tag 都会反映出来。
- **`previous_changelog` 在生成当前版时写入**；`next_changelog` 只能等下一版生成时**回填**，所以先写 `null`。
- 链表靠两个方向的赋值凑成，任一侧失败就是断链。

### 2.4 Step 3：生成 `update.json`

```bash
jq -n --arg tag "$TAG" --arg repo "${REPO}" '{
  version_code: ($tag | tonumber),
  version_name: $tag,
  download_url: "https://github.com/\($repo)/releases/download/\($tag)/app-release.apk",
  changelog_url: "https://raw.githubusercontent.com/\($repo)/info/changelog-\($tag).json",
  latest_changelog: "https://raw.githubusercontent.com/\($repo)/info/changelog-\($tag).json"
}' > /tmp/_info/update.json
```

**`download_url` 是约定而非探测**：写死 `releases/download/<tag>/app-release.apk`。所以 Release 里的 **asset 文件名必须精确是 `app-release.apk`**，否则链接 404。

### 2.5 Step 4：产物校验（推送前的闸门）

四项检查，任一失败 → `FAIL=1` → 不推送 `info` 分支：

| # | 检查 | 目的 |
|---|---|---|
| 1 | 5 个必需字段非空 + `version_code` 匹配 `^[1-9][0-9]*$` | 拦住 `0` / 负数 / 空字段 |
| 2 | `changelog-<tag>.json` 确实已生成 | 拦住生成环节漏掉 |
| 3 | `curl -fsIL` 能 HEAD 到 `download_url` | **拦住 APK 改名 / asset 缺失** |
| 4 | 遍历所有 changelog，`previous_/next_changelog` 指向的文件都存在于本次生成集 | 拦住静默断链 |

第 3 项特别重要——它把「JSON 正确但 APK 不在」这个原本静默的问题变成了失败。

### 2.6 Step 5：推送到 `info` 分支

```bash
git checkout --orphan _info_branch
git reset --hard
cp -r /tmp/_info/* .
rm -f .gitignore

git add -A
if ! git diff --cached --quiet; then
  git commit -m "chore: auto-generate update info for ${TAG}"
  git push origin _info_branch:info --force
fi
```

- **orphan 分支**：无历史，只有产物文件，平铺在根目录。
- **`--force`**：全量替换。不在 `/tmp/_info` 里的文件会被删除（这也是为什么删 tag 会删掉对应 changelog 文件）。
- 内容无变化则跳过 commit（幂等）。

最终 `info` 分支结构：

```
update.json
changelog-260630.json
changelog-260651.json
changelog-260710.json
changelog-260722.json
changelog-260730.json
changelog-260743.json
...
```

### 2.7 Step 6：Job Summary

失败也要输出（`if: always()`），把 `update.json` 内容写进 Actions 页面摘要，发布后一眼可见生效的版本号。

---

## 3. 客户端：拉取与判断逻辑

参考实现：Kotlin / Android。**核心逻辑与平台无关**，移植到 WPF 只需替换网络层和 UI 层。

### 3.1 端点解析（优先级）

三个配置项，**优先级从高到低**：

| 配置 | 含义 |
|---|---|
| `customUrl` | 完整独立 URL，**不加任何前缀**，优先级最高 |
| `updateMode` = `PROXY` | 在 `githubUrl` 前拼 `mirrorPrefix` |
| `updateMode` = `GITHUB` | 直连 `githubUrl` |

默认值：

```
githubUrl   = "https://raw.githubusercontent.com/Ra1nify/KonamikU/info/update.json"
mirrorPrefix = "https://gh-proxy.com/"
customUrl   = ""
mode        = GITHUB
```

解析逻辑：

```kotlin
val effectiveUrl = when {
    customUrl.isNotBlank() -> customUrl
    mode == PROXY && mirrorPrefix.isNotBlank() ->
        "${mirrorPrefix.trimEnd('/')}/${githubUrl.trimStart('/')}"
    else -> githubUrl
}
```

**镜像只是字符串前缀拼接**，拼出来是 `https://gh-proxy.com/https://raw.githubusercontent.com/...`。这不是标准代理协议，而是 gh-proxy 这类服务的特定 URL 形式。

### 3.2 检查流程

```
1. 解析 URL          → 失败: NO_URL
2. HTTP GET          → 按状态码分类失败
3. 解析 JSON          → 失败: MALFORMED
4. 校验 version_code  → <= 0: INVALID
5. 拉 changelog（无论是否有更新，都要拉，用于展示）
6. 比较 version_code  → <= 本地: hasUpdate = false
7. 否则              → hasUpdate = true，返回下载链接
```

伪代码：

```
function check(githubUrl, mirrorPrefix, customUrl) -> UpdateState:
    url = resolveUrl(...)
    if url is null: return error(NO_URL)

    result = httpGet(url)
    if result is Failure: return error(result.kind)
    text = result.body

    info = parse<UpdateInfo>(text)  // 失败 → error(MALFORMED)

    if info.version_code <= 0:
        return error(INVALID)        // ← 关键：不能当成"已是最新"

    changelogUrl = if info.latest_changelog != "" then info.latest_changelog
                   else info.changelog_url
    // 镜像模式且非 customUrl 时，changelog URL 也要加前缀
    if mirrorPrefix != "" and customUrl == "":
        changelogUrl = mirrorPrefix.trimEnd('/') + "/" + changelogUrl.trimStart('/')

    changelog = fetchChangelog(changelogUrl)   // 失败不影响主流程，返回 null

    if info.version_code <= currentVersionCode:
        return { hasUpdate: false, changelog: changelog }

    return {
        hasUpdate:    true,
        latestVersion: info.version_name,
        latestCode:   info.version_code,
        downloadUrl:  info.download_url,
        changelogUrl: changelogUrl,
        changelog:    changelog
    }
```

### 3.3 HTTP 错误分类

**这是最容易做错的部分**。把所有失败压成"网络错误"会让用户去查自己的网，而真实原因可能是服务端坏了。

| HTTP / 异常 | 分类 | 含义 | 用户能做什么 |
|---|---|---|---|
| DNS / 超时 / socket 异常 | `NETWORK` | 本地网络问题 | 检查网络、换源 |
| 401 / 403 / 429 | `BLOCKED` | 镜像限流或拒绝 | 稍后重试、换源 |
| 404 / 410 | `NOT_FOUND` | **服务端**没发布 | 无 |
| 其他非 200 | `HTTP` | 意外状态码 | 无 |
| JSON 解析失败 | `MALFORMED` | **服务端**数据坏了 | 无，需反馈 |
| `version_code <= 0` | `INVALID` | **服务端**数据无效 | 无，需反馈 |

`NOT_FOUND` / `MALFORMED` / `INVALID` 都指向服务端，重试永远不会好——必须和 `NETWORK` 区分开。

### 3.4 超时设置

```kotlin
private const val TIMEOUT_MS = 8_000L   // connect + read
```

3 秒在 gh-proxy 往返下失败率过高，产生大量误报。8 秒是折中。

### 3.5 Changelog 翻页

`Changelog` 对象带 `previous_changelog` / `next_changelog` 两个绝对 URL：

- 非 `null` → 按钮可用，点击即用该 URL 拉取
- `null` → 按钮置灰（链表尽头）

翻页时若处于镜像模式，同样要给 URL 加前缀：

```kotlin
val finalUrl = if (mirrorPrefix.isNotBlank())
    "${mirrorPrefix.trimEnd('/')}/${url.trimStart('/')}"
else url
```

### 3.6 下载链接与镜像

**`download_url` 由客户端决定是否加镜像前缀**，服务端只给直链：

```kotlin
fun resolveDownloadUrl(downloadUrl: String): String =
    if (changelogMirrorPrefix.isBlank()) downloadUrl
    else "${changelogMirrorPrefix.trimEnd('/')}/${downloadUrl.trimStart('/')}"
```

⚠️ **已知限制**：gh-proxy 这类代理对 `github.com/.../releases/download/...` 的支持因服务而异，部分只代理 `raw.githubusercontent.com`。客户端应保留**失败回退到直连**的一次重试。

### 3.7 自动检查调度（Android 特有，供参考）

- WorkManager `PeriodicWorkRequest`，`NetworkType.CONNECTED` 约束
- 唯一任务名 `konamiku_update_check`，`ExistingPeriodicWorkPolicy.UPDATE`
- 可选间隔：关 / 30min / 1h / 2h / 6h / 12h / 24h / 3d / 7d

**节流逻辑**（手动检查会绕过）：

```
if interval == OFF: skip
if (now - lastCheck) < interval * 0.8: skip
```

结果落盘：`lastCheck` 只在**成功**（有更新或无更新的正常结果）时更新，出错时不更新，避免把错误当成"刚检查过"。

---

## 4. 已知缺陷（移植时请修正，不要复制）

### 4.1 ⚠️ `commits[].hash` 带前导换行

线上实际数据：

```json
{ "hash": "\nb5a430d98a27c96ecd608accc0ca7cc2bc532b24", ... }
```

只有**该版本的第一个提交** `hash` 是干净的，之后每个都多了 `\n`。

**原因**：`git log --format` 在每个提交记录之间插入一个换行分隔符，NUL 切分后残留到下一段开头。实测确认：

```
[0] = 784570ebfd6b80c0bbb8380eb3c1714ccb8b9d43       ← 干净
[3] = \nb5a430d98a27c96ecd608accc0ca7cc2bc532b24     ← 前导换行
[5] = hidden api is just Native_Support_Felica...\n  ← 尾部换行（正文自带，正常）
```

**修正方式**：给 `git log` 加 `-z`（`--null`），用 NUL 而不是换行分隔记录：

```bash
git log "$RANGE" --reverse -z --format='%H%x00%s%x00%b' > raw.txt
```

实测加 `-z` 后 `[3]` 变为干净的 SHA。注意此时 `--format` 里末尾的 `%x00` 要去掉，否则会多一个空段。

备选：在 jq 里清洗

```jq
{hash: (.[0] | gsub("^\\s+|\\s+$"; "")), title: .[1], body: .[2]}
```

> `--no-separator` 不可用（git 不认这个参数，已实测报错）。

**对客户端的影响**：`hash` 仅用于展示（如需跳转 commit 页），带换行会拼出坏 URL。目前 KonamikU 客户端未使用 `hash` 字段，所以线上虽有脏数据但无实际故障。

### 4.2 非数字 tag 会污染产物

**当前状态**：workflow 已加 `grep -E '^[0-9]+$'` 过滤，且 `Resolve versions` 步骤会拒绝非数字 tag。

**历史遗留**：若 tag 曾被发布过，`info` 分支上可能残留 `changelog-hello.json` 之类的文件。因为推送是 force + 全量替换，下次 Action 运行会自动清理。

### 4.3 `version_code` 编码天花板

`version_code = yyMM + major + minor`（例：`2607` + `1` + `0` = `260710`）。

- `major` 或 `minor` 超过一位数会位数不定长，`1.10` 与 `11.0` 会撞车。
- 约束：**major / minor 都必须保持 0–9**。

移植建议：若你的项目版本节奏更快，改用固定位数（如 `yyMM` + 2 位 major + 2 位 minor）。

### 4.4 仓库改名后必须重新生成产物

本项目曾把仓库从 `C-F0x/KonamikU` 改名到 `Ra1nify/KonamikU`。改名之前生成的 `update.json` 里，`download_url` 和 `changelog_url` 仍然写死旧名，只靠 GitHub 对改名仓库的 301 转发才解析成功。

一旦旧名被复用或删除，转发失效，**所有已发布客户端的下载与 changelog 链接会同时断掉**——而这些客户端手里只有旧的 URL，改不了。

**根治**：改名后立即重新跑一次生成流程，让 `info` 分支上的产物用新名重建；同时确保生成脚本用 `GITHUB_REPOSITORY` 这类运行时变量，而不是硬编码 owner。

**移植时**：本文档 §1 的示例已按新仓库名 `Ra1nify` 书写。若你的项目也经历过改名，注意 `update.json` 里残留的旧名会长期潜伏——它不会报错，只是有一天突然 404。

---

## 5. 移植到 WPF 的对应关系

| Android 侧 | WPF 侧建议 |
|---|---|
| `HttpURLConnection` | `HttpClient`（注意 `AutomaticDecompression`、`Timeout`） |
| `kotlinx.serialization` | `System.Text.Json`，配 `PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower` 或 `[JsonPropertyName]` |
| `UpdateFailure` 枚举 | 同名 `enum`，保持分类一致 |
| `UpdateState` data class | `record` / `class` |
| DataStore 配置持久化 | `Properties.Settings` 或 JSON 配置文件 |
| WorkManager 周期任务 | `DispatcherTimer` 或 Windows 计划任务 |
| 前台服务 + 通知 | `ToastNotificationManager` / 应用内进度窗 |
| `ACTION_VIEW` 打开浏览器 | `Process.Start(new ProcessStartInfo(url) { UseShellExecute = true })` |
| 内嵌字体/颜色 | 无对应，忽略 |

**最低实现清单**（能跑起来所需的最小集）：

1. 拉 `update.json`（GET + 8s 超时）
2. 解析 5 个字段
3. `version_code <= 0` → 报错
4. 与本地版本比较
5. 有更新 → 弹窗展示 `version_name` + 打开 `download_url`
6. 拉 `changelog_url` → 展示 `commits[].title`
7. 错误分类至少区分「网络」和「服务端」

**可选进阶**：

- 镜像前缀（gh-proxy）
- changelog 双向翻页
- 下载进度 + 校验
- 自动检查节流

---

## 6. 快速自检清单

移植完成后，用这些用例验证：

| 场景 | 期望行为 |
|---|---|
| 正常拉取 | 解析出 `version_code`，与本地比较 |
| `update.json` 404 | 报 `NOT_FOUND`，**不是** "已是最新" |
| `version_code = 0` | 报 `INVALID`，**不是** "已是最新" |
| 返回 HTML 而非 JSON | 报 `MALFORMED` |
| 断网 | 报 `NETWORK` |
| `download_url` 404 | 报错而非静默（服务端应有 §2.5 的检查，客户端也应有兜底） |
| `previous_changelog` 为 `null` | "上一版"按钮置灰 |
| 本地版本 = 远端版本 | 显示 changelog，但提示"已是最新" |
| 镜像超时 | 回退直连重试一次 |

---

## 7. 文件索引

| 文件 | 角色 |
|---|---|
| `.github/workflows/update-changelog.yml` | 服务端生成逻辑 |
| `app/src/main/java/org/cf0x/konamiku/system/UpdateChecker.kt` | 拉取 + 解析 + 错误分类 |
| `app/src/main/java/org/cf0x/konamiku/system/UpdateManager.kt` | 端点解析 + 调度 + 通知 |
| `app/src/main/java/org/cf0x/konamiku/data/UpdateModels.kt` | 数据模型 + `UpdateFailure` 枚举 |
| `app/src/main/java/org/cf0x/konamiku/system/UpdateDownloadService.kt` | APK 下载 + 安装（Android 特有） |
| `app/src/main/res/values/strings.xml` | 错误文案（`setting_update_err_*`、`update_err_*`） |
