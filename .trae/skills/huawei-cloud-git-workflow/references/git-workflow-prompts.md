# Git 工作流提示词模板

以下模板在用户触发 huawei-cloud-git-workflow skill 后按需加载。每个模板都是一段可执行的指令集，AI 读取后按步骤操作。

---

## 1. 分支初始化

```
为当前仓库初始化分支策略：
1. 当前应在 master 分支（如不在则切换），git pull 确保最新
2. 创建 develop 分支：git checkout -b develop，推送到远程：git push -u origin develop
3. 列出华为云 CodeHub 网页上需要手动配置的分支保护规则（路径和具体选项）
4. 在仓库根目录创建 .gitmessage 模板文件，内容为：
   类型(范围): 简短描述
   类型列表：feat fix docs style refactor test chore hotfix
5. 配置 git config commit.template .gitmessage （仓库本地配置）
```

## 2. 功能开发

```
开始新功能开发：
1. git checkout develop && git pull 确保开发分支最新
2. 用英文 kebab-case 命名创建 feature 分支：git checkout -b feature/<name>
3. 告知当前分支状态和远程同步情况
4. 开发过程中用户说"提交代码"时，按模板执行模板 3
5. 开发完成后（用户说"开发完成"或类似），执行 rebase：
   - git fetch origin
   - git rebase origin/develop
   - 如有冲突，逐个解决 → git add <file> → git rebase --continue
   - rebase 成功后 git push --force-with-lease（必须用 force-with-lease 而非 force）
   - 提示用户去华为云 CodeHub 网页发起 Merge Request
```

## 3. 代码提交

```
提交当前修改：
1. git status 查看工作区状态
2. 执行安全扫描（检查 application.yml、properties、env 文件中的明文密码和内部路径）
3. 将安全扫描结果展示给用户，如有问题则提示修复后再提交
4. 安全检查通过后：git add <变更文件>（除非用户明确要求 add -A，否则逐个 add）
5. git diff --staged 查看暂存区内容，据此生成符合规范的 commit message
6. 执行 git commit -m "<类型>(<范围>): <描述>"
7. git push 推送到远程分支
8. 如在 feature 分支，提示用户去华为云 CodeHub 发起 Merge Request 合并到 develop
```

## 4. 合并发布（MR 准备）

```
合并前准备（执行在 feature 分支上）：
1. git fetch origin && git rebase origin/develop 确保基于最新 develop
2. 如有冲突逐个解决：git add <file> → git rebase --continue
3. rebase 完成后 git push --force-with-lease
4. 列出华为云 CodeHub 网页操作步骤：
   a. 打开仓库 → Merge Request → 新建
   b. Source branch: 当前 feature 分支
   c. Target branch: develop
   d. 填写标题和变更说明
   e. 指定审批人（至少 1 人）
   f. 提交 MR，等待审批合并
```

## 5. Hotfix 紧急修复

```
线上紧急修复流程：
1. git checkout master && git pull 确保基线正确
2. 用英文 kebab-case 创建 hotfix 分支：git checkout -b hotfix/<issue-description>
3. 用户完成修复后，执行模板 3 的提交流程
4. 修复提交后，依次合并到 master 和 develop：
   a. 切回 master：git checkout master && git pull && git merge --no-ff hotfix/<name>
   b. 推送到 master：git push origin master
   c. 切回 develop：git checkout develop && git pull && git merge --no-ff hotfix/<name>
   d. 推送到 develop：git push origin develop
5. 提示用户在华为云 CodeHub 为 master 打 tag（版本号格式 vX.Y.Z）
6. 建议用户删除远程 hotfix 分支：git push origin --delete hotfix/<name>
```

## 6. 版本打 Tag

```
版本发布打 Tag：
1. git log --oneline master -- 最近的提交记录
2. git tag --sort=-creatordate 查看已有 tag，据此建议下一个版本号（语义化）
   - 主版本号：不兼容的 API 变更
   - 次版本号：向下兼容的功能新增
   - 修订号：向下兼容的问题修复
3. 确认版本号后：git tag -a vX.Y.Z -m "发布说明"
4. 推送到远程：git push origin vX.Y.Z
5. 生成本次发布的变更日志：从上次 tag 到本次之间，提取类型为 feat 和 fix 的提交
6. 提示用户在华为云 CodeHub 网页创建 Release 并关联 tag
```

## 7. 敏感信息扫描

```
提交前敏感信息检查：
1. 扫描所有待提交文件（git diff --cached --name-only）
2. 对每个文件检查：
   - 正则匹配 password|passwd|secret|token|key|apikey|api_key|privateKey|PrivateKey
   - 检查是否有本地绝对路径（d:/ c:/ 等 Windows 路径或 /Users/ /home/ 等 Unix 路径）
   - 检查 application.yml / application.properties / *.env 中的 JDBC URL 和连接串
3. 特别关注文件：application.yml、bootstrap.yml、*.properties、*.env、*.pem、*.key、*.jks
4. 输出安全报告：文件名、行号、具体内容、风险等级（高/中/低）
5. 如发现高风险项，阻止提交并执行明文密码处理流程（见主 SKILL.md 安全检查章节）
6. 建议更新 .gitignore 添加敏感文件排除规则
```

## 8. SSH 安全检查

```
SSH 连接安全检查：
1. ssh -T git@codehub.devcloud.cn-north-4.huaweicloud.com 验证连接是否正常
2. ls ~/.ssh/ 列出本地密钥文件
3. 对每个公钥文件执行 ssh-keygen -lf <file>.pub，报告密钥类型和位数
4. 如发现 RSA 密钥位数 < 4096 或使用 DSA/ECDSA，建议升级到 ed25519
5. 检查 ssh-agent 状态：eval $(ssh-agent -s) && ssh-add -l
6. 给出密钥轮换建议（每 90 天轮换一次，或发现泄露立即轮换）
```

## 9. 分支保护配置指引

```
华为云 CodeHub 分支保护配置（需要用户手动在网页上操作）：
1. 打开 https://console.huaweicloud.com/codehub/
2. 找到仓库 → 左侧菜单「设置 Settings」→「分支保护 Branch Protection」
3. 为 master 添加保护规则：
   - 保护范围：分支名称精确匹配 master
   - 禁止 force push
   - 禁止直接推送（仅允许通过 MR 合并）
   - 最少审批人数：1
   - 允许合并请求发起人自己审批：关闭
4. 为 develop 添加保护规则：
   - 保护范围：分支名称精确匹配 develop
   - 禁止 force push
   - 禁止直接推送
5. 点击保存，截图确认生效
```

## 10. 回滚与备份验证

```
代码回滚能力验证：
1. 检查 .gitattributes 是否存在，如不存在则提示创建（统一换行符等）
2. 演示回滚方式（以最近一次提交为例，仅演示不执行）：
   - 安全方式：git revert HEAD（创建新提交撤销，保留历史）
   - 危险方式：git reset --hard HEAD~1 + git push --force（丢失历史）
3. 建议用户配置备用 remote 作为备份，命令如下：
   git remote add backup <备用仓库 SSH URL>
   每周执行一次：git push backup --all --tags
4. git config --local --list 检查是否有异常 remote、proxy 或 unsafe options 配置
5. git log --all --oneline --graph --decorate 输出完整分支图，确认历史清晰
```
