# 部署说明

## 文件清单

需要提交到仓库的内容：

```
仓库根/
├── .github/
│   └── workflows/
│       └── pages.yml      ← Pages 自动部署工作流
└── web/                   ← 站点根目录
    ├── index.html
    ├── README.md
    ├── css/style.css
    ├── js/codec.js
    ├── js/pixelator.js
    ├── js/i18n.js
    ├── js/app.js
    └── assets/icon.svg
```

## 三种部署方式

### 方式 A：GitHub Actions（推荐，全自动）

适合想保持 `web/` 子目录结构、不想污染主分支根目录的情况。

**步骤：**

1. 把 `web/` 和 `.github/workflows/pages.yml` 提交到 `main`
2. 打开仓库 **Settings → Pages**
3. **Build and deployment → Source** 选择 **GitHub Actions**
4. 推送到 `main`（或手动触发 workflow）
5. 等 Actions 跑完，访问 `https://<用户名>.github.io/<仓库名>/`

**好处：** 目录整洁、推送即部署、可回滚

**注意：** `pages.yml` 里写的是 `path: ./web`，如果你的目录名不同要同步改。

---

### 方式 B：gh-pages 分支（最简单，无需 Actions）

适合只想快点上线、不介意多一个分支。

**步骤：**

1. 创建 orphan 分支并放入站点文件：

```bash
git checkout --orphan gh-pages
git rm -rf .
cp -r web/* .
rm -rf web
git add .
git commit -m "deploy: PixelColorPicker Web"
git push origin gh-pages
```

2. 仓库 **Settings → Pages**
3. **Source** 选 **Deploy from a branch**
4. 分支选 `gh-pages`，目录选 `/ (root)`
5. 保存，等 1~2 分钟

访问 `https://<用户名>.github.io/<仓库名>/`

---

### 方式 C：docs/ 目录（主分支内，最省事）

适合不想用 Actions、也不想多分支。

**步骤：**

1. 把 `web/` 重命名为 `docs/`，放在仓库根目录
2. **Settings → Pages → Source** 选 **Deploy from a branch**
3. 分支 `main`，目录 `/docs`
4. 保存

访问 `https://<用户名>.github.io/<仓库名>/`

---

## 部署前自检

- [ ] `web/index.html` 里的资源路径是相对路径（`css/...`、`js/...`、`assets/...`），不要写成 `/css/...`
- [ ] 所有文件名大小写一致（Linux 上 Pages 区分大小写，Windows 本地开发容易踩坑）
- [ ] 仓库 **Settings → Pages** 的 Source 已正确设置

## 常见问题

**Q: 页面 404？**
A: 检查 Pages 的 Source 设置是否指向了正确目录；Actions 方式要确认 workflow 成功跑完。

**Q: 样式没加载？**
A: 多半是路径写成了绝对路径 `/css/style.css`。GitHub Pages 项目站点有子路径（`/<仓库名>/`），必须用相对路径。

**Q: 改了代码但页面没变？**
A: Pages 有 CDN 缓存，等 1~2 分钟；或强制刷新（Ctrl+Shift+R）。

**Q: 想绑自定义域名？**
A: **Settings → Pages → Custom domain** 填域名，然后在 DNS 加 CNAME 记录指向 `<用户名>.github.io`。

## 本地预览

部署前最好本地跑一下：

```bash
cd web
python3 -m http.server 8080
```

打开 <http://localhost:8080> 检查功能。

> `file://` 直接打开时，剪贴板 API 和 `CompressionStream` 可能受限，建议始终用本地服务器测试。