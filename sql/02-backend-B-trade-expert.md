# 后端 B - 交易 + 专家模块

## 负责范围
商品货源、采购需求、购物车、订单、专家、在线问答、专家预约

---

## 认证说明（重要）

本项目使用 **Spring Security + JWT（无状态）** 认证。

- 登录接口 `POST /api/auth/login`（后端 A 负责）用 `AuthenticationManager` 调用 `DbUserDetailsService` 从数据库查用户，BCrypt 校验密码通过后颁发 JWT
- 前端后续请求自动带 `Authorization: Bearer <token>` 请求头
- `JwtAuthenticationFilter` 拦截每个请求，解析 token 后再调用 `DbUserDetailsService` 从数据库查用户（防用户被删/禁用），设置 SecurityContext
- **公开接口**（SecurityConfig 里 `permitAll()`）：商品列表/详情、需求列表、专家列表/详情、知识列表/详情、问答列表/详情、融资产品列表/匹配、分类查询、登录注册等
- **需登录接口**（SecurityConfig 里 `authenticated()`）：购物车、订单、我的货源、我的需求、我的问答、预约、我的知识、融资申请等
- **管理员接口**（`@PreAuthorize("hasRole('ADMIN')")`）：全部 `/api/admin/**`
- **获取当前用户**：继承 `BaseController` 后调 `getCurrentUserId()` / `getCurrentUser()`，**不要**从请求头手动解析 token

接口下方标注"需登录"即表示必须带有效 JWT 才能访问，标注"公开"即表示任何人都可访问。

---

## 一、商品/货源

### 1.1 商品列表（公开）

**请求**
```
GET /api/product
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 搜索名称/产地 |
| categoryId | long | 否 | 分类ID |
| origin | string | 否 | 产地 |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

**请求示例**
```
GET /api/product?keyword=苹果&categoryId=1&pageNum=1&pageSize=10
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1, "name": "山东红富士苹果（80mm以上）",
        "categoryId": 1, "categoryName": "水果",
        "price": 5.80, "unit": "斤", "stock": 10000,
        "image": "/images/apple1.jpg", "origin": "山东烟台",
        "createTime": "2026-09-18 10:00:00",
        "seller": { "id": 2, "realName": "李四", "farmName": "烟台果园" }
      }
    ],
    "total": 287, "pageNum": 1, "pageSize": 10, "pages": 29
  }
}
```

---

### 1.2 商品详情（公开）

**请求**
```
GET /api/product/{id}
```

**响应示例**
```json
{
  "code": 200,
  "data": {
    "id": 1, "sellerId": 2,
    "name": "山东红富士苹果（80mm以上）",
    "categoryId": 1, "categoryName": "水果",
    "price": 5.80, "unit": "斤", "stock": 10000,
    "image": "/images/apple1.jpg",
    "images": ["/images/apple1.jpg", "/images/apple2.jpg"],
    "description": "正宗烟台红富士，脆甜多汁...",
    "origin": "山东烟台",
    "auditStatus": 1, "status": 1,
    "seller": {
      "id": 2, "username": "lisi", "realName": "李四",
      "farmName": "烟台果园", "phone": "13800000001"
    },
    "createTime": "2026-09-18 10:00:00"
  }
}
```

---

### 1.3 发布货源（农民）

**请求**
```
POST /api/product
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 商品名称 |
| categoryId | long | 是 | 分类ID |
| price | decimal | 是 | 单价 |
| unit | string | 否 | 单位，默认"斤" |
| stock | int | 是 | 库存数量 |
| image | string | 是 | 主图URL（先调上传接口） |
| images | string | 否 | 多图JSON字符串 |
| description | string | 否 | 商品描述 |
| origin | string | 否 | 产地 |

**请求示例**
```json
{
  "name": "山东红富士苹果（80mm以上）",
  "categoryId": 1,
  "price": 5.80,
  "unit": "斤",
  "stock": 10000,
  "image": "/images/apple1.jpg",
  "images": "[\"/images/apple1.jpg\",\"/images/apple2.jpg\"]",
  "description": "正宗烟台红富士，脆甜多汁...",
  "origin": "山东烟台"
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": { "id": 7 } }
```

---

### 1.4 修改自己的货源（农民）

**请求**
```
PUT /api/product/{id}
Content-Type: application/json
```

**请求参数 (Body)** 同发布，只传要修改的字段。校验 seller_id == 当前用户。

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 1.5 上架/下架（农民）

**请求**
```
PUT /api/product/{id}/status
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 是 | 0=下架 1=上架 |

**请求示例**
```json
{ "status": 0 }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 1.6 删除自己的货源（农民）

**请求**
```
DELETE /api/product/{id}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 1.7 我的货源（农民）

**请求**
```
GET /api/product/my
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditStatus | int | 否 | 0待审核/1通过/2驳回 |
| status | int | 否 | 0下架/1上架 |
| pageNum | int | 否 | 默认 1 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1, "name": "山东红富士苹果", "price": 5.80, "stock": 10000,
        "auditStatus": 1, "auditRemark": null, "status": 1,
        "createTime": "2026-09-18 10:00:00"
      }
    ],
    "total": 3, "pageNum": 1, "pageSize": 10, "pages": 1
  }
}
```

---

## 二、采购需求

### 2.1 需求列表（公开）

**请求**
```
GET /api/demand
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 搜索名称/描述 |
| categoryId | long | 否 | 分类筛选 |
| status | int | 否 | 0已关闭/1进行中/2已完成 |
| pageNum | int | 否 | 默认 1 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1, "buyerId": 3, "name": "需要苹果纸箱",
        "quantity": 5000, "unit": "个", "expectedPrice": 2.50,
        "deadline": "2026-10-01 12:00:00", "status": 1,
        "buyer": { "id": 3, "realName": "王五", "farmName": "五常米业" }
      }
    ],
    "total": 45, "pageNum": 1, "pageSize": 10, "pages": 5
  }
}
```

---

### 2.2 需求详情（公开）

**请求**
```
GET /api/demand/{id}
```

**响应示例**
```json
{
  "code": 200,
  "data": {
    "id": 1, "buyerId": 3,
    "name": "需要苹果纸箱", "description": "高质量苹果专用纸箱，需印刷",
    "quantity": 5000, "unit": "个", "expectedPrice": 2.50,
    "categoryId": 1, "deadline": "2026-10-01 12:00:00", "status": 1,
    "buyer": { "id": 3, "realName": "王五", "farmName": "五常米业", "phone": "13800000002" },
    "createTime": "2026-09-10 10:00:00"
  }
}
```

---

### 2.3 发布需求

**请求**
```
POST /api/demand
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 需求商品名称 |
| categoryId | long | 是 | 分类ID |
| quantity | int | 是 | 需求数量 |
| unit | string | 否 | 单位，默认"斤" |
| expectedPrice | decimal | 否 | 期望单价 |
| description | string | 否 | 详细要求 |
| deadline | string | 否 | 截止日期，格式 "2026-10-01 12:00:00" |

**请求示例**
```json
{
  "name": "需要苹果纸箱",
  "categoryId": 1,
  "quantity": 5000,
  "unit": "个",
  "expectedPrice": 2.50,
  "description": "高质量苹果专用纸箱，需印刷",
  "deadline": "2026-10-01 12:00:00"
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": { "id": 4 } }
```

---

### 2.4 修改需求

**请求**
```
PUT /api/demand/{id}
Content-Type: application/json
```

**请求参数 (Body)** 同发布，校验 buyer_id == 当前用户。

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 2.5 删除需求

**请求**
```
DELETE /api/demand/{id}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 2.6 我的需求

**请求**
```
GET /api/demand/my
```

**响应示例**
```json
{
  "code": 200,
  "data": [
    { "id": 1, "name": "需要苹果纸箱", "quantity": 5000, "status": 1, "deadline": "..." }
  ]
}
```

---

## 三、购物车

### 3.1 购物车列表

**请求**
```
GET /api/cart
```

**响应示例**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1, "productId": 1, "quantity": 50, "selected": 1,
      "createTime": "2026-09-19 10:00:00",
      "product": {
        "name": "山东红富士苹果", "price": 5.80,
        "image": "/images/apple1.jpg", "stock": 10000
      }
    }
  ]
}
```

---

### 3.2 加入购物车

**请求**
```
POST /api/cart
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productId | long | 是 | 商品ID（必须上架） |
| quantity | int | 是 | 数量，>0 且不超库存 |

**请求示例**
```json
{ "productId": 1, "quantity": 50 }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 3.3 修改数量

**请求**
```
PUT /api/cart/{id}
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| quantity | int | 是 | 新数量 |

**请求示例**
```json
{ "quantity": 100 }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 3.4 删除购物车项

**请求**
```
DELETE /api/cart/{id}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 3.5 批量勾选

**请求**
```
PUT /api/cart/selected
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| ids | array | 是 | 购物车项ID数组 |
| selected | int | 是 | 0=取消 1=勾选 |

**请求示例**
```json
{ "ids": [1, 2, 3], "selected": 1 }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 四、订单

### 4.1 创建订单

**请求**
```
POST /api/order
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| addressId | long | 是 | 收货地址ID（必须属于当前用户） |
| cartIds | array | 是 | 购物车项ID数组（已选中状态） |
| remark | string | 否 | 订单备注 |

**请求示例**
```json
{
  "addressId": 3,
  "cartIds": [1, 2],
  "remark": "尽快发货"
}
```

**响应示例**（不同卖家自动拆成多单）
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "orderNos": ["RX20260920001", "RX20260920002"],
    "totalAmount": 730.00
  }
}
```

---

### 4.2 我的采购订单（买家视角）

**请求**
```
GET /api/order/purchase
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0待支付/1已支付/2已发货/3已完成/4已取消 |
| pageNum | int | 否 | 默认 1 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1, "orderNo": "RX20260920001",
        "totalAmount": 290.00, "status": 3, "refundStatus": 0,
        "receiver": "王五", "receiverPhone": "13800000002",
        "receiverAddress": "黑龙江省哈尔滨市五常市...",
        "seller": { "id": 2, "realName": "李四", "farmName": "烟台果园" },
        "createTime": "2026-09-18 10:30:00"
      }
    ],
    "total": 10, "pageNum": 1, "pageSize": 10, "pages": 1
  }
}
```

---

### 4.3 我的销售订单（卖家视角，农民）

**请求**
```
GET /api/order/sale
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 筛选 |
| pageNum | int | 否 | 默认 1 |

**响应示例** 同采购订单，但 sellerId = 当前用户，返回买家信息。

---

### 4.4 订单详情

**请求**
```
GET /api/order/{id}
```

**响应示例**
```json
{
  "code": 200,
  "data": {
    "id": 1, "orderNo": "RX20260920001",
    "buyerId": 3, "sellerId": 2, "remark": "尽快发货",
    "totalAmount": 290.00, "status": 3,
    "refundStatus": 0, "refundRemark": null,
    "receiver": "王五", "receiverPhone": "13800000002",
    "receiverAddress": "黑龙江省哈尔滨市五常市...",
    "payTime": "2026-09-18 10:30:00",
    "shipTime": "2026-09-18 14:00:00",
    "finishTime": "2026-09-20 09:00:00",
    "createTime": "2026-09-18 10:28:00",
    "items": [
      {
        "id": 1, "productId": 1,
        "productName": "山东红富士苹果（80mm以上）",
        "productImage": "/images/apple1.jpg",
        "price": 5.80, "quantity": 50, "subtotal": 290.00
      }
    ]
  }
}
```

---

### 4.5 模拟支付（买家）

**请求**
```
PUT /api/order/{id}/pay
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 4.6 卖家发货（农民）

**请求**
```
PUT /api/order/{id}/ship
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 4.7 买家确认收货

**请求**
```
PUT /api/order/{id}/finish
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 4.8 取消订单

**请求**
```
PUT /api/order/{id}/cancel
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 4.9 申请退款

**请求**
```
POST /api/order/{id}/refund
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| refundRemark | string | 否 | 退款原因 |

**请求示例**
```json
{ "refundRemark": "商品质量问题" }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 五、专家模块

### 5.1 专家列表（公开）

**请求**
```
GET /api/expert
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 搜索专长/姓名 |
| pageNum | int | 否 | 默认 1 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 5, "userId": 5, "realName": "陈农师",
        "specialty": "水稻、小麦", "title": "高级农艺师",
        "rating": 4.90, "consultCount": 128,
        "consultPrice": 100.00, "avatar": "/images/expert1.jpg"
      }
    ],
    "total": 23, "pageNum": 1, "pageSize": 10, "pages": 3
  }
}
```

---

### 5.2 专家详情（公开）

**请求**
```
GET /api/expert/{id}
```

**响应示例**
```json
{
  "code": 200,
  "data": {
    "id": 5, "userId": 5,
    "realName": "陈农师", "avatar": "/images/xxx.jpg", "phone": "13811111111",
    "specialty": "水稻、小麦", "title": "高级农艺师",
    "introduction": "从事粮食作物研究30年...",
    "consultPrice": 100.00, "rating": 4.90, "consultCount": 128,
    "availableTimes": [
      { "date": "2026-09-25", "busySlots": ["14:00-14:30"] },
      { "date": "2026-09-26", "busySlots": ["10:00-11:00"] }
    ]
  }
}
```

---

### 5.3 专家自己的资料

**请求**
```
GET /api/expert/profile
```

**响应示例**
```json
{
  "code": 200,
  "data": {
    "specialty": "水稻、小麦", "title": "高级农艺师",
    "introduction": "...", "consultPrice": 100.00,
    "rating": 4.90, "consultCount": 128,
    "auditStatus": 1, "auditRemark": null
  }
}
```

---

### 5.4 专家完善自己的资料

**请求**
```
PUT /api/expert/profile
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| specialty | string | 是 | 专长领域 |
| title | string | 否 | 职称 |
| introduction | string | 否 | 个人简介 |
| consultPrice | decimal | 是 | 咨询价格，默认 0 |

**请求示例**
```json
{
  "specialty": "水稻、小麦、病虫害防治",
  "title": "高级农艺师",
  "introduction": "从事粮食作物研究30年，擅长...",
  "consultPrice": 120.00
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 5.5 专家可预约时间（公开）

返回专家可预约的日期列表，以及每个日期下可用的时间段。后端需结合专家排班设置 + 已有预约冲突检测，算出未来 14 天内每天哪些时间段可约。

**请求**
```
GET /api/expert/{id}/available-time
```

**响应示例**
```json
{
  "code": 200,
  "data": [
    {
      "date": "2026-09-25",
      "slots": ["09:00-09:30", "09:30-10:00", "14:00-14:30", "14:30-15:00"]
    },
    {
      "date": "2026-09-26",
      "slots": ["10:00-10:30", "10:30-11:00", "15:00-15:30"]
    }
  ]
}
```

---

## 六、在线问答

### 6.1 问答列表（公开）

**请求**
```
GET /api/qa
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 搜索标题/内容 |
| categoryId | long | 否 | 问题分类 |
| status | int | 否 | 0待回答/1已回答/2已关闭 |
| pageNum | int | 否 | 默认 1 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1, "title": "红富士苹果采后如何保鲜？",
        "categoryId": 1, "status": 1,
        "viewCount": 234, "replyCount": 3,
        "questioner": { "id": 2, "realName": "李四" },
        "expert": { "id": 5, "realName": "陈农师" },
        "createTime": "2026-09-15 10:00:00"
      }
    ],
    "total": 20, "pageNum": 1, "pageSize": 10, "pages": 2
  }
}
```

---

### 6.2 问答详情（公开）

**请求**
```
GET /api/qa/{id}
```

**响应示例**
```json
{
  "code": 200,
  "data": {
    "id": 1, "title": "红富士苹果采后如何保鲜？",
    "content": "我家果园刚摘了一批红富士，想问下怎么保存能放得久一些？",
    "images": null, "categoryId": 1, "status": 1,
    "viewCount": 235, "replyCount": 3,
    "questioner": { "id": 2, "realName": "李四", "avatar": "/..." },
    "expert": { "id": 5, "realName": "陈农师", "avatar": "/..." },
    "createTime": "2026-09-15 10:00:00",
    "replies": [
      {
        "id": 1, "content": "红富士苹果适宜冷藏温度0~1℃...", "isExpert": 1,
        "replier": { "id": 5, "realName": "陈农师" },
        "createTime": "2026-09-15 14:00:00"
      }
    ]
  }
}
```

---

### 6.3 发起提问

**请求**
```
POST /api/qa
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | string | 是 | 问题标题 |
| content | string | 是 | 问题内容 |
| categoryId | long | 是 | 问题分类 |
| expertId | long | 否 | 指定专家ID（不传=公开） |
| images | string | 否 | 图片JSON字符串 |

**请求示例**
```json
{
  "title": "红富士苹果采后如何保鲜？",
  "content": "我家果园刚摘了一批红富士...",
  "categoryId": 1,
  "expertId": 5
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": { "id": 4 } }
```

---

### 6.4 修改自己的问题

**请求**
```
PUT /api/qa/{id}
Content-Type: application/json
```

**请求参数 (Body)** 同发起提问，校验 questioner_id。

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 6.5 删除自己的问题

**请求**
```
DELETE /api/qa/{id}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 6.6 回答问题

**请求**
```
POST /api/qa/{id}/reply
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| content | string | 是 | 回答内容 |

**请求示例**
```json
{ "content": "红富士苹果适宜冷藏温度0~1℃，湿度90%~95%..." }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 6.7 我的提问

**请求**
```
GET /api/qa/my/question
```

**响应示例**
```json
{
  "code": 200,
  "data": [
    { "id": 1, "title": "红富士苹果采后如何保鲜？", "status": 1, "replyCount": 3, "createTime": "..." }
  ]
}
```

---

### 6.8 我的回答

**请求**
```
GET /api/qa/my/reply
```

**响应示例**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1, "content": "红富士苹果适宜冷藏温度0~1℃...",
      "qa": { "id": 1, "title": "红富士苹果采后如何保鲜？" },
      "createTime": "..."
    }
  ]
}
```

---

### 6.9 删除自己的回答

**请求**
```
DELETE /api/qa/reply/{id}
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 回复ID |

**业务校验**：校验 replier_id == 当前登录用户 ID，只能删除自己的回答。

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 七、专家预约

### 7.1 创建预约

**请求**
```
POST /api/appointment
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| expertId | long | 是 | 专家ID（audit_status=1） |
| appointmentTime | string | 是 | "2026-09-25 14:00:00" |
| duration | int | 否 | 分钟，默认 30 |
| topic | string | 否 | 咨询主题 |

**请求示例**
```json
{
  "expertId": 5,
  "appointmentTime": "2026-09-25 14:00:00",
  "duration": 30,
  "topic": "苹果病虫害防治咨询"
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": { "id": 3 } }
```

**业务校验**：同一专家同一时间段不能重复预约（判断 [已开始, 已结束] 与 [新开始, 新结束] 是否有重叠）。

---

### 7.2 用户端：我的预约

**请求**
```
GET /api/appointment/my/user
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0待确认/1已确认/2已完成/3已取消 |

**响应示例**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1, "topic": "苹果病虫害防治咨询",
      "appointmentTime": "2026-09-25 14:00:00", "duration": 30,
      "amount": 100.00, "status": 1,
      "expert": { "id": 5, "realName": "陈农师", "specialty": "水稻、果树" },
      "createTime": "2026-09-20 10:00:00"
    }
  ]
}
```

---

### 7.3 专家端：待我处理的预约

**请求**
```
GET /api/appointment/my/expert
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 不传返回全部 |

**响应示例** 同用户端，但 expert_id = 当前专家。

---

### 7.4 专家确认预约

**请求**
```
PUT /api/appointment/{id}/confirm
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 7.5 专家标记完成预约

**请求**
```
PUT /api/appointment/{id}/complete
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 7.6 取消预约

**请求**
```
PUT /api/appointment/{id}/cancel
```

**业务校验**：仅待确认(0)状态允许取消。

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 本次涉及数据库表
`product`、`demand`、`cart`、`orders`、`order_item`、`qa`、`qa_reply`、`appointment`、`expert`（读）、`user`（读）、`address`（读）