# B 模块功能实现说明

## 1. 负责范围

本文以最终运行版代码为准：

```text
E:\zzdd\20260918rxt\20260918rxt\src
```

负责模块：

1. 商品/货源模块
2. 采购需求模块
3. 购物车模块
4. 订单模块
5. 在线问答模块
6. 商品管理员模块
7. 需求管理员模块
8. 订单管理员模块

对应接口文档 `02-backend-B-trade-expert.md` 的第 `1-4、6、8-10` 章，共 `8` 个模块、`44` 个接口。

不包含：

- 专家模块
- 专家预约模块
- 用户管理、专家审核等后台管理功能

问答模块会读 `expert` 表，用于判断某个用户是否是专家。这只是问答模块对专家表的只读依赖，不属于专家模块实现。

> 注意：根目录 `E:\zzdd\20260918rxt\src` 是早期无 JWT 版本，`E:\zzdd\20260918rxt\20260918rxt\src` 才是带 JWT 的最终版。本文所有行号均按最终版代码。

---

## 2. 统一请求链路

所有接口基本都按照下面的链路执行：

```text
HTTP 请求
→ JwtAuthenticationFilter 解析 Authorization: Bearer token
→ SecurityConfig 判断接口是公开、需登录还是管理员
→ XxxController 接收参数
→ BaseController.getCurrentUserId() 获取当前登录用户
→ XxxService 接口
→ XxxServiceImpl 执行业务校验
→ XxxMapper 通过 MyBatis-Plus 读写数据库
→ Entity 转换成 VO
→ Result / PageResult 包装
→ JSON 返回前端
```

关键公共代码：

| 功能 | 代码位置 |
|---|---|
| 解析 JWT、设置登录态 | `config/security/JwtAuthenticationFilter.java:38` |
| 放行公开接口、限制登录接口 | `config/SecurityConfig.java:93` |
| 获取当前登录用户 | `controller/BaseController.java:25` |
| 统一返回结构 | `common/Result.java:18` |
| 统一分页结构 | `common/PageResult.java:30` |
| MyBatis-Plus 分页插件 | `config/MyBatisPlusConfig.java:17` |
| LocalDateTime 时间格式 | `config/JacksonConfig.java:24` |

管理员接口使用两层权限校验：

1. Controller 类上的 `@PreAuthorize("hasRole('ADMIN')")`。
2. ServiceImpl 中的 `requireAdmin()` 再查一次数据库，确认当前用户角色仍然是管理员。

---

## 3. 商品/货源模块

### 3.1 商品列表

接口：

```text
GET /api/product
```

实现链路：

```text
ProductController.java:42
→ ProductServiceImpl.java:56
→ ProductMapper.selectPage()
→ 批量查询分类和卖家
→ ProductListVO
→ PageResult
```

实现逻辑：

- 固定筛选 `audit_status=1`，保证只展示审核通过的商品。
- 固定筛选 `status=1`，保证只展示上架商品。
- 关键字同时搜索商品名称和产地。
- 可以继续按分类 ID、产地筛选。
- 先分页查询商品，再收集本页所有 `categoryId` 和 `sellerId` 去重。
- 使用 `selectBatchIds()` 批量查分类和用户，避免 N+1 查询。
- 把 `Product` 转成 `ProductListVO` 后返回。

代码位置：

```text
controller/ProductController.java:42
service/impl/ProductServiceImpl.java:56
service/impl/ProductServiceImpl.java:251
service/impl/ProductServiceImpl.java:264
service/impl/ProductServiceImpl.java:277
```

### 3.2 商品详情

接口：

```text
GET /api/product/{id}
```

实现链路：

```text
ProductController.java:53
→ ProductServiceImpl.java:88
→ 查商品、分类、卖家
→ 解析 images JSON
→ ProductDetailVO
```

实现逻辑：

- 使用 `selectById()` 查询商品，不存在时抛 `2002 商品不存在`。
- 查询商品分类和卖家用户。
- 将数据库中的 `images` JSON 字符串解析成 `List<String>`。
- 组装 `ProductDetailVO`。

代码位置：

```text
controller/ProductController.java:53
service/impl/ProductServiceImpl.java:88
service/impl/ProductServiceImpl.java:320
```

### 3.3 发布货源

接口：

```text
POST /api/product
```

实现链路：

```text
ProductController.java:59
→ ProductServiceImpl.java:117
→ 服务端校验
→ 设置 sellerId、auditStatus、status
→ ProductMapper.insert()
→ 返回新增 ID
```

实现逻辑：

- Controller 使用 `@Validated(ValidateGroup.Create.class)` 做 DTO 基础校验。
- Service 再校验商品名称、分类类型、价格、库存、主图。
- `sellerId` 必须取当前登录用户，不能由前端传入。
- 新商品默认 `auditStatus=0`，进入待审核状态。
- 新商品默认 `status=1`，但公开列表要求审核通过后才可见。
- 插入成功后返回 MyBatis-Plus 回填的 `id`。

代码位置：

```text
controller/ProductController.java:59
service/impl/ProductServiceImpl.java:117
service/impl/ProductServiceImpl.java:245
```

### 3.4 修改货源

接口：

```text
PUT /api/product/{id}
```

实现链路：

```text
ProductController.java:66
→ ProductServiceImpl.java:154
→ 查商品
→ 判断 sellerId == 当前用户
→ 组装非空更新字段
→ ProductMapper.updateById()
```

实现逻辑：

- 先查询商品是否存在。
- 比较 `sellerId` 和当前登录用户，防止修改别人的货源。
- 只把请求中传入的字段设置到更新对象。
- 利用 MyBatis-Plus `updateById()` 默认忽略 `null` 字段的特性，实现只修改传入字段。

代码位置：

```text
controller/ProductController.java:66
service/impl/ProductServiceImpl.java:154
```

### 3.5 上架/下架

接口：

```text
PUT /api/product/{id}/status
```

实现链路：

```text
ProductController.java:73
→ ProductServiceImpl.java:189
→ 校验 status 只能是 0/1
→ 查商品并校验 sellerId
→ 更新 status
```

实现逻辑：

- 状态参数只能是 `0=下架` 或 `1=上架`。
- 商品必须存在且属于当前用户。
- 只修改 `status` 字段，不修改 `auditStatus`。

代码位置：

```text
controller/ProductController.java:73
service/impl/ProductServiceImpl.java:189
```

### 3.6 删除货源

接口：

```text
DELETE /api/product/{id}
```

实现链路：

```text
ProductController.java:80
→ ProductServiceImpl.java:208
→ 查商品
→ 校验 sellerId
→ ProductMapper.deleteById()
```

实现逻辑：

- 先确认商品存在。
- 再确认商品属于当前登录用户。
- 校验通过后物理删除商品。

代码位置：

```text
controller/ProductController.java:80
service/impl/ProductServiceImpl.java:208
```

### 3.7 我的货源

接口：

```text
GET /api/product/my
```

实现链路：

```text
ProductController.java:87
→ ProductServiceImpl.java:221
→ 按 sellerId 查询
→ 按 auditStatus/status 筛选
→ 返回分页结果
```

实现逻辑：

- 强制使用当前登录用户作为 `sellerId`。
- 支持按审核状态和上下架状态筛选。
- 默认按创建时间倒序。
- 直接返回 `PageResult<Product>`。

代码位置：

```text
controller/ProductController.java:87
service/impl/ProductServiceImpl.java:221
```

### 3.8 商品管理员

接口与实现位置：

| 功能 | 接口 | Controller | ServiceImpl |
|---|---|---|---|
| 商品列表 | `GET /api/admin/products` | `AdminProductController.java:35` | `AdminProductServiceImpl.java:51` |
| 审核通过 | `PUT /api/admin/products/{id}/approve` | `AdminProductController.java:46` | `AdminProductServiceImpl.java:73` |
| 审核驳回 | `PUT /api/admin/products/{id}/reject` | `AdminProductController.java:53` | `AdminProductServiceImpl.java:87` |
| 强制上下架 | `PUT /api/admin/products/{id}/status` | `AdminProductController.java:60` | `AdminProductServiceImpl.java:107` |
| 删除商品 | `DELETE /api/admin/products/{id}` | `AdminProductController.java:67` | `AdminProductServiceImpl.java:120` |

实现逻辑：

- 所有接口先执行 `requireAdmin()` 校验管理员身份。
- 列表支持按审核状态、上下架状态、关键字筛选。
- 列表批量查询卖家信息，组装 `AdminProductListVO`。
- 审核通过只允许处理待审核商品，把 `auditStatus` 改成 `1`。
- 审核驳回只允许处理待审核商品，要求驳回原因非空且不超过 255 个字符，把 `auditStatus` 改成 `2`。
- 强制上下架由管理员直接修改 `status`，不校验卖家归属。
- 删除商品由管理员直接删除，不校验卖家归属。

---

## 4. 采购需求模块

### 4.1 需求列表

接口：

```text
GET /api/demand
```

实现链路：

```text
DemandController.java:43
→ DemandServiceImpl.java:51
→ DemandMapper.selectPage()
→ 批量查询买家
→ DemandListVO
→ PageResult
```

实现逻辑：

- 关键字同时搜索需求名称和描述。
- 支持按分类 ID、状态筛选。
- 分页查询需求。
- 收集本页所有 `buyerId` 去重，批量查询买家。
- 组装 `DemandListVO`。

代码位置：

```text
controller/DemandController.java:43
service/impl/DemandServiceImpl.java:51
service/impl/DemandServiceImpl.java:203
service/impl/DemandServiceImpl.java:216
```

### 4.2 需求详情

接口：

```text
GET /api/demand/{id}
```

实现链路：

```text
DemandController.java:54
→ DemandServiceImpl.java:79
→ 查询需求
→ 查询买家
→ DemandDetailVO
```

实现逻辑：

- 根据 ID 查需求，不存在时抛 `2002 需求不存在`。
- 查询买家用户信息。
- 组装 `DemandDetailVO`。

代码位置：

```text
controller/DemandController.java:54
service/impl/DemandServiceImpl.java:79
```

### 4.3 发布需求

接口：

```text
POST /api/demand
```

实现链路：

```text
DemandController.java:60
→ DemandServiceImpl.java:103
→ 校验名称、分类、数量、期望价格
→ 设置 buyerId 和默认状态
→ DemandMapper.insert()
```

实现逻辑：

- Controller 使用创建分组校验。
- Service 再次校验名称、分类是否存在、数量大于 0、期望价格大于 0。
- `buyerId` 取当前登录用户。
- 发布后的默认状态是 `1=进行中`。
- 插入成功后返回新增 ID。

代码位置：

```text
controller/DemandController.java:60
service/impl/DemandServiceImpl.java:103
```

### 4.4 修改需求

接口：

```text
PUT /api/demand/{id}
```

实现链路：

```text
DemandController.java:67
→ DemandServiceImpl.java:134
→ 查需求
→ 判断 buyerId == 当前用户
→ 组装非空更新字段
→ DemandMapper.updateById()
```

实现逻辑：

- 先确认需求存在。
- 比较 `buyerId` 和当前登录用户，防止修改别人的需求。
- 只更新请求中传入的字段。

代码位置：

```text
controller/DemandController.java:67
service/impl/DemandServiceImpl.java:134
```

### 4.5 删除需求

接口：

```text
DELETE /api/demand/{id}
```

实现链路：

```text
DemandController.java:74
→ DemandServiceImpl.java:167
→ 查需求
→ 校验 buyerId
→ 删除
```

实现逻辑：

- 先确认需求存在。
- 再确认需求属于当前用户。
- 校验通过后删除。

代码位置：

```text
controller/DemandController.java:74
service/impl/DemandServiceImpl.java:167
```

### 4.6 我的需求

接口：

```text
GET /api/demand/my
```

实现链路：

```text
DemandController.java:81
→ DemandServiceImpl.java:180
→ 按 buyerId 查询
→ 返回列表
```

实现逻辑：

- 使用当前登录用户作为 `buyerId`。
- 默认按创建时间倒序。
- 直接返回 `List<Demand>`。

代码位置：

```text
controller/DemandController.java:81
service/impl/DemandServiceImpl.java:180
```

### 4.7 需求管理员

接口与实现位置：

| 功能 | 接口 | Controller | ServiceImpl |
|---|---|---|---|
| 需求列表 | `GET /api/admin/demands` | `AdminDemandController.java:30` | `AdminDemandServiceImpl.java:43` |
| 删除需求 | `DELETE /api/admin/demands/{id}` | `AdminDemandController.java:40` | `AdminDemandServiceImpl.java:64` |

实现逻辑：

- 所有接口先执行 `requireAdmin()` 校验管理员身份。
- 列表支持按状态、关键字筛选。
- 列表批量查询买家信息。
- 删除前先确认需求存在，然后直接删除。

---

## 5. 购物车模块

### 5.1 购物车列表

接口：

```text
GET /api/cart
```

实现链路：

```text
CartController.java:38
→ CartServiceImpl.java:43
→ 按 userId 查询购物车
→ 批量查询商品
→ CartItemVO
```

实现逻辑：

- 只查询当前用户的购物车。
- 收集购物车里的 `productId` 去重。
- 批量查询商品。
- 组装 `CartItemVO` 列表。

代码位置：

```text
controller/CartController.java:38
service/impl/CartServiceImpl.java:43
```

### 5.2 加入购物车

接口：

```text
POST /api/cart
```

实现链路：

```text
CartController.java:44
→ CartServiceImpl.java:65
→ 校验商品 ID、数量
→ 查商品并校验可购买
→ 查同一用户是否已有该商品
→ 累加数量并校验库存
→ 插入或更新购物车
```

实现逻辑：

- 商品 ID 不能为空。
- 数量必须大于 0。
- 商品必须存在。
- 商品必须审核通过且已上架。
- 如果同一用户已有同一商品，则数量累加。
- 累加后的数量不能超过商品库存。
- 不存在则新增购物车项，存在则更新数量。

代码位置：

```text
controller/CartController.java:44
service/impl/CartServiceImpl.java:65
```

### 5.3 修改购物车数量

接口：

```text
PUT /api/cart/{id}
```

实现链路：

```text
CartController.java:51
→ CartServiceImpl.java:110
→ 校验数量
→ 校验购物车归属
→ 查询商品
→ 校验库存
→ 更新数量
```

实现逻辑：

- 新数量必须大于 0。
- 通过 `getOwnedCart()` 校验购物车项存在且属于当前用户。
- 查询商品，确认商品仍然存在。
- 新数量不能超过商品库存。
- 更新购物车数量。

代码位置：

```text
controller/CartController.java:51
service/impl/CartServiceImpl.java:110
service/impl/CartServiceImpl.java:174
```

### 5.4 删除购物车项

接口：

```text
DELETE /api/cart/{id}
```

实现链路：

```text
CartController.java:58
→ CartServiceImpl.java:132
→ 校验购物车归属
→ 删除
```

实现逻辑：

- 使用 `getOwnedCart()` 确认购物车项属于当前用户。
- 确认后直接删除。

代码位置：

```text
controller/CartController.java:58
service/impl/CartServiceImpl.java:132
```

### 5.5 批量勾选

接口：

```text
PUT /api/cart/selected
```

实现链路：

```text
CartController.java:65
→ CartServiceImpl.java:139
→ 校验 ids 和 selected
→ 批量查询购物车项
→ 校验数量一致
→ 逐个校验归属
→ 逐个更新 selected
```

实现逻辑：

- `ids` 不能为空。
- `selected` 只能是 `0` 或 `1`。
- 批量查询购物车项。
- 查询结果数量必须和传入 ID 数量一致，否则说明部分数据不存在。
- 遍历确认每一项都属于当前用户。
- 最后逐个更新勾选状态。

代码位置：

```text
controller/CartController.java:65
service/impl/CartServiceImpl.java:139
```

---

## 6. 订单模块

### 6.1 创建订单

接口：

```text
POST /api/order
```

实现链路：

```text
OrdersController.java:38
→ OrdersServiceImpl.java:81
→ 校验地址
→ 校验购物车项
→ 校验商品和库存
→ 按卖家分组
→ 拆分订单
→ 写订单主表和明细表
→ 扣减库存
→ 删除购物车项
```

核心实现：

- Method 上带 `@Transactional(rollbackFor = Exception.class)`。
- 校验收货地址属于当前用户。
- 校验购物车项存在且属于当前用户。
- 逐项检查商品已审核、已上架、库存足够。
- 使用 `LinkedHashMap` 按 `sellerId` 分组，保持顺序。
- 每个卖家生成一张订单。
- 每个订单主表存收货信息快照。
- 每个订单明细存商品名称、图片、单价快照。
- 每个商品扣减库存。
- 每个已下单购物车项被删除。
- 最后返回订单号和总金额。

代码位置：

```text
controller/OrdersController.java:38
service/impl/OrdersServiceImpl.java:81
service/impl/OrdersServiceImpl.java:389
```

### 6.2 我的采购订单

接口：

```text
GET /api/order/purchase
```

实现链路：

```text
OrdersController.java:44
→ OrdersServiceImpl.java:191
→ 按 buyerId 查询
→ 批量查卖家
→ OrderListVO
```

实现逻辑：

- 按当前用户 `buyerId` 查询订单。
- 支持按订单状态筛选。
- 分页后批量查询本页卖家信息。
- 组装 `OrderListVO`。

代码位置：

```text
controller/OrdersController.java:44
service/impl/OrdersServiceImpl.java:191
```

### 6.3 我的销售订单

接口：

```text
GET /api/order/sale
```

实现链路：

```text
OrdersController.java:53
→ OrdersServiceImpl.java:210
→ 按 sellerId 查询
→ 批量查买家
→ OrderListVO
```

实现逻辑：

- 按当前用户 `sellerId` 查询订单。
- 支持按订单状态筛选。
- 分页后批量查询本页买家信息。
- 组装 `OrderListVO`。

代码位置：

```text
controller/OrdersController.java:53
service/impl/OrdersServiceImpl.java:210
```

### 6.4 订单详情

接口：

```text
GET /api/order/{id}
```

实现链路：

```text
OrdersController.java:62
→ OrdersServiceImpl.java:229
→ 查订单
→ 判断买家/卖家/管理员权限
→ 查订单明细
→ OrderDetailVO
```

实现逻辑：

- 查询订单，不存在时抛 `2002 订单不存在`。
- 当前用户必须是买家、卖家或管理员。
- 查询订单明细。
- 组装 `OrderDetailVO`。

代码位置：

```text
controller/OrdersController.java:62
service/impl/OrdersServiceImpl.java:229
```

### 6.5 模拟支付

接口：

```text
PUT /api/order/{id}/pay
```

实现链路：

```text
OrdersController.java:68
→ OrdersServiceImpl.java:267
→ 校验买家
→ 判断状态必须是 0
→ 改成 1 并写支付时间
```

实现逻辑：

- 只有买家可以支付自己的订单。
- 只有 `0=待支付` 状态可以支付。
- 更新状态为 `1=已支付`。
- 写入 `payTime`。

代码位置：

```text
controller/OrdersController.java:68
service/impl/OrdersServiceImpl.java:267
```

### 6.6 卖家发货

接口：

```text
PUT /api/order/{id}/ship
```

实现链路：

```text
OrdersController.java:75
→ OrdersServiceImpl.java:282
→ 校验卖家
→ 判断状态必须是 1
→ 改成 2 并写发货时间
```

实现逻辑：

- 只有卖家可以操作自己的销售订单。
- 只有 `1=已支付` 状态可以发货。
- 更新状态为 `2=已发货`。
- 写入 `shipTime`。

代码位置：

```text
controller/OrdersController.java:75
service/impl/OrdersServiceImpl.java:282
```

### 6.7 确认收货

接口：

```text
PUT /api/order/{id}/finish
```

实现链路：

```text
OrdersController.java:82
→ OrdersServiceImpl.java:297
→ 校验买家
→ 判断状态必须是 2
→ 改成 3 并写完成时间
```

实现逻辑：

- 只有买家可以确认收货。
- 只有 `2=已发货` 状态可以确认收货。
- 更新状态为 `3=已完成`。
- 写入 `finishTime`。

代码位置：

```text
controller/OrdersController.java:82
service/impl/OrdersServiceImpl.java:297
```

### 6.8 取消订单

接口：

```text
PUT /api/order/{id}/cancel
```

实现链路：

```text
OrdersController.java:89
→ OrdersServiceImpl.java:313
→ 校验买家
→ 判断状态必须是 0
→ 根据订单明细回补库存
→ 改成 4
```

实现逻辑：

- Method 上带事务。
- 只有买家可以取消自己的订单。
- 只有 `0=待支付` 状态可以取消。
- 查询订单明细。
- 根据订单明细逐项回补商品库存。
- 更新订单状态为 `4=已取消`。

代码位置：

```text
controller/OrdersController.java:89
service/impl/OrdersServiceImpl.java:313
```

### 6.9 申请退款

接口：

```text
POST /api/order/{id}/refund
```

实现链路：

```text
OrdersController.java:96
→ OrdersServiceImpl.java:339
→ 校验买家
→ 判断订单状态
→ 判断是否已申请退款
→ 设置 refundStatus=1
```

实现逻辑：

- 只有买家可以申请退款。
- 待支付和已取消状态不能申请退款。
- 已有退款申请时不能重复申请。
- 更新 `refundStatus=1` 表示申请中。
- 保存退款原因。

代码位置：

```text
controller/OrdersController.java:96
service/impl/OrdersServiceImpl.java:339
```

### 6.10 订单管理员

接口：

```text
GET /api/admin/orders
```

实现链路：

```text
AdminOrderController.java:28
→ AdminOrderServiceImpl.java:44
→ 校验管理员
→ 按状态/订单号分页查询
→ 批量查询买家和卖家
→ AdminOrderListVO
```

实现逻辑：

- 先执行 `requireAdmin()`。
- 支持按订单状态和订单号筛选。
- 分页查询全部订单。
- 收集本页买家和卖家 ID，合并去重。
- 一次性查询所有用户，组装 `AdminOrderListVO`。

代码位置：

```text
controller/AdminOrderController.java:28
service/impl/AdminOrderServiceImpl.java:44
```

---

## 7. 在线问答模块

### 7.1 问答列表

接口：

```text
GET /api/qa
```

实现链路：

```text
QaController.java:43
→ QaServiceImpl.java:60
→ 动态查询 qa
→ 批量查询提问者和专家
→ QaListVO
→ PageResult
```

实现逻辑：

- 支持关键字搜索标题和内容。
- 支持按分类、状态筛选。
- 分页查询问题。
- 批量查询提问者和指定专家。
- 组装 `QaListVO`。

代码位置：

```text
controller/QaController.java:43
service/impl/QaServiceImpl.java:60
```

### 7.2 问答详情

接口：

```text
GET /api/qa/{id}
```

实现链路：

```text
QaController.java:54
→ QaServiceImpl.java:88
→ 查问题
→ 浏览数 +1
→ 查回答
→ 批量查回答者
→ QaDetailVO
```

实现逻辑：

- Method 上带事务。
- 查询问题，不存在时抛 `2002 问题不存在`。
- 浏览数 `+1` 并更新数据库。
- 查询该问题的全部回答，按创建时间升序。
- 批量查询提问者、专家和回答者。
- 组装 `QaDetailVO`。

代码位置：

```text
controller/QaController.java:54
service/impl/QaServiceImpl.java:88
```

### 7.3 发起提问

接口：

```text
POST /api/qa
```

实现链路：

```text
QaController.java:60
→ QaServiceImpl.java:125
→ 校验标题、内容、分类
→ 校验指定专家
→ 设置 questionerId
→ 设置默认状态和计数
→ 插入 qa
```

实现逻辑：

- 校验请求体、标题、内容、分类。
- 如果传了 `expertId`，查 `expert` 表确认该用户是专家。
- `questionerId` 取当前登录用户。
- 状态默认 `0=待回答`。
- `viewCount=0`，`replyCount=0`。
- 插入问题后返回新增 ID。

代码位置：

```text
controller/QaController.java:60
service/impl/QaServiceImpl.java:125
service/impl/QaServiceImpl.java:317
```

### 7.4 修改问题

接口：

```text
PUT /api/qa/{id}
```

实现链路：

```text
QaController.java:66
→ QaServiceImpl.java:150
→ 校验请求体
→ 查问题
→ 校验 questionerId
→ 更新问题字段
```

实现逻辑：

- 校验请求体、标题、内容、分类。
- 查询问题是否存在。
- 校验 `questionerId == 当前用户`。
- 如果修改了专家，重新校验专家是否存在。
- 更新标题、内容、分类、专家、图片。

代码位置：

```text
controller/QaController.java:66
service/impl/QaServiceImpl.java:150
```

### 7.5 删除问题

接口：

```text
DELETE /api/qa/{id}
```

实现链路：

```text
QaController.java:73
→ QaServiceImpl.java:170
→ 查问题
→ 校验提问者
→ 先删 qa_reply
→ 再删 qa
```

实现逻辑：

- Method 上带事务。
- 校验当前用户是提问者。
- 先删除该问题下所有回答。
- 再删除问题本身。

代码位置：

```text
controller/QaController.java:73
service/impl/QaServiceImpl.java:170
```

### 7.6 回答问题

接口：

```text
POST /api/qa/{id}/reply
```

实现链路：

```text
QaController.java:80
→ QaServiceImpl.java:181
→ 校验回答内容
→ 查问题
→ 禁止回答自己的问题
→ 插入回答
→ 问题回答数 +1
→ 必要时改成已回答
```

实现逻辑：

- Method 上带事务。
- 回答内容不能为空。
- 问题必须存在。
- 不能回答自己提出的问题。
- 查 `expert` 表判断回答者是否是专家，写入 `isExpert`。
- 插入回答。
- 问题 `replyCount + 1`。
- 如果问题状态是 `0=待回答`，改成 `1=已回答`。

代码位置：

```text
controller/QaController.java:80
service/impl/QaServiceImpl.java:181
```

### 7.7 我的提问

接口：

```text
GET /api/qa/my/question
```

实现链路：

```text
QaController.java:87
→ QaServiceImpl.java:209
→ 按 questionerId 查询
→ QaMyQuestionVO
```

实现逻辑：

- 当前用户作为 `questionerId`。
- 默认按创建时间倒序。
- 返回 `id、title、status、replyCount、createTime`。

代码位置：

```text
controller/QaController.java:87
service/impl/QaServiceImpl.java:209
```

### 7.8 我的回答

接口：

```text
GET /api/qa/my/reply
```

实现链路：

```text
QaController.java:93
→ QaServiceImpl.java:227
→ 按 replierId 查询回答
→ 收集 qaId
→ 批量查询问题
→ QaMyReplyVO
```

实现逻辑：

- 当前用户作为 `replierId`。
- 查询当前用户的回答。
- 收集回答对应的 `qaId` 并去重。
- 批量查询问题，补问题标题。
- 组装 `QaMyReplyVO`。

代码位置：

```text
controller/QaController.java:93
service/impl/QaServiceImpl.java:227
```

### 7.9 删除回答

接口：

```text
DELETE /api/qa/reply/{id}
```

实现链路：

```text
QaController.java:99
→ QaServiceImpl.java:255
→ 查回答
→ 校验 replierId
→ 删除回答
→ 问题回答数 -1
→ 必要时恢复待回答
```

实现逻辑：

- Method 上带事务。
- 回答必须存在。
- 只能删除自己的回答。
- 删除回答后，把对应问题的 `replyCount - 1`。
- 使用 `Math.max(0, count)` 防止出现负数。
- 如果回答数减到 0，问题状态恢复为 `0=待回答`。

代码位置：

```text
controller/QaController.java:99
service/impl/QaServiceImpl.java:255
```

---

## 8. 涉及的数据表

| 模块 | 主要数据表 |
|---|---|
| 商品 | `product`、`category`、`user` |
| 需求 | `demand`、`category`、`user` |
| 购物车 | `cart`、`product` |
| 订单 | `orders`、`order_item`、`cart`、`product`、`address`、`user` |
| 问答 | `qa`、`qa_reply`、`user`、`expert`（只读） |
| 商品管理员 | `product`、`user` |
| 需求管理员 | `demand`、`user` |
| 订单管理员 | `orders`、`user` |

---

## 9. 通用实现技巧

### 9.1 动态查询

使用 MyBatis-Plus 的 `LambdaQueryWrapper`：

```java
wrapper.eq(auditStatus != null, Product::getAuditStatus, auditStatus);
```

第一个参数是布尔开关，为 `false` 时不会拼接该查询条件。

### 9.2 关键字 OR 查询

关键字同时搜多个字段时，必须用 `and(...)` 包起来：

```java
wrapper.and(w -> w.like(Product::getName, kw)
        .or().like(Product::getOrigin, kw));
```

否则 `or` 可能破坏前面已经拼接的审核、上架等条件。

### 9.3 避免 N+1

列表查询统一使用：

```text
1. 分页查询当前页数据
2. 收集关联外键 ID
3. filter 非空并去重
4. selectBatchIds 批量查询
5. 转成 Map
6. 组装 VO
```

### 9.4 归属校验

写操作统一遵循：

```text
1. 校验是否登录
2. 查询数据是否存在
3. 比较 ownerId 和当前用户 ID
4. 校验通过后才执行更新或删除
```

商品看 `sellerId`，需求看 `buyerId`，购物车看 `userId`，订单根据操作分别看 `buyerId` 或 `sellerId`，问答看 `questionerId` 或 `replierId`。

### 9.5 事务边界

明确使用事务的方法：

| 模块 | 方法 | 原因 |
|---|---|---|
| 订单 | `create` | 写订单、写明细、扣库存、删购物车 |
| 订单 | `cancel` | 回补库存、修改订单状态 |
| 问答 | `detail` | 读数据并增加浏览数 |
| 问答 | `delete` | 删除问题、删除全部回答 |
| 问答 | `reply` | 插入回答、更新回答数和状态 |
| 问答 | `deleteReply` | 删除回答、回退回答数和状态 |

### 9.6 管理员双层权限

```text
第一层：@PreAuthorize("hasRole('ADMIN')")
第二层：Service requireAdmin() 查数据库确认角色
```

第一层拦截普通请求，第二层防止旧 JWT 中角色过期后仍然通过。

---

## 10. 答辩时可以直接使用的总结

我负责交易和问答这条业务线，共 8 个模块、44 个接口，包括商品、采购需求、购物车、订单、在线问答，以及商品、需求、订单三个管理员模块。

整体实现采用 Controller、Service、ServiceImpl、Mapper、Entity、DTO、VO 分层。Controller 只负责接收参数和获取当前用户；ServiceImpl 负责业务规则、权限归属、状态判断和事务；Mapper 负责数据库读写。

核心业务规则包括：

1. 商品发布时 `sellerId` 取登录用户，默认待审核、默认上架。
2. 公开商品列表只展示审核通过且已上架的商品。
3. 购物车加入同一商品时累加数量，并检查库存。
4. 创建订单时校验地址、购物车、商品和库存，并按卖家自动拆单。
5. 订单状态按照 `0→1→2→3` 流转，取消时回补库存。
6. 问答详情增加浏览数，回答和删除回答维护回答数闭环。
7. 管理员接口统一使用注解和数据库二次校验双重权限控制。

详细逐接口说明可继续参考同目录下的 `B模块代码详解.md`。
