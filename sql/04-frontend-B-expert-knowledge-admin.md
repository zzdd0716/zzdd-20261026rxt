# 前端 B - 专家服务 + 在线问答 + 农业知识 + 管理员后台

> 路由和公共组件前端 A 已经配好，你只需要实现下面列出的 `.vue` 页面文件。  
> A 的目录结构是 `src/views/expert/ExpertList.vue` 这种路径，跟下面一致。  
> 所有接口 URL 前面自动加 `http://localhost:8088`，不要自己写 baseURL。

---

## 一、专家服务模块

---

### 3.1.1 专家列表 ExpertList.vue

**路由**: `/expert`  
**功能**: 专家列表，支持关键词搜索和专长筛选

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 专家列表 | GET | `/api/expert` | 分页查询认证通过的专家 |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 按姓名搜索 |
| specialty | string | 否 | 专长关键词 |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

**展示字段（来自响应 data.list[]）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 专家卡片 | id | 点击跳转 `/expert/{id}` |
| | realName | 专家姓名 |
| | avatar | 头像URL |
| | title | 职称（如"高级农艺师"） |
| | specialty | 专长领域（后端返回逗号分隔字符串，渲染成多个 el-tag） |
| | consultPrice | 咨询价格（元/次），**后端字段叫 consultPrice，不是 consultationFee** |
| | rating | 评分（如 4.9） |
| | consultCount | 已咨询次数，**后端字段叫 consultCount，不是 consultationCount** |

**用户操作流程**
```
进页面 → GET /api/expert
搜索 → 改 keyword / specialty 参数 → 重新加载
点专家卡片 → router.push('/expert/' + id)
```

---

### 3.1.2 专家详情 ExpertDetail.vue

**路由**: `/expert/:id`  
**功能**: 专家完整资料 + 最近评价（如有）+ 预约入口

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 专家详情 | GET | `/api/expert/{id}` | |

**展示字段（来自响应 data）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 头部卡片 | id | |
| | realName | 姓名 |
| | avatar | 头像 |
| | title | 职称 |
| | specialty | 专长（逗号分隔） |
| | consultPrice | 咨询价格，**后端叫 consultPrice，不是 consultationFee** |
| | rating | 评分 |
| | consultCount | 累计咨询次数，**后端叫 consultCount，不是 consultationCount** |
| | introduction | 个人简介（富文本/纯文本） |
| 评价区 | — | 后端专家详情接口不返回评价列表（当前项目无评价模块），前端这一块可以先不做或显示"暂无评价" |
| 预约按钮 | — | 未登录: router.push('/login')；已登录: router.push('/appointment/create/' + id) |

> 注意：后端专家详情接口**不返回 certification**（资质证书图片），那只在专家审核状态接口里有，用户自己在 `/my/expert-audit` 里看。专家详情页不要期望这个字段。

---

### 3.1.3 预约专家 AppointmentCreate.vue

**路由**: `/appointment/create/:id`  
**权限**: 需登录  
**功能**: 选择可用时间段 + 填写预约信息 + 提交

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 专家详情 | GET | `/api/expert/{id}` | 页面顶部展示专家信息（expertId 来自 route.params.id） |
| 可用时段 | GET | `/api/expert/{id}/available-time` | 日历形式的空闲时段 |
| 提交预约 | POST | `/api/appointment` | |

**专家可用时段接口响应结构（来自 GET `/api/expert/{id}/available-time` data）**

```json
[
  { "date": "2026-09-25", "slots": ["09:00-09:30", "09:30-10:00", "14:00-14:30"] },
  { "date": "2026-09-26", "slots": ["10:00-10:30", "15:00-15:30"] }
]
```

前端渲染：先按 date 分组显示日期卡，每张日期卡下渲染多个 slots（el-button-group），点击某个 slot 记录选择的时间段。

**提交预约 POST `/api/appointment` 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| expertId | long | 是 | 专家ID（来自 route.params.id） |
| appointmentTime | string | 是 | 开始时间，格式 "2026-09-25 09:00:00"（从选中的 date + slot 拼接） |
| duration | int | 是 | 时长，分钟数（slot "09:00-09:30" → 30；"09:00-10:00" → 60） |
| topic | string | 否 | 咨询主题，如"苹果树修剪" |
| description | string | 否 | 详细描述问题 |

**用户操作流程**
```
进页面 → GET /api/expert/{id} 显示专家信息
       → GET /api/expert/{id}/available-time 显示空闲日历
选一个时间段 → 高亮选中
填 topic 和 description（可选）
点"提交预约" → POST /api/appointment
  → 成功: ElMessage.success('预约成功，等待专家确认')
           → router.push('/appointment/my-user')
  → 失败: ElMessage.error(msg)（时间冲突、专家不可用等）
```

---

### 3.1.4 我的预约（用户）AppointmentMyUser.vue

**路由**: `/appointment/my-user`  
**权限**: 需登录  
**功能**: 用户作为预约发起者，查看自己的预约列表 + 取消预约

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 用户预约列表 | GET | `/api/appointment/my/user` | |
| 取消预约 | PUT | `/api/appointment/{id}/cancel` | |

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 专家 | expert.realName | 专家姓名（后端返回嵌套对象） |
| 主题 | topic | |
| 预约时间 | appointmentTime | 格式 "2026-09-25 09:00:00" |
| 时长 | duration | 显示 "XX分钟" |
| 状态 | status | 0待确认/1已确认/2已完成/3已取消（StatusTag type="appointment"） |
| 操作 | — | 待确认(0)→取消；其他状态→无操作 |

---

### 3.1.5 我的预约（专家）AppointmentMyExpert.vue

**路由**: `/appointment/my-expert`  
**权限**: 需登录且 role=2 专家  
**功能**: 专家视角，看别人预约自己的记录 + 确认 + 标记完成

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 专家预约列表 | GET | `/api/appointment/my/expert` | |
| 确认预约 | PUT | `/api/appointment/{id}/confirm` | |
| 完成预约 | PUT | `/api/appointment/{id}/complete` | |

**列表响应字段**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 用户 | questioner.realName / questioner.phone | 预约用户姓名和手机号（后端返回嵌套对象） |
| 主题 | topic | |
| 预约时间 | appointmentTime | |
| 时长 | duration | |
| 状态 | status | StatusTag type="appointment" |
| 操作 | — | 待确认(0)→确认；已确认(1)→完成；已完成(2)/已取消(3)→无操作 |

> 注意：后端响应中需确认是否返回 questioner 对象。如果后端未返回 questioner，则前端需要用其他方式获取预约人信息。

---

## 二、在线问答模块

---

### 3.2.1 问答列表 QaList.vue

**路由**: `/qa`  
**功能**: 浏览所有公开的问答

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 问答列表 | GET | `/api/qa` | |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 按标题搜索 |
| categoryId | long | 否 | 按知识分类筛选（用 type=2 的 CategorySelect） |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

**列表响应字段（来自 data.list[]）**

| 卡片位置 | 字段 | 说明 |
|---|---|---|
| 标题 | title | 点击跳转 `/qa/{id}` |
| 提问者 | questioner.realName | 后端字段叫 questioner，**不是 author** |
| 提问时间 | createTime | |
| 回答数 | replyCount | |
| 状态 | status | 0待回答/1已回答/2已关闭 |

> 注意：后端问答列表接口**不返回 categoryName**（只有 categoryId）和 content（正文）。所以：
> - 分类筛选可以传 categoryId，但列表里**不要显示**分类名（或者前端调分类接口自己做 id→name 映射）
> - 列表卡片只显示标题，不要试图截取正文摘要（后端不返回）
> - 提问者字段叫 `questioner.realName`，不是 `author.realName`

**用户操作流程**
```
进页面 → GET /api/qa
点标题 → router.push('/qa/' + id)
点"我要提问"（需登录）→ router.push('/qa/create')
```

---

### 3.2.2 发起提问 QaCreate.vue

**路由**: `/qa/create`  
**权限**: 需登录  
**功能**: 发布一个问题

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 分类下拉 | GET | `/api/category/2` | 问答分类列表 |
| 发起提问 | POST | `/api/qa` | |

**请求参数 (POST)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | string | 是 | 问题标题，限 50 字 |
| content | string | 是 | 问题描述（el-input type="textarea" 或 wangEditor） |
| categoryId | long | 是 | 分类ID（type=2 的 CategorySelect） |

**用户操作流程**
```
进页面 → GET /api/category/2 填分类下拉
填 title + content + 选 categoryId
提交 → POST /api/qa → 成功后 router.push('/qa')
```

---

### 3.2.3 问答详情 QaDetail.vue

**路由**: `/qa/:id`  
**功能**: 问题详情 + 所有回答 + 自己可以回答

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 问答详情 | GET | `/api/qa/{id}` | |
| 新增回答 | POST | `/api/qa/{id}/reply` | |
| 删除我的回答 | DELETE | `/api/qa/reply/{id}` | |

**问答详情响应字段（来自 GET 响应 data）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 问题区 | id | |
| | title | 问题标题 |
| | content | 问题正文 |
| | questioner.realName | 提问者（匿名时显示"匿名用户"），**后端叫 questioner 不是 author** |
| | questioner.avatar | 提问者头像 |
| | createTime | 提问时间 |
| | viewCount | 浏览量 |
| | status | 0待回答/1已回答/2已关闭 |
| 回答列表 | replies[] | 所有回答 |
| 每条回答 | replies[].id | 回答ID（删回答要用） |
| | replies[].content | 回答内容 |
| | replies[].replier.realName | 回答者姓名，**后端叫 replier 不是 author** |
| | replies[].replier.avatar | 回答者头像 |
| | replies[].replier.role | 回答者角色，1农民/2专家/3管理员（专家可以显示 el-tag"专家"） |
| | replies[].createTime | 回答时间 |

**POST `/api/qa/{id}/reply` 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| content | string | 是 | 回答内容 |

**用户操作流程**
```
进页面 → GET /api/qa/{id} → 渲染问题区 + 回答列表
未登录 → 回答区提示"请先登录后回答"
已登录 → 底部显示回答输入框
提交回答 → POST /api/qa/{id}/reply → 成功后刷新列表
删除自己的回答 → 只有 replier.id === store.userInfo.id 才显示删除按钮
  → ElMessageBox.confirm → DELETE /api/qa/reply/{id}
```

---

### 3.2.4 我的问答 QaMy.vue

**路由**: `/qa/my`  
**权限**: 需登录  
**功能**: Tab 切换"我的提问" / "我的回答"

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 我的提问列表 | GET | `/api/qa/my/question` | |
| 我的回答列表 | GET | `/api/qa/my/reply` | |

**我的提问列表响应字段（来自 GET /api/qa/my/question data[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 标题 | title | 点击跳 `/qa/{id}` |
| 状态 | status | 0未回答/1已回答 |
| 回答数 | replyCount | |
| 提问时间 | createTime | |

> 注意：后端"我的提问"接口返回的数据**没有 categoryName 和 viewCount**，表格里不要显示这两列。

**我的回答列表响应字段（来自 GET /api/qa/my/reply data[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 问题标题 | qa.title | 后端返回嵌套对象 `{ id, title }`，所以用 qa.title 取标题，**不是 questionTitle** |
| 我的回答 | content | 回答内容（可截取前 50 字显示） |
| 回答时间 | createTime | |

> 注意：后端"我的回答"接口**不返回 isExpert** 字段。如果要显示专家标识，可以在每个回答项里拿不到，就先不显示。

---

## 三、农业知识模块

---

### 3.3.1 知识列表 KnowledgeList.vue

**路由**: `/knowledge`  
**功能**: 知识文章列表，支持分类筛选和关键词搜索

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 知识列表 | GET | `/api/knowledge` | |
| 知识分类 | GET | `/api/category/2` | 左侧分类树 |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 标题搜索 |
| categoryId | long | 否 | 分类筛选 |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

**列表响应字段（来自 data.list[]）**

| 卡片位置 | 字段 | 说明 |
|---|---|---|
| 文章卡片 | id | 点击跳转 `/knowledge/{id}` |
| | title | 文章标题 |
| | coverImage | 封面图（知识的封面确实叫 coverImage ✅，和商品的 image 不同） |
| | summary | 摘要/简介 |
| | author.realName | 作者姓名（后端返回嵌套对象 author:{id, realName}，前端写 data.author.realName，不要写成 authorName） |
| | categoryName | 分类名称 |
| | viewCount | 浏览量 |
| | createTime | 发布时间 |

**用户操作流程**
```
进页面 → 并发 GET /api/knowledge + GET /api/category/2
左侧分类树 → 点击切换 categoryId → 重新加载
顶部搜索框 → 输入关键词 → 重新加载
点文章卡片 → router.push('/knowledge/' + id)
```

---

### 3.3.2 知识详情 KnowledgeDetail.vue

**路由**: `/knowledge/:id`  
**功能**: 文章全文展示 + 浏览量+1（后端自动处理）

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 知识详情 | GET | `/api/knowledge/{id}` | |

**详情响应字段（来自响应 data）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 顶部 | title | 标题 |
| 元信息 | author.realName | 作者 |
| | author.avatar | 作者头像 |
| | categoryName | 分类名 |
| | viewCount | 浏览量 |
| | createTime | 发布时间 |
| 封面 | coverImage | 封面图（知识确实叫 coverImage） |
| 正文 | content | 富文本 HTML（用 v-html 渲染） |
| 底部 | — | 可以放"上一篇 / 下一篇"（可选，后端没提供专门接口，可以根据当前分类查前后一条） |

**用户操作流程**
```
进页面 → GET /api/knowledge/{id} → 渲染全文
后端 GET 时会自动 viewCount +1（后端实现的话）
```

---

### 3.3.3 发布知识 KnowledgeCreate.vue

**路由**: `/knowledge/create`  
**权限**: 需登录  
**功能**: 专家和管理员可发布知识文章（农民也可以写，但审核后才能发布）

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 知识分类 | GET | `/api/category/2` | 分类下拉 |
| 发布知识 | POST | `/api/knowledge` | |

**请求参数 (POST)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | string | 是 | 标题 |
| categoryId | long | 是 | 分类（type=2 的 CategorySelect） |
| coverImage | string | 是 | 封面图URL（ImageUpload） |
| summary | string | 否 | 摘要，限 200 字 |
| content | string | 是 | 正文 HTML（wangEditor 富文本） |

**用户操作流程**
```
进页面 → GET /api/category/2 填分类
用 wangEditor 编辑正文 → ImageUpload 传封面
提交 → POST /api/knowledge
  → 成功: ElMessage.success → router.push('/knowledge')
```

---

### 3.3.4 我的知识 KnowledgeMy.vue

**路由**: `/knowledge/my`  
**权限**: 需登录  
**功能**: 当前登录用户自己发布的知识文章 + 编辑 + 删除

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 我的知识列表 | GET | `/api/knowledge/my` | |
| 删除知识 | DELETE | `/api/knowledge/{id}` | |

**列表请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 标题 | title | 点击跳 `/knowledge/{id}` |
| 分类 | categoryName | |
| 浏览量 | viewCount | |
| 发布时间 | createTime | |
| 操作 | — | 删除 |

**用户操作流程**
```
进页面 → GET /api/knowledge/my
点标题 → router.push('/knowledge/' + id)
删 → ElMessageBox.confirm → DELETE → 刷新
```

---

## 四、管理员后台模块

> 管理员后台共用 `AdminLayout.vue`（侧边栏 + 顶栏 + 内容区），A 负责 AppLayout.vue，你新建 AdminLayout.vue 即可。

---

### 3.4.1 管理员登录 AdminLogin.vue

**路由**: `/admin/login`  
**功能**: 管理员独立登录页（其实走的是同一个 `/api/auth/login`，只是 role=3 才允许进后台）

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 登录 | POST | `/api/auth/login` | 同普通登录 |

**逻辑**
```
POST /api/auth/login
  → user.role !== 3: ElMessage.error('该账号无管理员权限'); store.logout()
  → user.role === 3: store.setLogin(...) → router.push('/admin/dashboard')
```

---

### 3.4.2 管理员仪表盘 AdminDashboard.vue

**路由**: `/admin/dashboard`  
**权限**: 需登录且 role=3  
**功能**: 数据看板，各模块统计概览

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 仪表盘数据 | GET | `/api/admin/stats/overview` | 一次性返回全部统计 |

**响应字段（来自响应 data）**

| 卡片位置 | 字段 | 说明 |
|---|---|---|
| 用户总数 | userCount | |
| 专家总数 | expertCount | |
| 商品总数 | productCount | |
| 订单总数 | orderCount | |
| **累计成交金额** | **orderAmount** | **后端字段叫 orderAmount，不是 totalSales** |
| 融资申请数 | financeApplyCount | |
| 融资总金额 | financeAmount | |
| 知识文章数 | knowledgeCount | |
| 最近订单列表 | recentOrders | 数组，展示最近订单 |
| 最近融资申请列表 | recentFinanceApplies | 数组，展示最近融资申请 |

---

### 3.4.3 用户管理 AdminUsers.vue

**路由**: `/admin/users`  
**功能**: 用户列表 + 启停用 + 改角色

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 用户列表 | GET | `/api/admin/users` | |
| 启停用 | PUT | `/api/admin/users/{id}/status` | |
| 改角色 | PUT | `/api/admin/users/{id}/role` | |
| 删除用户 | DELETE | `/api/admin/users/{id}` | |

**请求参数 (GET)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 按用户名/真实姓名搜索 |
| role | int | 否 | 1农民/2专家/3管理员 |
| status | int | 否 | 0禁用/1启用 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 用户名 | username | |
| 真实姓名 | realName | |
| 手机号 | phone | |
| 角色 | role | 1农民/2专家/3管理员（用 ROLE_MAP 常量渲染 el-tag） |
| 状态 | status | 0禁用/1启用 |
| 注册时间 | createTime | |
| 操作 | — | 启用/禁用 / 改角色 / 删除 |

**PUT `/api/admin/users/{id}/status` 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 是 | 0禁用 1启用 |

**PUT `/api/admin/users/{id}/role` 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| role | int | 是 | 1/2/3 |

---

### 3.4.4 专家审核 AdminExperts.vue

**路由**: `/admin/experts`  
**功能**: 专家认证申请审核 + 专家列表

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 专家列表 | GET | `/api/admin/experts` | |
| 审核通过 | PUT | `/api/admin/experts/{id}/approve` | |
| 审核驳回 | PUT | `/api/admin/experts/{id}/reject` | |

**请求参数 (GET)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditStatus | int | 否 | 0待审核/1已通过/2已驳回；空=全部 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 用户 | user.realName | 后端返回嵌套对象 user:{id, username, realName, phone} |
| 专长 | specialty | |
| 职称 | title | |
| 咨询价格 | consultPrice | |
| 审核状态 | auditStatus | 0待审核/1已通过/2已驳回 |
| 审核备注 | auditRemark | 驳回原因 |
| 提交时间 | submitTime | |
| 操作 | — | 待审核→通过/驳回；已通过/已驳回→查看 |

**PUT 驳回请求参数（仅驳回时）**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditRemark | string | 是 | 驳回原因 |

---

### 3.4.5 商品审核 AdminProducts.vue

**路由**: `/admin/products`  
**功能**: 商品列表 + 审核 + 强制下架 + 删除

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 商品列表 | GET | `/api/admin/products` | |
| 审核通过 | PUT | `/api/admin/products/{id}/approve` | |
| 审核驳回 | PUT | `/api/admin/products/{id}/reject` | |
| 强制下架 | PUT | `/api/admin/products/{id}/status` | 卖家违规时强制下架 |
| 删除 | DELETE | `/api/admin/products/{id}` | |

**请求参数 (GET)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 商品名搜索 |
| auditStatus | int | 否 | 0待审核/1已通过/2已驳回 |
| status | int | 否 | 0下架/1上架 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 图片 | image | **后端叫 image，不是 coverImage**（缩略图 60x60） |
| 名称 | name | |
| 价格 | price | |
| 卖家 | seller.realName | 后端返回嵌套对象 seller:{id, realName}，**不是 sellerName 扁平字段** |
| 审核状态 | auditStatus | 0待审核/1已通过/2已驳回 |
| 上架状态 | status | 0下架/1上架 |
| 上架时间 | createTime | |
| 操作 | — | 审核（仅待审核）/ 下架 / 删除 |

> 注意：后端商品列表**不返回 categoryName**，表格里不要显示分类列。

---

### 3.4.6 需求审核 AdminDemands.vue

**路由**: `/admin/demands`  
**功能**: 采购需求列表 + 删除

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 需求列表 | GET | `/api/admin/demands` | |
| 删除 | DELETE | `/api/admin/demands/{id}` | |

**请求参数 (GET)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 需求名搜索 |
| status | int | 否 | 0已关闭/1进行中/2已完成 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 名称 | name | |
| 数量 | quantity | |
| 期望价 | expectedPrice | |
| 买家 | buyer.realName | 后端返回嵌套对象 buyer:{id, realName}，**不是 buyerName 扁平字段** |
| 状态 | status | 0已关闭/1进行中/2已完成 |
| 截止时间 | deadline | |
| 操作 | — | 删除 |

> 注意：后端需求列表**不返回 categoryName**，表格不要显示分类列。

---

### 3.4.7 订单管理 AdminOrders.vue

**路由**: `/admin/orders`  
**功能**: 平台视角，所有订单列表，只读

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 订单列表 | GET | `/api/admin/orders` | |

**请求参数 (GET)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| orderNo | string | 否 | 按订单号搜索 |
| status | int | 否 | 0待支付/1已支付/2已发货/3已完成/4已取消 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 订单号 | orderNo | 点击可跳转对应订单详情（前端可以直接弹详情 el-dialog 或复制） |
| 买家 | buyer.realName | 嵌套对象 buyer:{id, realName}，**不是 buyerName 扁平字段** |
| 卖家 | seller.realName | 嵌套对象 seller:{id, realName}，**不是 sellerName 扁平字段** |
| 金额 | totalAmount | |
| 状态 | status | StatusTag type="order" |
| 下单时间 | createTime | |

> 注意：后端订单管理列表接口**不返回 itemCount（商品件数）** 和 **payTime（支付时间）** 这两个字段。表格里不要显示这两列。如果需要看商品明细，需要调订单详情接口 `/api/order/{orderNo}`。

---

### 3.4.8 融资申请审核 AdminFinances.vue

**路由**: `/admin/finances`  
**功能**: 融资申请列表 + 审核通过 + 驳回

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 融资列表 | GET | `/api/admin/finances` | |
| 审核通过 | PUT | `/api/admin/finances/{id}/approve` | |
| 审核驳回 | PUT | `/api/admin/finances/{id}/reject` | |

**请求参数 (GET)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0待审核/1通过/2拒绝 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 项目名称 | title | |
| 申请人 | user.realName | 嵌套对象 user:{id, realName} |
| 金融产品 | product.bankName + ' ' + product.name | 后端 product:{id, bankName, name, interestRate} 嵌套 |
| 金额 | amount | |
| 状态 | status | StatusTag type="finance" |
| 申请时间 | createTime | |
| 操作 | — | 通过 / 驳回 |

> 注意：后端融资列表**不返回** contactPhone、repayPeriod、productName 这几个字段。表格里只显示上表列出的列。如果要看详细的申请内容和联系方式，可以点"查看详情"弹 dialog 调 `/api/finance/{id}`。

**PUT 驳回请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| reviewRemark | string | 是 | 驳回原因 |

---

### 3.4.9 知识管理 AdminKnowledges.vue

**路由**: `/admin/knowledges`  
**功能**: 知识文章列表 + 上下架 + 删除

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 知识列表 | GET | `/api/admin/knowledges` | |
| 上下架 | PUT | `/api/admin/knowledges/{id}/status` | |
| 删除 | DELETE | `/api/admin/knowledges/{id}` | |

**请求参数 (GET)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 标题搜索 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 封面 | coverImage | 知识封面确实叫 coverImage ✅（缩略图 60x60） |
| 标题 | title | |
| 作者 | author.realName | **后端返回 author:{id, realName} 嵌套对象，不是 authorName 扁平字段** |
| 分类 | categoryName | |
| 浏览量 | viewCount | |
| 发布时间 | createTime | |
| 操作 | — | 上架/下架 / 删除 |

---

### 3.4.10 分类管理 AdminCategories.vue

**路由**: `/admin/categories`  
**功能**: 系统分类增删改查（商品分类 type=1、问答/知识分类 type=2 分开管理）

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 分类列表 | GET | `/api/category/{type}` | type=1 或 2 |
| 新增分类 | POST | `/api/category` | |
| 修改分类 | PUT | `/api/category/{id}` | |
| 删除分类 | DELETE | `/api/category/{id}` | |

**GET `/api/category/{type}` 返回字段（来自 data[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 分类名 | name | |
| 图标 | icon | |
| **排序** | **sort** | **后端字段叫 sort，不是 sortOrder** |
| 类型 | type | 1商品分类 2问答/知识分类 |
| 状态 | status | 0禁用 1启用 |
| 操作 | — | 编辑 / 删除 |

**新增/修改请求参数（POST/PUT `/api/category`）**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 分类名称 |
| icon | string | 否 | 图标URL |
| sort | int | 否 | 排序值，默认 0 |
| type | int | 是 | 1商品 2问答/知识 |
| status | int | 否 | 0禁用 1启用，默认 1 |

---

## 五、后端接口对应总表

| B 的页面 | 调后端接口 | 后端属于 |
|---|---|---|
| ExpertList / ExpertDetail | expert 全部 | 后端B |
| AppointmentCreate / MyUser / MyExpert | appointment 全部 | 后端B |
| QaList / QaCreate / QaDetail / QaMy | qa 全部（含 qa/reply） | 后端B |
| KnowledgeList / Create / Detail / My | knowledge 全部 | 后端C |
| AdminLogin | auth/login | 后端A |
| AdminDashboard | admin/stats/overview | 后端C |
| AdminUsers | admin/users 全部 | 后端A |
| AdminExperts | admin/experts 全部 | 后端A |
| AdminProducts | admin/products 全部 | 后端A |
| AdminDemands | admin/demands 全部 | 后端A |
| AdminOrders | admin/orders | 后端C |
| AdminFinances | admin/finances 全部 | 后端C |
| AdminKnowledges | admin/knowledges 全部 | 后端C |
| AdminCategories | category 全部 + category/{type} | 后端A |