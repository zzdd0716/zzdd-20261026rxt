---
name: huawei-cloud-git-workflow
description: 华为云 CodeHub Git 版本迭代与安全工作流。用户说版本迭代 提交代码 分支保护 合并发布 安全检查 回滚时触发。不适用于非 Git 项目或本地仓库初始化之外的场景。
---

# 华为云 CodeHub Git 工作流

为当前仓库提供标准化的分支策略、提交规范、安全检查、合并发布和回滚操作指引。远程仓库位于 `git@codehub.devcloud.cn-north-4.huaweicloud.com`，通过 SSH 连接。

## 分支策略

采用简化版 Git Flow，分支层级如下：

| 分支 | 用途 | 推送权限 |
|------|------|----------|
| `master` | 生产发布分支，tag 从此打出 | 受保护，禁止直接推送 |
| `develop` | 开发集成分支 | 受保护，禁止直接推送 |
| `feature/<name>` | 新功能开发，命名用英文 kebab-case | 开发者本人可推送 |
| `hotfix/<name>` | 线上紧急修复 | 开发者本人可推送 |

功能分支从 `develop` 创建并合并回 `develop`；hotfix 分支从 `master` 创建，修复后**同时**合并到 `master` 和 `develop`。

## 工作流总览

1. **分支初始化** — 首次使用时从 `master` 创建 `develop` 并推送
2. **功能开发** — 从 `develop` 切 `feature/*`，开发完成后 rebase 最新 `develop` 再发起 Merge Request
3. **提交代码** — 每次提交前必须执行安全扫描
4. **合并发布** — 通过华为云 CodeHub 网页发起 MR，经审批后合并
5. **版本打 Tag** — 合并到 `master` 后按语义化版本打 tag
6. **紧急修复** — 从 `master` 切 `hotfix/*`，修复后双向合并
7. **回滚恢复** — 优先使用 `git revert`，保留历史

## 命令执行规范

- 所有 Git 命令前先确认当前分支 (`git branch --show-current`)
- 涉及 push 操作前，先 pull / fetch 确认远程状态
- 遇到合并冲突时，逐个文件解释冲突原因，给出解决方案，再执行 `git add` + `git rebase --continue`
- **禁止**在未告知用户的情况下执行 `git reset --hard` 或 `git push --force`（rebase 后的 force-with-lease 除外，且需明确说明）

## 提交信息规范

格式：`类型(范围): 简短描述（中文）`

| 类型 | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档变更 |
| `style` | 格式调整（不影响逻辑） |
| `refactor` | 重构（不新增功能、不修复 bug） |
| `test` | 测试相关 |
| `chore` | 构建、配置、依赖等杂项 |
| `hotfix` | 紧急线上修复 |

## 安全检查（每次提交前必经）

提交前必须扫描所有变更文件，输出安全报告，发现以下问题时**阻止提交**并给出修复方案：

1. **明文密码/密钥** — 检查 `application.yml`、`*.properties`、`*.env` 中是否有数据库密码、Redis 密码、API Key、私钥等
2. **内部路径泄露** — 检查是否有本地绝对路径（如 `d:/wangyanan/`）硬编码到配置中
3. **敏感文件** — 检查是否误提交了 `.pem`、`.key`、`.jks`、`*.log`、`.env.local` 等
4. **`.gitignore` 覆盖** — 确认 `.gitignore` 是否正确排除了上述文件

### 明文密码的处理

当发现 `application.yml` 或 `*.properties` 中有明文密码时，执行以下步骤：

1. 将敏感配置替换为环境变量占位符，例如 `password: ${DB_PASSWORD}`
2. 在 `.gitignore` 中添加 `application-local.yml` 和 `.env.local`
3. 创建 `application-local.yml.example` 作为模板（不含真实密码）
4. 如果密码**已经推送到远程**，告知用户 Git 历史清理方案（`git filter-repo` 或 BFG Repo-Cleaner），并提醒用户修改已暴露的密码

## SSH 安全

定期（建议每月）提醒用户检查以下事项：

- `ssh-keygen -lf ~/.ssh/id_rsa.pub` 确认密钥类型（推荐 `ed25519`，次选 RSA 4096 位）
- `ssh -T git@codehub.devcloud.cn-north-4.huaweicloud.com` 验证连接正常
- 如发现 RSA 密钥长度不足 4096，建议升级到 `ed25519`

## 华为云 CodeHub 分支保护配置

由于分支保护规则需要在网页上配置，AI 执行到这一步时必须暂停并列出网页操作指引，让用户手动完成：

1. 打开 https://console.huaweicloud.com/codehub/ → 进入仓库
2. 左侧菜单「设置」→「分支保护」
3. 为 `master` 添加保护：禁止 force push、禁止直接推送、最少审批人数 1
4. 为 `develop` 添加保护：禁止 force push、禁止直接推送
5. 保存后截图或确认已生效

## 回滚操作

| 场景 | 推荐操作 | 危险操作 |
|------|----------|----------|
| 撤销已提交到远程的 commit | `git revert <commit-hash>` | `git reset --hard` + force push |
| 撤销本地未 push 的 commit | `git reset --soft HEAD~1` 保留改动 | `git reset --hard HEAD~1` 丢弃改动 |
| Hotfix 后双向合并冲突 | 手动解决 + `git add` + `git merge --continue` | 跳过冲突直接提交 |

## 详细提示词模板

用户要深入执行某一流程时，将 `references/git-workflow-prompts.md` 中对应的模板作为当前对话的上下文执行。模板覆盖：分支初始化、功能开发、代码提交、合并发布、Hotfix、版本打 Tag、敏感信息扫描、SSH 安全检查、分支保护清单、回滚验证共 10 个场景。
