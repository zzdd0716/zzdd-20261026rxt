# 融销通 - 接口请求/响应字段详细规格

## 字段说明约定
| 标记 | 含义 |
|---|---|
| **✅ 必填** | 该字段必须传入 |
| ⭕ 选填 | 可传可不传，有默认值 |
| ❌ 不传 | 后端自动填充，或响应才返回 |
| 🔒 只读 | 响应字段，前端不能改 |

---

## 1. 认证模块 `/api/auth`

### POST /api/auth/register 用户注册

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | String(50) | ✅ | 用户名，唯一，不能重复 |
| password | String(100) | ✅ | 密码，建议 6-20 位 |
| realName | String(50) | ✅ | 真实姓名 |
| phone | String(20) | ✅ | 手机号，格式校验 11 位数字 |
| role | Integer | ✅ | 1=农民 2=专家（管理员 role=3 不开放注册） |
| email | String(100) | ⭕ | 邮箱 |
| farmName | String(100) | ⭕ | 农场/合作社名称（role=1 农民才填） |

**登录接口 POST /api/auth/login**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | String | ✅ | 用户名 |
| password | String | ✅ | 密码（明文传输，后端用 BCrypt 加密存储） |

**登录响应 data：**
```json
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": 1,
    "username": "admin",
    "realName": "超级管理员",
    "role": 3,
    "avatar": "/images/xxx.jpg",
    "phone": "13800000000"
  }
}
```

---

## 2. 用户模块 `/api/user`

### GET /api/user/info 获取当前用户信息

**响应 data（根据角色不同返回不同扩展字段）：**

| 字段 | 类型 | 只读 | 说明 |
|---|---|---|---|
| id | Long | 🔒 | 用户ID |
| username | String | 🔒 | 用户名 |
| realName | String | 🔒 | 真实姓名 |
| phone | String | 🔒 | 手机号 |
| email | String | 🔒 | 邮箱 |
| avatar | String | 🔒 | 头像URL |
| role | Integer | 🔒 | 角色 1农民/2专家/3管理员 |
| farmName | String | 🔒 | 农场名称（仅农民有） |
| expert | Object | 🔒 | 专家扩展信息（仅 role=2 时返回） |
| expert.specialty | String | 🔒 | 专长领域 |
| expert.title | String | 🔒 | 职称 |
| expert.consultPrice | BigDecimal | 🔒 | 咨询价格 |
| expert.auditStatus | Integer | 🔒 | 审核状态 0待审核/1通过/2驳回 |

### PUT /api/user/info 修改个人信息

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| realName | String(50) | ⭕ | 不传则不改 |
| phone | String(20) | ⭕ | 不传则不改 |
| email | String(100) | ⭕ | 不传则不改 |
| avatar | String(255) | ⭕ | 头像URL（先调上传接口拿URL） |
| farmName | String(100) | ⭕ | 农场名称（农民用） |

### PUT /api/user/password 修改密码

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| oldPassword | String | ✅ | 原密码 |
| newPassword | String | ✅ | 新密码，6-20位 |

### POST /api/user/address 新增地址

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| receiver | String(50) | ✅ | 收货人姓名 |
| phone | String(20) | ✅ | 收货人手机号 |
| province | String(20) | ✅ | 省 |
| city | String(20) | ✅ | 市 |
| district | String(20) | ✅ | 区/县 |
| detail | String(255) | ✅ | 详细地址 |
| isDefault | Integer | ⭕ | 0=非默认 1=默认，默认0 |

### PUT /api/user/address/{id} 修改地址

和新增一样，id 在路径里。

### POST /api/user/expert/apply 专家提交认证

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| specialty | String(100) | ✅ | 专长领域，如"水稻、果树栽培" |
| title | String(50) | ⭕ | 职称，如"高级农艺师" |
| introduction | Text | ⭕ | 个人简介 |
| consultPrice | BigDecimal | ⭕ | 咨询价格（元/次），默认0 |
| certification | String(255) | ⭕ | 资质证书图片URL |

### GET /api/user/expert/audit 专家查看审核状态

**响应 data：**
```json
{
  "auditStatus": 0,          // 0待审核 1通过 2驳回
  "auditRemark": null,       // 驳回时才有
  "submitTime": "2026-09-20 10:00:00"
}
```

---

## 3. 分类模块 `/api/category`

### GET /api/category/{type} 查询分类列表（公开）

**路径参数 type：1=商品分类 2=知识分类 3=问题分类**

**响应 data 列表项：**
```json
{
  "id": 1,
  "name": "水果",
  "type": 1,
  "icon": "/images/cat1.jpg",
  "sort": 1
}
```

### POST /api/category 新增分类（管理员）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | String(50) | ✅ | 分类名 |
| type | Integer | ✅ | 1/2/3 |
| icon | String(255) | ⭕ | 图标URL |
| sort | Integer | ⭕ | 排序，数字越小越靠前，默认0 |
| status | Integer | ⭕ | 0=禁用 1=启用，默认1 |

---

## 4. 文件上传 `/api/common`

### POST /api/common/upload 单文件上传

**Content-Type: multipart/form-data**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| file | MultipartFile | ✅ | 图片文件，支持 jpg/png/jpeg/gif/webp，最大 10MB |

**响应 data：**
```json
{ "url": "/images/20260920/uuid_abc123.jpg" }
```

### POST /api/common/upload/multi 多文件上传

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| files | MultipartFile[] | ✅ | 多文件数组 |

**响应 data：**
```json
[{ "url": "/images/xxx1.jpg" }, { "url": "/images/xxx2.jpg" }]
```

---

## 5. 商品模块 `/api/product`

### GET /api/product 商品列表（公开）

**Query 参数：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | ⭕ | 搜索商品名称/产地 |
| categoryId | Long | ⭕ | 分类ID筛选 |
| origin | String | ⭕ | 产地筛选 |
| pageNum | Integer | ⭕ | 页码，默认 1 |
| pageSize | Integer | ⭕ | 每页数量，默认 10 |

**响应 data（分页格式）：**
```json
{
  "list": [
    {
      "id": 1,
      "name": "山东红富士苹果",
      "categoryId": 1,
      "categoryName": "水果",
      "price": 5.80,
      "unit": "斤",
      "stock": 10000,
      "image": "/images/apple1.jpg",
      "origin": "山东烟台",
      "createTime": "2026-09-18 10:00:00",
      "seller": { "id": 2, "realName": "李四", "farmName": "烟台果园" }
    }
  ],
  "total": 287, "pageNum": 1, "pageSize": 10, "pages": 29
}
```

### GET /api/product/{id} 商品详情（公开）

**响应 data（含完整信息）：**
```json
{
  "id": 1,
  "sellerId": 2,
  "name": "山东红富士苹果（80mm以上）",
  "categoryId": 1,
  "categoryName": "水果",
  "price": 5.80,
  "unit": "斤",
  "stock": 10000,
  "image": "/images/apple1.jpg",
  "images": ["/images/apple1.jpg", "/images/apple2.jpg"],
  "description": "正宗烟台红富士，脆甜多汁...",
  "origin": "山东烟台",
  "auditStatus": 1,
  "status": 1,
  "seller": { "id": 2, "username": "lisi", "realName": "李四", "farmName": "烟台果园", "phone": "13800000001" },
  "createTime": "2026-09-18 10:00:00"
}
```

### POST /api/product 发布货源（农民）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | String(100) | ✅ | 商品名称 |
| categoryId | Long | ✅ | 分类ID |
| price | BigDecimal(10,2) | ✅ | 单价 |
| unit | String(20) | ⭕ | 单位：斤/吨/箱/袋，默认"斤" |
| stock | Integer | ✅ | 库存数量 |
| image | String(255) | ✅ | 主图URL |
| images | String(TEXT) | ⭕ | 多图JSON数组，如 '["/a.jpg","/b.jpg"]' |
| description | Text | ⭕ | 商品描述 |
| origin | String(100) | ⭕ | 产地 |

### PUT /api/product/{id} 修改自己的货源（农民）

和发布一样的字段，只传要修改的字段即可，不传的字段保持不变。

### PUT /api/product/{id}/status 上下架

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | Integer | ✅ | 0=下架 1=上架 |

### GET /api/product/my 我的货源（农民）

**Query 参数：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditStatus | Integer | ⭕ | 0待审核/1通过/2驳回，不传返回全部 |
| status | Integer | ⭕ | 0下架/1上架，不传返回全部 |
| pageNum | Integer | ⭕ | 页码，默认 1 |

**响应列表项（比公开列表多 auditStatus 字段）：**
```json
{
  "id": 1, "name": "...", "price": 5.80, "stock": 10000,
  "auditStatus": 1,    // 🔒 审核状态
  "auditRemark": null,  // 🔒 驳回原因（如有）
  "status": 1,         // 🔒 上下架状态
  "createTime": "..."
}
```

---

## 6. 采购需求模块 `/api/demand`

### GET /api/demand 需求列表（公开）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | ⭕ | 搜索需求名称/描述 |
| categoryId | Long | ⭕ | 分类筛选 |
| status | Integer | ⭕ | 0已关闭/1进行中/2已完成 |
| pageNum | Integer | ⭕ | 默认 1 |

**响应列表项：**
```json
{
  "id": 1, "buyerId": 3, "name": "需要苹果纸箱", "categoryId": 1,
  "quantity": 5000, "unit": "个", "expectedPrice": 2.50,
  "deadline": "2026-10-01 12:00:00", "status": 1,
  "buyer": { "id": 3, "realName": "王五", "farmName": "五常米业" }
}
```

### POST /api/demand 发布需求

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | String(100) | ✅ | 需求商品名称 |
| categoryId | Long | ✅ | 分类ID |
| quantity | Integer | ✅ | 需求数量 |
| unit | String(20) | ⭕ | 单位，默认"斤" |
| expectedPrice | BigDecimal(10,2) | ⭕ | 期望单价 |
| description | Text | ⭕ | 详细要求 |
| deadline | DateTime | ⭕ | 截止日期，格式 "2026-10-01 12:00:00" |

---

## 7. 购物车模块 `/api/cart`

### GET /api/cart 购物车列表（已登录）

**响应 data 列表项（含商品信息）：**
```json
{
  "id": 1,
  "productId": 1,
  "quantity": 50,
  "selected": 1,          // 🔒 0未选中 1选中
  "createTime": "2026-09-19 10:00:00",
  "product": {            // 🔒 关联商品信息
    "name": "山东红富士苹果",
    "price": 5.80,
    "image": "/images/apple1.jpg",
    "stock": 10000
  }
}
```

### POST /api/cart 加入购物车

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productId | Long | ✅ | 商品ID，必须是上架状态 |
| quantity | Integer | ✅ | 数量，必须 > 0，不能超过库存 |

### PUT /api/cart/{id} 修改数量

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| quantity | Integer | ✅ | 新数量 |

### PUT /api/cart/selected 批量勾选

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| ids | List\<Long\> | ✅ | 要操作的购物车项ID数组 |
| selected | Integer | ✅ | 0=取消勾选 1=勾选 |

---

## 8. 订单模块 `/api/order`

### POST /api/order 创建订单（已登录）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| addressId | Long | ✅ | 收货地址ID（必须属于当前用户） |
| cartIds | List\<Long\> | ✅ | 从购物车选中的商品ID数组（必须是已选中状态） |
| remark | String(255) | ⭕ | 订单备注 |

**注意：不同卖家的商品会自动拆成多个订单返回**

**响应 data：**
```json
{
  "orderNos": ["RX20260920001", "RX20260920002"],
  "totalAmount": 730.00
}
```

### GET /api/order/purchase 我的采购订单

**Query 参数：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | Integer | ⭕ | 0待支付/1已支付/2已发货/3已完成/4已取消，不传返回全部 |
| pageNum | Integer | ⭕ | 默认 1 |

**响应列表项：**
```json
{
  "id": 1, "orderNo": "RX20260920001", "totalAmount": 290.00,
  "status": 3, "refundStatus": 0,
  "receiver": "王五", "receiverPhone": "13800000002",
  "receiverAddress": "黑龙江省哈尔滨市五常市...",
  "seller": { "id": 2, "realName": "李四", "farmName": "烟台果园" },
  "createTime": "2026-09-18 10:30:00"
}
```

### GET /api/order/sale 我的销售订单（农民）

和采购订单格式一样，但 `sellerId` = 当前用户，返回的是买家信息。

### GET /api/order/{id} 订单详情

**响应 data：**
```json
{
  "id": 1, "orderNo": "RX20260920001",
  "buyerId": 3, "sellerId": 2,
  "totalAmount": 290.00, "remark": "尽快发货",
  "status": 3, "refundStatus": 0, "refundRemark": null,
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
```

### PUT /api/order/{id}/pay 模拟支付（无请求体）

### PUT /api/order/{id}/ship 卖家发货（无请求体）

### PUT /api/order/{id}/finish 买家确认收货（无请求体）

### PUT /api/order/{id}/cancel 取消订单（无请求体）

### POST /api/order/{id}/refund 申请退款

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| refundRemark | String(255) | ⭕ | 退款原因说明 |

---

## 9. 专家模块 `/api/expert`

### GET /api/expert 专家列表（公开）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | ⭕ | 搜索专长/姓名 |
| pageNum | Integer | ⭕ | 默认 1 |

**响应列表项：**
```json
{
  "id": 5,
  "userId": 5,
  "realName": "陈农师",
  "specialty": "水稻、小麦",
  "title": "高级农艺师",
  "rating": 4.90,
  "consultCount": 128,
  "consultPrice": 100.00,
  "avatar": "/images/expert1.jpg"
}
```

### GET /api/expert/{id} 专家详情

**响应 data：**
```json
{
  "id": 5, "userId": 5,
  "realName": "陈农师", "avatar": "/images/xxx.jpg",
  "phone": "13811111111",
  "specialty": "水稻、小麦",
  "title": "高级农艺师",
  "introduction": "从事粮食作物研究30年...",
  "consultPrice": 100.00,
  "rating": 4.90, "consultCount": 128,
  "availableTimes": [
    { "date": "2026-09-25", "busySlots": ["14:00-14:30"] },
    { "date": "2026-09-26", "busySlots": ["10:00-11:00"] }
  ]
}
```

### PUT /api/expert/profile 专家完善自己的资料

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| specialty | String(100) | ✅ | 专长领域 |
| title | String(50) | ⭕ | 职称 |
| introduction | Text | ⭕ | 个人简介 |
| consultPrice | BigDecimal | ✅ | 咨询价格，默认0 |

---

## 10. 在线问答模块 `/api/qa`

### GET /api/qa 问答列表（公开）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | ⭕ | 搜索标题/内容 |
| categoryId | Long | ⭕ | 问题分类筛选 |
| status | Integer | ⭕ | 0待回答/1已回答/2已关闭 |
| pageNum | Integer | ⭕ | 默认 1 |

**响应列表项：**
```json
{
  "id": 1, "title": "红富士苹果采后如何保鲜？",
  "categoryId": 1, "status": 1,
  "viewCount": 234, "replyCount": 3,
  "questioner": { "id": 2, "realName": "李四" },
  "expert": { "id": 5, "realName": "陈农师" },   // 指定了专家才返回
  "createTime": "2026-09-15 10:00:00"
}
```

### GET /api/qa/{id} 问答详情

**响应 data：**
```json
{
  "id": 1, "title": "...", "content": "...", "images": ["..."],
  "categoryId": 1, "status": 1,
  "questioner": { "id": 2, "realName": "李四", "avatar": "/..." },
  "expert": { "id": 5, "realName": "陈农师", "avatar": "/..." },
  "viewCount": 235, "replyCount": 3,
  "createTime": "...",
  "replies": [
    {
      "id": 1, "content": "...", "isExpert": 1,
      "replier": { "id": 5, "realName": "陈农师" },
      "createTime": "..."
    }
  ]
}
```

### POST /api/qa 发起提问

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | String(200) | ✅ | 问题标题 |
| content | Text | ✅ | 问题内容 |
| categoryId | Long | ✅ | 问题分类ID |
| expertId | Long | ⭕ | 指定专家ID，不传=公开提问 |
| images | String(TEXT) | ⭕ | 图片JSON数组 |

### POST /api/qa/{id}/reply 回答问题

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| content | Text | ✅ | 回答内容 |

---

## 11. 专家预约模块 `/api/appointment`

### POST /api/appointment 创建预约

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| expertId | Long | ✅ | 专家ID（必须 audit_status=1） |
| appointmentTime | String | ✅ | 预约开始时间，格式 "2026-09-25 14:00:00" |
| duration | Integer | ⭕ | 咨询时长（分钟），默认 30 |
| topic | String(200) | ⭕ | 咨询主题 |

### GET /api/appointment/my/user 用户端我的预约

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | Integer | ⭕ | 0待确认/1已确认/2已完成/3已取消，不传返回全部 |
| pageNum | Integer | ⭕ | 默认 1 |

**响应列表项：**
```json
{
  "id": 1, "expertId": 5, "topic": "苹果病虫害防治咨询",
  "appointmentTime": "2026-09-25 14:00:00", "duration": 30,
  "amount": 100.00, "status": 1,
  "expert": { "id": 5, "realName": "陈农师", "specialty": "水稻、果树" },
  "createTime": "2026-09-20 10:00:00"
}
```

### PUT /api/appointment/{id}/confirm 专家确认（无请求体）
### PUT /api/appointment/{id}/complete 专家完成（无请求体）
### PUT /api/appointment/{id}/cancel 用户取消（无请求体）

---

## 12. 农业知识模块 `/api/knowledge`

### GET /api/knowledge 知识列表（公开）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | ⭕ | 搜索标题/摘要 |
| categoryId | Long | ⭕ | 知识分类筛选 |
| pageNum | Integer | ⭕ | 默认 1 |

**响应列表项：**
```json
{
  "id": 1, "title": "水稻高产栽培十大要点",
  "categoryId": 1, "summary": "从选种到收获...",
  "coverImage": "/images/know1.jpg",
  "viewCount": 1200, "likeCount": 85,
  "author": { "id": 5, "realName": "陈农师", "role": 2 },
  "createTime": "2026-08-01 10:00:00"
}
```

### GET /api/knowledge/hot 热门TOP10（公开）

无参数，按 viewCount desc 返回前 10 条。

### GET /api/knowledge/{id} 知识详情

**响应 data：**
```json
{
  "id": 1, "title": "...", "summary": "...",
  "categoryId": 1, "coverImage": "...",
  "content": "完整富文本内容",
  "viewCount": 1201, "likeCount": 85,
  "author": { "id": 5, "realName": "陈农师", "role": 2 },
  "createTime": "...", "updateTime": "..."
}
```

### POST /api/knowledge 发布知识（专家/管理员）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | String(200) | ✅ | 文章标题 |
| categoryId | Long | ✅ | 知识分类ID |
| summary | String(500) | ✅ | 文章摘要 |
| content | Text | ✅ | 正文（富文本 HTML） |
| coverImage | String(255) | ⭕ | 封面图URL |

### PUT /api/knowledge/{id} 修改知识

和发布一样，只传要修改的字段。

### POST /api/knowledge/{id}/like 点赞/取消（无请求体）

**响应 data：**
```json
{ "likeCount": 86, "liked": true }   // liked=true 表示刚点了赞
```

---

## 13. 融资模块 `/api/finance`

### GET /api/finance/products 模拟金融产品列表（公开）

**响应 data 列表项：**
```json
{
  "id": 1,
  "bankName": "中国农业银行",
  "name": "农E贷-种植贷",
  "purpose": "种植,果园,扩建,化肥,农药",
  "minAmount": 50000.00,
  "maxAmount": 3000000.00,
  "minPeriod": 6, "maxPeriod": 36,
  "interestRate": 0.0435,
  "description": "面向种植户的专项贷款..."
}
```

### POST /api/finance/apply 提交融资申请（农民）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | String(200) | ✅ | 融资标题 |
| amount | BigDecimal(15,2) | ✅ | 融资金额 |
| purpose | String(500) | ✅ | 融资用途，如"果园扩建" |
| description | Text | ⭕ | 详细说明 |
| repayPeriod | Integer | ⭕ | 还款期限（月） |
| contactName | String(50) | ✅ | 联系人姓名 |
| contactPhone | String(20) | ✅ | 联系人手机号 |
| productId | Long | ⭕ | 关联商品ID（如果有对应商品） |

### GET /api/finance/match 智能匹配推荐（农民）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| amount | BigDecimal | ⭕ | 金额，不传则用最近申请的金额 |
| purpose | String | ⭕ | 用途关键词 |
| repayPeriod | Integer | ⭕ | 期限（月） |

**响应 data：**
```json
{
  "products": [
    {
      "id": 1, "bankName": "中国农业银行", "name": "农E贷-种植贷",
      "minAmount": 50000, "maxAmount": 3000000,
      "interestRate": 0.0435,
      "matchScore": 75,
      "matchReason": "关键词匹配:种植/果园/扩建，金额20万元符合[5万,300万]区间，期限24月符合[6,36]月范围"
    },
    {
      "id": 2, "bankName": "中国邮政储蓄银行", "name": "农贷通",
      "matchScore": 30,
      "matchReason": "金额符合，期限符合，但用途关键词无匹配"
    }
  ]
}
```

### PUT /api/finance/{id} 修改融资申请（仅 status=0 时）

和提交时的字段一样，只传要改的。

---

## 14. 管理员模块 `/api/admin`

### GET /api/admin/users 用户列表

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| role | Integer | ⭕ | 1农民/2专家/3管理员，不传返回全部 |
| keyword | String | ⭕ | 模糊搜索 username/realName/phone |
| pageNum | Integer | ⭕ | 默认 1 |

### PUT /api/admin/users/{id}/status 启用禁用

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | Integer | ✅ | 0=禁用 1=启用 |

### DELETE /api/admin/users/{id} 删除用户（软删除，无请求体）

### GET /api/admin/experts 专家列表（含审核）

**响应列表项：**
```json
{
  "expertId": 1, "userId": 5,
  "realName": "陈农师", "phone": "13811111111",
  "specialty": "水稻、小麦", "title": "高级农艺师",
  "introduction": "...", "consultPrice": 100.00,
  "certification": "/images/cert1.jpg",
  "submitTime": "2026-09-15 10:00:00"
}
```

### PUT /api/admin/experts/{id}/approve 专家审核通过

### PUT /api/admin/experts/{id}/reject 专家审核驳回

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditRemark | String(255) | ⭕ | 驳回原因 |

### GET /api/admin/products 所有商品

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditStatus | Integer | ⭕ | 0待审核/1通过/2驳回 |
| status | Integer | ⭕ | 0下架/1上架 |
| keyword | String | ⭕ | 搜索商品名 |
| pageNum | Integer | ⭕ | 默认 1 |

### PUT /api/admin/products/{id}/approve 商品审核通过

### PUT /api/admin/products/{id}/reject 商品审核驳回

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditRemark | String(255) | ⭕ | 驳回时说明原因 |

### PUT /api/admin/finances/{id}/approve 融资审核通过

### PUT /api/admin/finances/{id}/reject 融资审核驳回

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| reviewRemark | String | ⭕ | 审核备注/拒绝原因 |

### GET /api/admin/stats/overview 数据统计概览

**响应 data：**
```json
{
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
    { "orderNo": "RX20260920001", "buyerName": "王五", "totalAmount": 290.00, "status": 3, "createTime": "..." }
  ],
  "recentFinanceApplies": [
    { "id": 1, "title": "红富士果园扩建项目融资", "userName": "李四", "amount": 200000.00, "status": 1, "createTime": "..." }
  ]
}
```

---

## 状态码速查

### 订单状态 orders.status
| 值 | 含义 |
|---|---|
| 0 | 待支付 |
| 1 | 已支付 |
| 2 | 已发货 |
| 3 | 已完成 |
| 4 | 已取消 |

### 订单退款状态 orders.refund_status
| 值 | 含义 |
|---|---|
| 0 | 无退款 |
| 1 | 退款申请中 |
| 2 | 已退款 |
| 3 | 退款拒绝 |

### 专家审核 expert.audit_status
| 值 | 含义 |
|---|---|
| 0 | 待审核 |
| 1 | 审核通过 |
| 2 | 已驳回 |

### 商品审核 product.audit_status
| 值 | 含义 |
|---|---|
| 0 | 待审核 |
| 1 | 审核通过 |
| 2 | 已驳回 |

### 融资申请 finance_apply.status
| 值 | 含义 |
|---|---|
| 0 | 待审核 |
| 1 | 审核通过 |
| 2 | 已拒绝 |
| 3 | 进行中 |
| 4 | 已完成 |

### 专家预约 appointment.status
| 值 | 含义 |
|---|---|
| 0 | 待确认 |
| 1 | 已确认 |
| 2 | 已完成 |
| 3 | 已取消 |

### 采购需求 demand.status
| 值 | 含义 |
|---|---|
| 0 | 已关闭 |
| 1 | 进行中 |
| 2 | 已完成 |

### HTTP 响应 code
| 值 | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 参数错误（缺少必填字段、格式不对） |
| 401 | 未登录 / Token 无效或过期 |
| 403 | 无权限（角色不对） |
| 404 | 资源不存在 |
| 500 | 服务端异常 |
| 1001+ | 自定义业务错误（如"库存不足"、"时间已被预约"） |