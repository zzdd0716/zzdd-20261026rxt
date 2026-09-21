# 后端 C - 农业知识 + 融资管理

## 负责范围
农业知识模块（普通 + 管理员）
融资管理模块（普通 + 管理员）
数据统计概览（仪表盘）

---

## 认证说明（重要）

本项目使用 **Spring Security + JWT（无状态）** 认证。

- 登录接口 `POST /api/auth/login`（后端 A 负责）用 `AuthenticationManager` 调用 `DbUserDetailsService` 从数据库查用户，BCrypt 校验密码通过后颁发 JWT
- 前端后续请求自动带 `Authorization: Bearer <token>` 请求头
- `JwtAuthenticationFilter` 拦截每个请求，解析 token 后再调用 `DbUserDetailsService` 从数据库查用户（防用户被删/禁用），设置 SecurityContext
- **公开接口**（SecurityConfig 里 `permitAll()`）：知识列表/详情、融资产品列表/匹配、分类查询等
- **需登录接口**（SecurityConfig 里 `authenticated()`）：发布知识、我的知识、融资申请、我的融资等
- **管理员接口**（`@PreAuthorize("hasRole('ADMIN')")`）：全部 `/api/admin/**`（管理员接口已移至后端 A）
- **获取当前用户**：继承 `BaseController` 后调 `getCurrentUserId()` / `getCurrentUser()`，**不要**从请求头手动解析 token

接口下方标注"需登录"即表示必须带有效 JWT 才能访问，标注"公开"即表示任何人都可访问。

---

## 一、农业知识模块

### 1.1 知识列表（公开）

**请求**
```
GET /api/knowledge
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 搜索标题/摘要 |
| categoryId | long | 否 | 知识分类ID |
| pageNum | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页数量，默认 10 |

**请求示例**
```
GET /api/knowledge?keyword=苹果&categoryId=2&pageNum=1&pageSize=10
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "红富士苹果优质栽培技术要点",
        "categoryId": 2,
        "categoryName": "果树栽培",
        "summary": "本文介绍红富士苹果的栽培技术...",
        "coverImage": "/images/knowledge/k1.jpg",
        "viewCount": 1280,
        "likeCount": 56,
        "author": { "id": 5, "realName": "陈农师" },
        "createTime": "2026-09-15 10:00:00"
      }
    ],
    "total": 56,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 6
  }
}
```

---

### 1.2 热门知识 TOP10（公开）

**请求**
```
GET /api/knowledge/hot
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "title": "红富士苹果优质栽培技术要点",
      "coverImage": "/images/knowledge/k1.jpg",
      "viewCount": 1280
    },
    {
      "id": 2,
      "title": "水稻病虫害综合防治方案",
      "coverImage": "/images/knowledge/k2.jpg",
      "viewCount": 980
    }
  ]
}
```

---

### 1.3 知识详情（公开）

**请求**
```
GET /api/knowledge/{id}
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 知识文章ID |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1,
    "title": "红富士苹果优质栽培技术要点",
    "categoryId": 2,
    "categoryName": "果树栽培",
    "coverImage": "/images/knowledge/k1.jpg",
    "content": "<p>红富士苹果是目前市场上最受欢迎的苹果品种之一...</p>",
    "viewCount": 1281,
    "likeCount": 56,
    "liked": false,
    "author": {
      "id": 5,
      "realName": "陈农师",
      "avatar": "/images/expert1.jpg"
    },
    "createTime": "2026-09-15 10:00:00",
    "updateTime": "2026-09-15 10:00:00"
  }
}
```

---

### 1.4 发布知识（专家/管理员）

**请求**
```
POST /api/knowledge
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | string | 是 | 文章标题 |
| categoryId | long | 是 | 知识分类ID |
| coverImage | string | 否 | 封面图片URL |
| summary | string | 否 | 文章摘要 |
| content | string | 是 | 文章内容（支持HTML） |
| status | int | 否 | 0=下架 1=上架，默认 1 |

**请求示例**
```json
{
  "title": "红富士苹果优质栽培技术要点",
  "categoryId": 2,
  "coverImage": "/images/knowledge/k1.jpg",
  "summary": "本文介绍红富士苹果的栽培技术要点，包括选地、施肥、修剪等...",
  "content": "<p>红富士苹果是目前市场上最受欢迎的苹果品种之一...</p>",
  "status": 1
}
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": { "id": 7 }
}
```

---

### 1.5 修改知识（专家/管理员）

**请求**
```
PUT /api/knowledge/{id}
Content-Type: application/json
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 知识文章ID |

**请求参数 (Body)** 同发布，只传要修改的字段。专家只能修改自己的文章（校验 authorId），管理员可修改全部。

**请求示例**
```json
{
  "title": "红富士苹果优质栽培技术要点（修订版）",
  "content": "<p>更新后的内容...</p>"
}
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

---

### 1.6 删除知识（专家/管理员）

**请求**
```
DELETE /api/knowledge/{id}
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 知识文章ID |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

---

### 1.7 点赞/取消点赞（已登录）

**请求**
```
POST /api/knowledge/{id}/like
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 知识文章ID |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "liked": true,
    "likeCount": 57
  }
}
```

---

### 1.8 我的知识（专家/管理员）

**请求**
```
GET /api/knowledge/my
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页数量，默认 10 |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "红富士苹果优质栽培技术要点",
        "categoryId": 2,
        "categoryName": "果树栽培",
        "viewCount": 1281,
        "likeCount": 56,
        "status": 1,
        "createTime": "2026-09-15 10:00:00"
      }
    ],
    "total": 3,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

---

## 二、融资管理模块

### 2.1 金融产品列表（公开）

**请求**
```
GET /api/finance/products
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "bankName": "农业银行",
      "name": "农E贷-种植贷",
      "purpose": "种植,果园,扩建",
      "minAmount": 50000,
      "maxAmount": 3000000,
      "minPeriod": 6,
      "maxPeriod": 36,
      "interestRate": 4.35,
      "description": "专为农业种植户设计的经营性贷款产品"
    },
    {
      "id": 2,
      "bankName": "邮储银行",
      "name": "裕农快贷",
      "purpose": "大棚,蔬菜,设备",
      "minAmount": 10000,
      "maxAmount": 500000,
      "minPeriod": 3,
      "maxPeriod": 24,
      "interestRate": 5.60,
      "description": "快速审批，灵活还款"
    }
  ]
}
```

---

### 2.2 智能匹配金融产品（农民）

**请求**
```
GET /api/finance/match
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| amount | decimal | 否 | 期望融资金额 |
| purpose | string | 否 | 融资用途关键词，如 "果园扩建" |
| repayPeriod | int | 否 | 期望还款期限（月） |

**请求示例**
```
GET /api/finance/match?amount=200000&purpose=果园扩建&repayPeriod=24
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "bankName": "农业银行",
      "name": "农E贷-种植贷",
      "purpose": "种植,果园,扩建",
      "minAmount": 50000,
      "maxAmount": 3000000,
      "minPeriod": 6,
      "maxPeriod": 36,
      "interestRate": 4.35,
      "description": "专为农业种植户设计的经营性贷款产品",
      "matchScore": 75,
      "matchReason": "关键词匹配:种植/果园/扩建，金额符合200000元需求，期限符合24个月需求"
    },
    {
      "id": 3,
      "bankName": "建设银行",
      "name": "农信贷",
      "purpose": "果园,种植,扩建",
      "minAmount": 100000,
      "maxAmount": 1000000,
      "minPeriod": 12,
      "maxPeriod": 48,
      "interestRate": 4.85,
      "description": "面向新型农业经营主体的贷款产品",
      "matchScore": 55,
      "matchReason": "关键词匹配:果园/扩建，金额符合200000元需求"
    }
  ]
}
```

---

### 2.3 提交融资申请（农民）

**请求**
```
POST /api/finance/apply
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productId | long | 否 | 意向金融产品ID |
| title | string | 是 | 融资项目名称 |
| amount | decimal | 是 | 申请金额 |
| purpose | string | 是 | 融资用途 |
| description | string | 否 | 详细说明 |
| repayPeriod | int | 是 | 期望还款期限（月） |
| contactName | string | 是 | 联系人 |
| contactPhone | string | 是 | 联系电话 |

**请求示例**
```json
{
  "productId": 1,
  "title": "红富士果园扩建项目融资",
  "amount": 200000.00,
  "purpose": "果园扩建",
  "description": "计划新增种植面积50亩，引进新品种苗木...",
  "repayPeriod": 24,
  "contactName": "李四",
  "contactPhone": "13800000001"
}
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": { "id": 5 }
}
```

---

### 2.4 我的融资申请列表（农民）

**请求**
```
GET /api/finance/my
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0待审核/1通过/2拒绝 |
| pageNum | int | 否 | 页码，默认 1 |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "红富士果园扩建项目融资",
        "amount": 200000.00,
        "purpose": "果园扩建",
        "repayPeriod": 24,
        "status": 1,
        "reviewRemark": "资料齐全，审批通过",
        "product": { "id": 1, "bankName": "农业银行", "name": "农E贷-种植贷" },
        "createTime": "2026-09-20 10:00:00"
      }
    ],
    "total": 2,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

---

### 2.5 融资申请详情（农民）

**请求**
```
GET /api/finance/{id}
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 融资申请ID |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1,
    "userId": 2,
    "productId": 1,
    "title": "红富士果园扩建项目融资",
    "amount": 200000.00,
    "purpose": "果园扩建",
    "description": "计划新增种植面积50亩，引进新品种苗木...",
    "repayPeriod": 24,
    "contactName": "李四",
    "contactPhone": "13800000001",
    "status": 1,
    "reviewRemark": "资料齐全，审批通过",
    "reviewTime": "2026-09-21 14:00:00",
    "createTime": "2026-09-20 10:00:00",
    "product": {
      "id": 1,
      "bankName": "农业银行",
      "name": "农E贷-种植贷",
      "interestRate": 4.35
    }
  }
}
```

---

### 2.6 修改融资申请（农民）

**请求**
```
PUT /api/finance/{id}
Content-Type: application/json
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 融资申请ID |

**请求参数 (Body)** 同提交，仅待审核（status=0）状态允许修改，校验 userId == 当前用户。

**请求示例**
```json
{
  "amount": 250000.00,
  "description": "计划新增种植面积80亩...",
  "repayPeriod": 36
}
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

---

### 2.7 撤销融资申请（农民）

**请求**
```
DELETE /api/finance/{id}
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 融资申请ID |

**业务校验**：仅待审核（status=0）状态允许撤销。

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

---

## 三、融资管理员接口

### 3.1 融资申请列表（管理员）

**请求**
```
GET /api/admin/finances
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0待审核/1通过/2拒绝 |
| pageNum | int | 否 | 页码，默认 1 |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "红富士果园扩建项目融资",
        "amount": 200000.00,
        "purpose": "果园扩建",
        "status": 1,
        "user": { "id": 2, "realName": "李四" },
        "product": { "id": 1, "bankName": "农业银行", "name": "农E贷-种植贷" },
        "createTime": "2026-09-20 10:00:00"
      }
    ],
    "total": 18,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 2
  }
}
```

---

### 3.2 融资审核通过

**请求**
```
PUT /api/admin/finances/{id}/approve
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 3.3 融资审核驳回

**请求**
```
PUT /api/admin/finances/{id}/reject
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| reviewRemark | String | ⭕ | 驳回原因 |

**请求示例**
```json
{ "reviewRemark": "资料不完整，请补充后重新申请" }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 四、知识管理员接口

### 4.1 知识列表（管理员）

**请求**
```
GET /api/admin/knowledges
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 搜索标题 |
| status | int | 否 | 0下架/1上架 |
| pageNum | int | 否 | 页码，默认 1 |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "红富士苹果优质栽培技术要点",
        "categoryId": 2,
        "viewCount": 1281,
        "likeCount": 56,
        "status": 1,
        "author": { "id": 5, "realName": "陈农师" },
        "createTime": "2026-09-15 10:00:00"
      }
    ],
    "total": 56,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 6
  }
}
```

---

### 4.2 知识上下架

**请求**
```
PUT /api/admin/knowledges/{id}/status
Content-Type: application/json
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 知识文章ID |

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | ✅ | 0=下架 1=上架 |

**请求示例**
```json
{ "status": 0 }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 4.3 删除知识（管理员）

**请求**
```
DELETE /api/admin/knowledges/{id}
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 知识文章ID |

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 五、数据统计概览（管理员仪表盘）

### 5.1 统计概览

**请求**
```
GET /api/admin/stats/overview
```

> 此接口聚合各模块统计数据，实现方式建议：在 Service 层通过方法调用获取各模块统计（product/demand/order/finance/knowledge），返回一次性数据。

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "userCount": 152,
    "expertCount": 23,
    "productCount": 287,
    "demandCount": 45,
    "orderCount": 1203,
    "orderAmount": 856200.50,
    "financeApplyCount": 18,
    "financeAmount": 3500000.00,
    "knowledgeCount": 56,
    "recentOrders": [
      {
        "orderNo": "RX20260920001",
        "buyerName": "王五",
        "totalAmount": 290.00,
        "status": 3,
        "createTime": "2026-09-18 10:28:00"
      }
    ],
    "recentFinanceApplies": [
      {
        "title": "红富士果园扩建项目融资",
        "userName": "李四",
        "amount": 200000.00,
        "status": 1
      }
    ]
  }
}
```

---

## 本次涉及数据库表
`knowledge`（含管理）、`finance_apply`（含管理）、`finance_product`（管理时只读）、`product`（读）、`demand`（读）、`orders`（读）、`order_item`（读）、`user`（只读统计）