# 农产品融销通一体平台 B 模块核心技术详解

> 代码基线：`E:\zzdd\20260918rxt\20260918rxt\src`
>
> 讲解范围：商品、需求、购物车、订单、在线问答、商品/需求/订单管理员，共 8 个模块、44 个接口。
>
> 目标：不只记住“用了什么技术”，还要能说清楚 **为什么这样做、请求怎样进入代码、每一层做了什么、数据库发生了什么、失败时怎样回滚、当前代码还有哪些限制**。

---

## 0. 先学会看代码的五个问题

阅读任何一个接口，都按下面五个问题走：

1. **HTTP 请求从哪里进来？** 找到 `Controller` 的 `@RequestMapping` 和具体 `@GetMapping/@PostMapping`。
2. **当前用户从哪里来？** 看 Controller 是否调用 `BaseController.getCurrentUserId()`。
3. **业务规则写在哪里？** 进入对应 `Service` 接口，再找到 `ServiceImpl` 中的实现。
4. **数据库操作有哪些？** 看 Mapper 的 `selectById`、`selectPage`、`selectBatchIds`、`insert`、`updateById`、`deleteById`。
5. **返回给前端什么？** 看 Entity 是否被转换成 VO，最后怎样被 `Result` 或 `PageResult` 包装。

本文默认源码根目录为：

```text
E:\zzdd\20260918rxt\20260918rxt\src\main\java\cn\lcj\rongxiaotong20260918
```

后文为了简洁，使用 `controller/...`、`service/impl/...` 这样的相对路径，实际都位于上述源码根目录下。

---

## 1. 整体分层与一次请求的完整链路

### 1.1 各层职责

| 层 | 主要文件 | 真正负责的内容 | 不应该负责的内容 |
|---|---|---|---|
| Controller | `controller/*.java` | 接收 HTTP 参数、取当前用户、调用 Service、包装返回值 | 写复杂业务规则、直接操作 Mapper |
| Service 接口 | `service/*.java` | 定义业务能力 | 写实现 |
| ServiceImpl | `service/impl/*.java` | 业务判断、权限归属、状态迁移、事务、Entity 转 VO | 处理 HTTP 状态码、解析请求头 |
| Mapper | `mapper/*.java` | 数据库读写 | 业务权限判断 |
| Entity | `pojo/entity/*.java` | 与数据库表字段对应 | 直接作为所有接口的响应模型 |
| DTO | `pojo/dto/*.java` | 接收请求参数和基础格式校验 | 承载数据库关联信息 |
| VO | `pojo/vo/*.java` | 控制接口响应字段 | 承载数据库写操作 |
| 公共设施 | `common/*`、`config/*` | 统一响应、异常、认证、分页 | 处理具体交易业务 |

### 1.2 以创建订单为例

接口：

```http
POST /api/order
```

实际调用链：

```text
浏览器 / 前端
  |
  | Authorization: Bearer <JWT>
  v
JwtAuthenticationFilter
  |-- 解析 JWT
  |-- 取得 userId
  |-- 按 userId 查询最新用户
  |-- 写入 SecurityContext
  v
SecurityConfig
  |-- /api/order 不属于 permitAll
  |-- 要求 authenticated()
  v
OrdersController.create()
  |-- @Validated 校验 OrderCreateDTO
  |-- getCurrentUserId() 从 SecurityContext 取 userId
  v
OrdersServiceImpl.create()
  |-- 开启事务
  |-- 校验收货地址
  |-- 批量查询购物车
  |-- 校验购物车归属
  |-- 查询商品并校验审核、上架、库存
  |-- 按 sellerId 分组
  |-- 每个卖家生成一张订单
  |-- 插入订单主表
  |-- 插入订单明细
  |-- 更新商品库存
  |-- 删除购物车项
  |-- 提交事务
  v
OrdersMapper / OrderItemMapper / ProductMapper / CartMapper
  |-- MyBatis-Plus 根据 Entity 和 Wrapper 生成 SQL
  v
MySQL
  |
  v
OrderCreateVO
  |
  v
Result.success(OrderCreateVO)
  |
  v
JSON 响应
```

关键代码：

- `controller/OrdersController.java:31`：订单接口统一前缀 `/api/order`。
- `controller/OrdersController.java:38-40`：接收 `POST /api/order`，调用 Service。
- `service/impl/OrdersServiceImpl.java:80-188`：创建订单主流程。
- `config/security/JwtAuthenticationFilter.java:38-80`：解析 JWT 并写入登录态。
- `config/SecurityConfig.java:81-157`：配置无状态认证和接口访问规则。

### 1.3 Controller 为什么这么薄

`OrdersController.create()` 的核心代码只有：

```java
return Result.success(ordersService.create(getCurrentUserId(), dto));
```

它做了三件事：

1. `@Validated` 让 Spring MVC 先做 DTO 基础校验。
2. `getCurrentUserId()` 从 `SecurityContextHolder` 读取 JWT 解析后的用户 ID。
3. 把 Service 返回的 VO 包装成统一响应 `Result`。

真正的业务规则全部在 `OrdersServiceImpl`。这是分层的最重要意义：Controller 不关心“地址是不是你的”“库存够不够”“要拆几张订单”。

---

## 2. 六个核心技术点总览

| 主题 | 你要理解的核心问题 | 主要代码 |
|---|---|---|
| 事务与按卖家拆单 | 一次下单为什么要写多张表，如何保证一起成功或一起失败 | `OrdersServiceImpl.create()` |
| 订单状态机与库存补偿 | 谁能在什么状态下做什么，取消后库存怎样恢复 | `OrdersServiceImpl.pay/ship/finish/cancel/refund` |
| 纵深权限与资源归属 | 认证、角色权限、数据归属权限分别解决什么 | `JwtAuthenticationFilter`、`SecurityConfig`、`@PreAuthorize`、`checkBuyer`、`getOwnedCart` |
| N+1 与批量查询 | 分页后为什么不能逐条查关联数据，Map 回填怎样工作 | `ProductServiceImpl.pageList()`、`categoryMap()`、`userMap()` |
| 商品双状态治理 | 审核状态和上下架状态为什么要分开 | `ProductServiceImpl`、`AdminProductServiceImpl` |
| 问答一致性 + 接口工程 | 主表、子表、计数和状态怎样保持一致 | `QaServiceImpl`、DTO、`Result`、`PageResult`、异常处理 |

从第 3 章开始，每个主题都会额外包含：

- **本部分面试题：** 面试官最可能从项目出发怎样追问。
- **本部分八股原理：** 题目背后的通用后端知识。
- **结合项目的答法：** 哪些是当前代码已经做到的，哪些只是优化方向。

---

## 3. 主题一：事务 + 按卖家自动拆单

## 3.1 业务问题

购物车中可能同时存在多个卖家的商品。例如：

| 购物车项 | 商品 | 卖家 |
|---|---|---|
| 1 | 苹果 10 斤 | 卖家 A |
| 2 | 梨 5 斤 | 卖家 B |
| 3 | 苹果 20 斤 | 卖家 A |

买家点击“提交订单”后，不能只做一条 `orders` 插入，因为：

1. 不同卖家需要分别发货、履约和结算。
2. 订单主表要记录每个卖家对应的总金额。
3. 订单明细要记录每件商品的成交快照。
4. 商品库存需要扣减。
5. 下单成功的购物车项需要删除。

所以系统按 `sellerId` 拆分，本例应生成两张订单：

```text
订单 RX...001，卖家 A，总金额 = 苹果 10 斤 + 苹果 20 斤
订单 RX...002，卖家 B，总金额 = 梨 5 斤
```

## 3.2 从接口到数据库的实现链路

```text
POST /api/order
-> OrdersController.create()                         OrdersController.java:38
-> getCurrentUserId()                                BaseController.java:25
-> OrdersServiceImpl.create()                        OrdersServiceImpl.java:81
   -> requireLogin()                                 OrdersServiceImpl.java:358
   -> addressMapper.selectById()                     OrdersServiceImpl.java:90
   -> cartMapper.selectBatchIds()                    OrdersServiceImpl.java:95
   -> productMapper.selectById()                     OrdersServiceImpl.java:105
   -> groupBySeller.computeIfAbsent()                OrdersServiceImpl.java:116
   -> ordersMapper.insert()                          OrdersServiceImpl.java:163
   -> orderItemMapper.insert()                       OrdersServiceImpl.java:170
   -> productMapper.updateById()                     OrdersServiceImpl.java:176
   -> cartMapper.deleteById()                        OrdersServiceImpl.java:178
-> Result.success(OrderCreateVO)
```

## 3.3 `@Transactional` 到底做了什么

代码位置：

- `service/impl/OrdersServiceImpl.java:79-81`

```java
@Override
@Transactional(rollbackFor = Exception.class)
public OrderCreateVO create(Long userId, OrderCreateDTO dto) {
```

`@Transactional` 不是“给方法画了一个标记”就结束，它的真实过程是：

1. Spring 启动时扫描到 `@Transactional`。
2. Spring 给 `OrdersServiceImpl` 创建代理对象。
3. Controller 注入的实际上是代理对象。
4. 请求进入 `create()` 时，代理先向事务管理器申请数据库连接。
5. 关闭 `autoCommit`，开启事务。
6. 执行方法内部的所有 Mapper 操作。
7. 方法正常返回：提交事务。
8. 方法抛出异常：根据 `rollbackFor` 决定是否回滚。

`rollbackFor = Exception.class` 的作用是：

- Spring 默认只对 `RuntimeException` 和 `Error` 自动回滚。
- 加上 `rollbackFor = Exception.class` 后，受检异常也会回滚。
- 本项目虽然主要抛 `BusinessException`（运行时异常），但显式写出更稳妥。

必须注意：**同类内部直接调用带事务的方法不会走 Spring 代理**。例如在同一个 Service 中直接调用 `this.someTransactionalMethod()`，事务注解可能不会生效。本项目事务方法由 Controller 从外部调用，因此代理链是正常的。

## 3.4 创建订单逐段解释

### 第一步：登录和基础参数校验

代码位置：`OrdersServiceImpl.java:82-88`

```java
requireLogin(userId);
if (dto.getAddressId() == null) {
    throw new BusinessException(2001, "请选择收货地址");
}
if (dto.getCartIds() == null || dto.getCartIds().isEmpty()) {
    throw new BusinessException(2001, "请选择要下单的购物车项");
}
```

为什么 Controller 已经 `@Validated`，Service 还要判断？

- DTO 校验负责“字段是否为空、长度是否超限”等通用格式。
- Service 校验负责“业务上下文”，例如购物车归属、商品是否可购买、库存是否足够。
- 即使以后 Service 被其他入口调用，也不能依赖 Controller 一定做过校验。

### 第二步：校验收货地址归属

代码位置：`OrdersServiceImpl.java:89-93`

```java
Address addr = addressMapper.selectById(dto.getAddressId());
if (addr == null || !addr.getUserId().equals(userId)) {
    throw new BusinessException(2002, "收货地址不存在");
}
```

这里不仅判断地址存在，还判断 `address.userId == 当前用户ID`。否则用户 A 可以把请求体中的地址 ID 改成用户 B 的地址。

### 第三步：批量查询购物车并确认数量

代码位置：`OrdersServiceImpl.java:94-98`

```java
List<Cart> carts = cartMapper.selectBatchIds(dto.getCartIds());
if (carts.size() != dto.getCartIds().size()) {
    throw new BusinessException(2002, "部分购物车项不存在");
}
```

调用 `selectBatchIds` 只生成一次批量查询，而不是循环调用 `selectById`。

数量比较用于发现：

- 某个购物车项已经被删除。
- 前端传入了不存在的 ID。

注意：如果请求里本身有重复 ID，`selectBatchIds` 返回数量可能小于原始列表长度，因此这种实现依赖前端不重复传 ID；更严谨的实现可以先 `distinct()` 并校验重复。

### 第四步：校验归属、商品状态和库存，同时按卖家分组

代码位置：`OrdersServiceImpl.java:99-117`

```java
Map<Long, List<Cart>> groupBySeller = new LinkedHashMap<>();
for (Cart c : carts) {
    if (!c.getUserId().equals(userId)) {
        throw new BusinessException(2003, "只能下单自己的购物车");
    }
    Product p = productMapper.selectById(c.getProductId());
    if (p == null) {
        throw new BusinessException(2002, "商品不存在");
    }
    if (!Objects.equals(p.getAuditStatus(), AUDIT_PASSED)
            || !Objects.equals(p.getStatus(), STATUS_ON)) {
        throw new BusinessException(2001, "商品已下架或未通过审核：" + p.getName());
    }
    if (c.getQuantity() > p.getStock()) {
        throw new BusinessException(2004, "库存不足：" + p.getName() + "，当前库存 " + p.getStock());
    }
    groupBySeller.computeIfAbsent(p.getSellerId(), k -> new ArrayList<>()).add(c);
}
```

这里有三层判断：

1. **数据归属**：购物车项必须属于当前用户。
2. **商品可售状态**：必须审核通过且上架。
3. **库存**：购物车项数量不能超过当前库存。

分组的实际过程：

```text
第一次遇到 sellerId=A：
  Map 中没有 A
  computeIfAbsent 创建 new ArrayList
  把购物车项加入 A 的 List

第二次遇到 sellerId=A：
  Map 中已有 A
  直接取出现有 List
  继续加入购物车项

遇到 sellerId=B：
  创建 B 对应 List
```

为什么使用 `LinkedHashMap`？

- `HashMap` 不保证遍历顺序。
- `LinkedHashMap` 保留第一次插入卖家的顺序。
- 订单号生成和返回顺序更稳定，调试时也更容易观察。

### 第五步：构造收货地址快照

代码位置：`OrdersServiceImpl.java:118-124`

```java
String receiver = addr.getReceiver();
String receiverPhone = addr.getPhone();
String receiverAddress = ...省 + 市 + 区 + 详细地址...;
```

为什么要复制到订单表？

- 地址可能被用户修改或删除。
- 历史订单必须保留下单时的收货人、电话和地址。
- 订单不能只存 `address_id`，否则地址变化后无法还原历史凭证。

实体对应位置：

- `pojo/entity/Orders.java:14-33`
- 收货快照字段：`receiver`、`receiverPhone`、`receiverAddress`

### 第六步：按卖家循环创建订单

代码位置：`OrdersServiceImpl.java:128-182`

对每个卖家：

1. 创建 `sellerTotal = 0`。
2. 遍历该卖家的购物车项。
3. 再次查询商品，计算 `price * quantity`。
4. 构建 `OrderItem` 快照。
5. 插入 `orders` 主表。
6. 遍历明细，插入 `order_item`。
7. 扣减商品库存。
8. 删除购物车项。

核心代码：

```java
BigDecimal subtotal = p.getPrice().multiply(BigDecimal.valueOf(c.getQuantity()));
sellerTotal = sellerTotal.add(subtotal);

oi.setProductName(p.getName());
oi.setProductImage(p.getImage());
oi.setPrice(p.getPrice());
oi.setQuantity(c.getQuantity());
oi.setSubtotal(subtotal);
```

这里保存了三类成交快照：

| 字段 | 为什么保存 |
|---|---|
| `productName` | 商品改名后历史订单仍然正确 |
| `productImage` | 商品主图更换或删除后仍能展示成交图片 |
| `price` | 商品改价后历史成交单价不变 |

实体对应：

- `pojo/entity/OrderItem.java:13-23`：`orderId`、`productId`、`productName`、`productImage`、`price`、`quantity`、`subtotal`。

### 第七步：插入订单主表后为什么马上有订单 ID

代码位置：

- `OrdersServiceImpl.java:163`
- `pojo/entity/Orders.java:16-17`

```java
@TableId(type = IdType.AUTO)
private Long id;
```

`ordersMapper.insert(order)` 后，MyBatis-Plus 会把数据库自增主键回填到 `order.getId()`。因此后面的：

```java
oi.setOrderId(order.getId());
```

才能拿到刚生成的订单 ID。

### 第八步：扣库存并删除购物车

代码位置：`OrdersServiceImpl.java:165-179`

```java
Product p = productMapper.selectById(c.getProductId());
Product stockUpdate = new Product();
stockUpdate.setId(p.getId());
stockUpdate.setStock(p.getStock() - c.getQuantity());
productMapper.updateById(stockUpdate);

cartMapper.deleteById(c.getId());
```

这表示：

```text
INSERT orders
INSERT order_item
UPDATE product SET stock = 新库存 WHERE id = ?
DELETE FROM cart WHERE id = ?
```

这些操作都属于同一个事务。任意一个 Mapper 抛出异常，之前已经执行的 SQL 会回滚。

## 3.5 失败回滚示例

假设流程如下：

1. 插入订单主表成功。
2. 插入订单明细成功。
3. 更新第一个商品库存成功。
4. 删除第二个购物车项时数据库异常。

因为 `create()` 整体在事务中，异常向外抛出后，Spring 会回滚：

- 已插入的订单主表记录。
- 已插入的订单明细。
- 已修改的商品库存。
- 已删除的购物车项。

这就是“订单、明细、库存、购物车”四类数据必须一起成功或一起失败的原因。

## 3.6 当前实现真实存在的并发问题

### 问题一：库存可能丢失更新

当前扣库存方式：

```java
Product p = productMapper.selectById(productId);
stockUpdate.setStock(p.getStock() - quantity);
productMapper.updateById(stockUpdate);
```

这是典型的“先查后写”。并发场景：

```text
库存 = 10
请求 A 读到 10
请求 B 读到 10
A 写入 8
B 写入 7
```

最终库存可能变成 7，而实际卖出了 5 件，正确库存应为 5。这就是丢失更新，严重时可能超卖。

更安全的写法是条件更新：

```sql
UPDATE product
SET stock = stock - #{quantity}
WHERE id = #{productId}
  AND stock >= #{quantity}
```

然后检查影响行数是否为 1。也可以用乐观锁版本号。

### 问题二：订单号可能重复

代码位置：`OrdersServiceImpl.java:389-393`

```java
String prefix = "RX" + LocalDateTime.now().format(...);
Long todayCount = ordersMapper.selectCount(...);
return prefix + String.format("%03d", todayCount + 1);
```

并发下两个请求可能同时读到相同 `todayCount`，生成相同订单号。`orders.order_no` 有唯一约束，最终会有一个请求失败。

改进方向：

- Redis `INCR`。
- 数据库序列表。
- 雪花算法等分布式 ID。
- 先预留号段再生成订单号。

### 问题三：创建订单重复查询商品

同一商品在本次请求中被查询多次：

1. 第 105 行初次校验。
2. 第 136 行计算金额。
3. 第 172 行扣库存前再次查询。

可以在最开始构造：

```java
Map<Long, Product> productMap;
```

后续统一从 Map 取 Product，减少数据库访问。

### 问题四：没有下单幂等

如果用户快速点击两次“提交订单”，可能生成两批订单。生产中通常需要：

- 客户端请求号。
- 订单幂等表。
- 唯一索引。
- Redis 防重复提交令牌。

当前项目没有实现这些能力，因此简历中不能写“已完成下单幂等设计”。

## 3.7 这一部分应该怎样讲

> 下单接口由 Controller 接收 DTO 和当前用户，业务集中在 `OrdersServiceImpl.create()`。方法使用 `@Transactional(rollbackFor = Exception.class)` 开启本地事务。执行时先校验地址和购物车归属，再检查商品的审核、上架和库存状态，然后用 `LinkedHashMap` 按卖家分组。每个卖家生成一张订单主表，订单明细保存商品名、图片和单价快照，随后扣库存、删购物车。上述所有数据库写操作在同一事务内，异常会整体回滚。当前库存扣减还是先查后写，高并发下需要改成条件更新或乐观锁。

## 3.8 本部分面试题

### 面试题 1：为什么下单必须使用事务？

**回答：**

因为一次下单不只是插入一条订单，而是同时写多张表：

```text
orders
order_item
product.stock
cart
```

这些数据存在业务一致性要求。如果订单主表插入成功、明细插入失败，就会产生无法解释的订单；如果库存扣了但购物车没删，用户会重复下单；如果部分卖家订单成功、另一部分失败，用户看到的订单也不完整。因此 `OrdersServiceImpl.create()` 使用 `@Transactional(rollbackFor = Exception.class)`，让多表写操作同成功、同失败。

### 面试题 2：你们的事务范围具体包括哪些 SQL？

**回答：**

事务范围就是 `OrdersServiceImpl.create()` 方法内的数据库操作：

```text
INSERT orders
INSERT order_item
UPDATE product SET stock = ?
DELETE FROM cart WHERE id = ?
```

如果有多个卖家，会在这个事务中执行多组主从表插入。方法正常返回时提交，抛出异常时回滚。

### 面试题 3：为什么订单主表保存收货快照，明细表保存商品快照？

**回答：**

商品和地址都是可变的。卖家可能改商品名、改价格、换图片，用户也可能修改或删除地址。如果订单只保存外键，历史订单展示的会是“现在”的数据，而不是成交时的数据。因此：

- `orders` 保存收货人、电话、完整地址。
- `order_item` 保存商品名、图片、成交单价。

这叫事实快照，目的是保证历史订单可追溯。

### 面试题 4：一次下单包含多个卖家的商品，为什么要拆单？

**回答：**

订单是履约单元。不同卖家需要独立发货、确认收货和后续结算，如果合成一张订单，订单状态无法准确表达多个卖家的履约进度。因此按 `sellerId` 用 `LinkedHashMap` 分组，每个卖家生成一张订单。

### 面试题 5：下单过程中某个商品库存不足，已经执行的 SQL 会怎样？

**回答：**

Service 会抛 `BusinessException`。项目外部的 Spring 事务代理捕获到异常后，根据 `rollbackFor = Exception.class` 回滚整个事务，已经插入的订单、明细、已扣的库存和已删除的购物车都会恢复。

### 面试题 6：当前代码怎样防止超卖？答案要诚实。

**回答：**

当前代码在扣库存前做了库存比较：

```java
if (c.getQuantity() > p.getStock()) {
    throw new BusinessException(2004, "库存不足");
}
```

它能拦截串行场景下的库存不足，但不能完全防住并发超卖，因为后续扣库存是“先读库存，再写新库存”。高并发下两个请求可能读到相同库存。更安全的实现是条件更新：

```sql
UPDATE product
SET stock = stock - #{quantity}
WHERE id = #{productId}
  AND stock >= #{quantity}
```

再检查影响行数是否为 1。

### 面试题 7：如何防止用户重复提交订单？

**回答：**

当前项目没有完整幂等机制。可增加：

- 客户端请求号 + 唯一索引。
- 订单幂等表。
- Redis 防重复提交 token。
- 下单接口先检查请求号是否已处理。

不要回答“用了 `@Transactional` 就天然幂等”。事务保证原子性，不保证同一个业务请求不会执行两次。

### 面试题 8：订单号怎样生成？并发下有什么问题？

**回答：**

当前订单号是 `RX + 日期 + 当日订单数 + 1`，代码在 `OrdersServiceImpl.nextOrderNo()`。并发下两个请求可能读到相同数量，生成相同订单号。虽然 `order_no` 有唯一约束能防止最终重复落库，但会造成一个请求失败。生产中可以改 Redis `INCR`、数据库序列或雪花算法。

## 3.9 本部分八股原理

### 八股 1：事务 ACID

- **A 原子性：** 事务内操作要么全部成功，要么全部失败。
- **C 一致性：** 事务前后数据满足业务约束，例如订单、明细和库存对应。
- **I 隔离性：** 并发事务之间按隔离级别相互影响。
- **D 持久性：** 事务提交后数据持久保存。

本项目下单主要体现原子性和一致性。

### 八股 2：MySQL 四种隔离级别

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
|---|---|---|---|
| READ UNCOMMITTED | 可能 | 可能 | 可能 |
| READ COMMITTED | 不可能 | 可能 | 可能 |
| REPEATABLE READ | 不可能 | 不可能 | InnoDB 通过 MVCC/Next-Key Lock 处理 |
| SERIALIZABLE | 不可能 | 不可能 | 不可能 |

InnoDB 默认是 REPEATABLE READ。但事务隔离并不能替代库存条件更新，因为“先查再写”仍可能丢失更新。

### 八股 3：Spring `@Transactional` 的实现原理

核心是 AOP 代理：

1. Spring 容器创建 Service 时生成代理对象。
2. 调用事务方法前，代理通过事务管理器获取连接。
3. 设置 `autoCommit=false`。
4. 执行业务方法。
5. 正常返回提交，异常时回滚。

### 八股 4：`@Transactional` 什么时候会失效？

常见情况：

- 同类内部 `this.method()` 自调用，没有经过代理。
- 方法不是 Spring Bean，或对象不是由容器管理。
- 异常被方法内部 `try-catch` 吃掉，代理感知不到。
- 抛出的异常不符合回滚规则。
- 方法不是可代理的 public 方法，或代理方式与修饰符不匹配。

本项目事务方法由 Controller 从外部调用，正常经过代理。

### 八股 5：`REQUIRED` 和 `REQUIRES_NEW`

- **REQUIRED：** Spring 默认传播行为。有事务就加入，没有就新建。
- **REQUIRES_NEW：** 挂起当前事务，另开一个独立事务。

本项目没有显式配置传播行为，使用默认 `REQUIRED`。

### 八股 6：事务能保证一致，为什么还需要锁或 CAS？

事务解决的是“多条 SQL 的原子提交与回滚”，不自动解决并发覆盖。库存仍然需要：

- 条件 UPDATE。
- 乐观锁版本号。
- `SELECT ... FOR UPDATE` 悲观锁。

这三种方式解决的问题是并发写冲突，与事务边界不是同一件事。

---

## 4. 主题二：订单状态机 + 角色鉴权 + 库存补偿

## 4.1 状态定义

代码位置：`OrdersServiceImpl.java:52-59`

```text
0 待支付
1 已支付
2 已发货
3 已完成
4 已取消
```

状态迁移图：

```text
                 买家支付                卖家发货               买家收货
待支付 0  ------------------>  已支付 1  --------->  已发货 2  --------->  已完成 3
   |
   | 买家取消
   v
已取消 4
```

退款状态：

```text
0 无退款
1 退款申请中
2 已退款（当前没有完整退款处理流程）
3 退款拒绝
```

## 4.2 状态操作调用链

| 操作 | 接口 | Controller | Service | 角色 | 前置状态 | 目标状态 |
|---|---|---|---|---|---|---|
| 模拟支付 | `PUT /api/order/{id}/pay` | `OrdersController.java:68` | `OrdersServiceImpl.java:267` | 买家 | 0 | 1 |
| 卖家发货 | `PUT /api/order/{id}/ship` | `OrdersController.java:75` | `OrdersServiceImpl.java:282` | 卖家 | 1 | 2 |
| 确认收货 | `PUT /api/order/{id}/finish` | `OrdersController.java:82` | `OrdersServiceImpl.java:297` | 买家 | 2 | 3 |
| 取消订单 | `PUT /api/order/{id}/cancel` | `OrdersController.java:89` | `OrdersServiceImpl.java:313` | 买家 | 0 | 4 |
| 申请退款 | `POST /api/order/{id}/refund` | `OrdersController.java:96` | `OrdersServiceImpl.java:339` | 买家 | 非 0、非 4、无退款 | 退款状态 0 -> 1 |

## 4.3 支付

代码位置：`OrdersServiceImpl.java:267-279`

```java
Orders o = mustGet(id);
checkBuyer(o, userId);
if (o.getStatus() != STATUS_PENDING_PAY) {
    throw new BusinessException(2005, "当前状态不可支付");
}

Orders update = new Orders();
update.setId(id);
update.setStatus(STATUS_PAID);
update.setPayTime(LocalDateTime.now());
ordersMapper.updateById(update);
```

执行顺序：

1. `requireLogin`：确认有用户 ID。
2. `mustGet`：确认订单存在。
3. `checkBuyer`：确认当前用户是买家。
4. 状态判断：只能从待支付进入已支付。
5. 只构造需要更新的字段。

`Orders` 是一个新对象，只设置了 `id`、`status`、`payTime`。MyBatis-Plus 的 `updateById()` 默认只更新非 `null` 字段，所以不会把其他列覆盖成 `null`。

## 4.4 发货与确认收货

发货代码位置：`OrdersServiceImpl.java:282-294`

```java
checkSeller(o, userId);
if (o.getStatus() != STATUS_PAID) {
    throw new BusinessException(2005, "仅已支付订单可发货");
}
```

确认收货代码位置：`OrdersServiceImpl.java:297-309`

```java
checkBuyer(o, userId);
if (o.getStatus() != STATUS_SHIPPED) {
    throw new BusinessException(2005, "仅已发货订单可确认收货");
}
```

同样是订单，为什么支付用 `checkBuyer`，发货用 `checkSeller`？

- 订单表同时有 `buyerId` 和 `sellerId`。
- 支付和确认收货是买家动作。
- 发货是卖家动作。
- 只检查“已登录”远远不够，还必须检查当前用户是否是该订单对应的角色。

## 4.5 取消与库存补偿

代码位置：`OrdersServiceImpl.java:311-336`

```java
@Transactional(rollbackFor = Exception.class)
public void cancel(Long userId, Long id) {
    checkBuyer(o, userId);
    if (o.getStatus() != STATUS_PENDING_PAY) {
        throw new BusinessException(2005, "仅待支付订单可取消");
    }

    List<OrderItem> items = orderItemMapper.selectList(...);
    for (OrderItem oi : items) {
        Product p = productMapper.selectById(oi.getProductId());
        if (p != null) {
            Product stockUpdate = new Product();
            stockUpdate.setId(p.getId());
            stockUpdate.setStock(p.getStock() + oi.getQuantity());
            productMapper.updateById(stockUpdate);
        }
    }

    Orders update = new Orders();
    update.setId(id);
    update.setStatus(STATUS_CANCELLED);
    ordersMapper.updateById(update);
}
```

为什么依据 `order_item` 回补，而不是依据购物车？

- 下单成功后购物车项已经被删除。
- 订单明细才是本次成交的权威记录。
- 依据明细能把每个商品和购买数量准确加回库存。

事务边界：

```text
UPDATE product SET stock = stock + quantity  (可能多行)
UPDATE orders SET status = 4
```

如果第二个商品库存回补成功，但第三个商品更新失败，异常会触发回滚，前面已经回补的库存也会撤销。

当前实现同样存在“先查后写”的并发问题。更严谨的做法是按订单状态做条件更新，并确保同一订单只能成功取消一次。

## 4.6 退款申请

代码位置：`OrdersServiceImpl.java:339-354`

```java
if (o.getStatus() == STATUS_PENDING_PAY || o.getStatus() == STATUS_CANCELLED) {
    throw new BusinessException(2005, "当前状态不可申请退款");
}
if (o.getRefundStatus() != REFUND_NONE) {
    throw new BusinessException(2005, "已有退款申请，请勿重复申请");
}
update.setRefundStatus(REFUND_APPLYING);
```

它实现的只是“退款申请状态”，不是资金原路退回：

- 没有调用支付宝/微信退款接口。
- 没有退款审核。
- 没有退款成功后的资金流水。
- 没有幂等键。

因此项目里讲“退款防重复申请”可以，不能讲“完成真实退款”。

## 4.7 角色校验辅助方法

代码位置：`OrdersServiceImpl.java:358-381`

```java
private void requireLogin(Long userId) {
    if (userId == null) throw new BusinessException(2001, "未登录或登录已过期");
}

private Orders mustGet(Long id) {
    Orders o = ordersMapper.selectById(id);
    if (o == null) throw new BusinessException(2002, "订单不存在");
    return o;
}

private void checkBuyer(Orders o, Long userId) {
    if (!o.getBuyerId().equals(userId))
        throw new BusinessException(2003, "只能操作自己的订单");
}

private void checkSeller(Orders o, Long userId) {
    if (!o.getSellerId().equals(userId))
        throw new BusinessException(2003, "只能操作自己的订单");
}
```

这就是订单模块的统一守卫顺序：

```text
登录了吗？
-> 订单存在吗？
-> 当前用户是这笔订单的对应角色吗？
-> 当前业务状态允许这个操作吗？
-> 执行更新
```

## 4.8 状态机当前局限

当前实现是“先查状态，再更新”，不是原子状态迁移。两个并发支付请求可能都读到状态 0，然后都执行更新。生产中可以用条件更新：

```sql
UPDATE orders
SET status = 1, pay_time = NOW()
WHERE id = ? AND status = 0
```

再根据影响行数判断是否支付成功。取消、发货、确认收货也可以采用相同方式。

## 4.9 这一部分应该怎样讲

> 订单状态不是普通字段，而是业务状态机。支付只能由买家把待支付改为已支付，发货只能由卖家把已支付改为已发货，确认收货只能由买家把已发货改为已完成，取消只能处理待支付订单。每个操作都先校验登录、订单存在、角色归属和前置状态。取消订单内部还会读取订单明细并回补库存，因此放在事务中执行。当前并发控制还不够原子，后续可以把状态条件加入 UPDATE 的 WHERE 子句。

## 4.10 本部分面试题

### 面试题 1：为什么订单状态要设计成状态机，而不是随便传一个状态值更新？

**回答：**

订单状态之间不是任意跳转关系。例如已取消订单不能再次支付，待支付订单不能直接确认收货。状态机把“当前状态”和“允许执行的动作”绑定，防止非法状态覆盖。当前代码通过常量、前置状态判断和买家/卖家角色判断实现。

### 面试题 2：支付、发货、确认收货分别由谁操作？

**回答：**

- 支付：买家，`checkBuyer()`。
- 发货：卖家，`checkSeller()`。
- 确认收货：买家，`checkBuyer()`。
- 取消：买家。
- 退款申请：买家。

### 面试题 3：为什么只有待支付订单能取消？

**回答：**

待支付订单还没有进入履约流程，取消后回补库存是明确的。已支付、已发货、已完成订单涉及资金、物流或售后，不能直接用取消动作处理，应进入退款或售后流程。

### 面试题 4：取消订单为什么按 `order_item` 回补库存？

**回答：**

下单后购物车项可能已被删除，购物车不再是可靠证据。`order_item` 记录本次实际购买商品和数量，是库存补偿的权威依据。

### 面试题 5：如果取消订单时库存回补到一半失败怎么办？

**回答：**

`cancel()` 带 `@Transactional(rollbackFor = Exception.class)`，库存回补和订单状态更新在同一个事务。任何一步失败都会整体回滚，不会留下部分商品已回补、订单却未取消的状态。

### 面试题 6：如何防止同一订单被并发支付或并发取消？

**回答：**

当前是“先查状态，再更新”，存在并发窗口。更严谨的做法是把前置状态放进 UPDATE：

```sql
UPDATE orders
SET status = 1, pay_time = NOW()
WHERE id = ? AND status = 0
```

检查影响行数，只有 1 行才认为支付成功。取消和发货同理。

### 面试题 7：为什么支付、发货、确认收货方法没有加 `@Transactional`？

**回答：**

这三个方法都只更新订单一张表的一条记录。单条 SQL 本身由数据库执行原子性，因此在当前实现中没有额外加事务。更严谨的状态迁移仍应使用条件 UPDATE 保证“校验状态 + 更新状态”是一个原子操作。

### 面试题 8：退款接口实现到了什么程度？

**回答：**

当前只实现了退款申请标记：

- 校验买家身份。
- 待支付和已取消不能申请。
- 已有申请不能重复申请。
- 设置 `refundStatus=1`。

没有真实资金退回、退款审核、第三方支付回调和对账，不能讲成完整退款系统。

## 4.11 本部分八股原理

### 八股 1：状态机思想

状态机由三部分组成：

```text
当前状态 + 事件/动作 + 下一个状态
```

订单例子：

```text
待支付 + 买家支付 -> 已支付
已支付 + 卖家发货 -> 已发货
已发货 + 买家收货 -> 已完成
待支付 + 买家取消 -> 已取消
```

用 if/else 也能实现，但状态多了以后容易漏掉非法路径。可以使用枚举、状态表或状态模式统一管理。

### 八股 2：乐观锁与悲观锁

**乐观锁：**

- 假设冲突较少。
- 通过版本号或条件更新实现。
- 失败后重试或提示用户。

```sql
UPDATE orders SET status=1 WHERE id=? AND status=0;
```

**悲观锁：**

- 假设冲突较多。
- 用 `SELECT ... FOR UPDATE` 锁住记录。
- 并发度高时容易阻塞。

### 八股 3：丢失更新

两个事务读到同一旧值，然后各自写回新值，后写覆盖前写。

库存例子：

```text
初始库存 10
A 读到 10，准备减 2
B 读到 10，准备减 3
A 写 8
B 写 7
```

实际应剩 5，结果却是 7。条件 UPDATE 或版本号可以避免。

### 八股 4：CAS

CAS 是 Compare-And-Swap：

```sql
UPDATE product
SET stock = stock - 2
WHERE id = 1 AND stock >= 2;
```

数据库判断当前库存是否仍满足条件，只有满足才更新。

### 八股 5：幂等

幂等的含义是同一个请求执行一次和执行多次，最终业务结果一致。

- GET、PUT、DELETE 通常要求幂等。
- POST 下单通常需要额外幂等设计。
- `@Transactional` 不是幂等机制。

常见实现：

- 唯一业务号。
- 幂等表。
- Redis token。
- 数据库唯一索引。

### 八股 6：订单状态索引

常见查询包括按买家、卖家、状态、创建时间筛选。可考虑联合索引：

```sql
INDEX idx_buyer_status_time (buyer_id, status, create_time)
INDEX idx_seller_status_time (seller_id, status, create_time)
```

实际索引要结合真实查询频率和数据量设计，不能只看字段是否存在。

---

## 5. 主题三：纵深权限与 IDOR 防护

## 5.1 三个不同层次

必须区分：

1. **认证 Authentication：** 你是谁？
2. **授权 Authorization：** 你有没有权限访问这个接口？
3. **数据归属校验：** 这条数据是不是你的？

例如用户 A 已登录且是普通用户：

- 通过 JWT 认证：说明 A 是有效用户。
- 普通业务接口要求登录：A 可以访问购物车接口。
- 但 A 不能操作 B 的购物车项，这是数据归属校验。

## 5.2 JWT 请求链路

关键代码：

- `config/security/JwtAuthenticationFilter.java:38-80`
- `config/security/UserDetailsServiceImpl.java:39-47`
- `config/SecurityConfig.java:81-157`
- `controller/BaseController.java:25-43`

整体链路：

```text
请求携带 Authorization: Bearer xxx
-> JwtAuthenticationFilter 取 token
-> JwtUtil.parseToken(token) 验签、检查过期
-> 从 claims 取 userId
-> UserDetailsServiceImpl.loadUserById(userId) 查最新用户
-> LoginUser.fromUser(user) 生成权限集合
-> 写入 SecurityContextHolder
-> SecurityConfig 判断当前 URL 是否允许访问
-> Controller 通过 getCurrentUserId() 读取当前用户
```

### 为什么过滤器要取 token

```java
String header = request.getHeader("Authorization");
if (header == null || !header.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}
String token = header.substring(7);
```

- 标准格式为 `Authorization: Bearer <token>`。
- `substring(7)` 去掉 `"Bearer "`。
- 没有 token 时不会抛出异常，而是继续走过滤链，后续由 Security 判断该接口是否需要登录。

### 为什么解析失败也放行

```java
Claims claims = jwtUtil.parseToken(token);
if (claims == null) {
    filterChain.doFilter(request, response);
    return;
}
```

解析失败时不向 `SecurityContext` 写入登录态。对于需要登录的接口，Security 后续会返回 401；对于公开接口，仍然可以正常访问。

### 为什么还要按 userId 查数据库

JWT 只负责证明“这个 token 是系统签发的”，但如果用户已被删除或禁用，token 在过期前仍然有效。因此过滤器调用：

```java
userDetailsService.loadUserById(userId);
```

`UserDetailsServiceImpl` 会重新查询 `user` 表，并检查 `status`。

重要事实：本项目 JWT 并不直接信任 token 里的角色，而是按 `userId` 重新加载最新用户和角色。因此后续 `@PreAuthorize` 使用的是数据库当前角色。

## 5.3 SecurityConfig 的两层规则

代码位置：`config/SecurityConfig.java:81-157`

### 第一层：哪些 URL 公开

```java
.requestMatchers(HttpMethod.GET, "/api/product").permitAll()
.requestMatchers(HttpMethod.GET, "/api/product/{id}").permitAll()
.requestMatchers(HttpMethod.GET, "/api/qa").permitAll()
.requestMatchers(HttpMethod.GET, "/api/qa/{id}").permitAll()
```

公开接口不需要登录。

### 第二层：其余接口都必须认证

```java
.anyRequest().authenticated()
```

例如购物车、订单、发布商品、回答问题都要求登录。

### 为什么请求没有 Session

```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

服务端不保存登录 Session，每次请求都携带 JWT。

### 一个必须知道的路径匹配细节

`SecurityConfig` 中写了：

```java
.requestMatchers(HttpMethod.GET, "/api/product/{id}").permitAll()
```

`{id}` 在 Security 的路径匹配中也会匹配 `my`，因此 `/api/product/my` 在过滤器授权层可能落入 `permitAll()`。真正阻止未登录请求的是 Controller 中的 `getCurrentUserId()`：它取不到用户时抛业务异常。

这意味着：

- 未登录请求仍然不能读到“我的货源”，业务层会兜底。
- 但它不一定走 Security 的 401 JSON，而可能走 `BusinessException` 的 400 响应。
- 更严谨的配置应把 `/api/product/my` 写在 `/api/product/{id}` 前面，并显式设置为 `authenticated()`。

专家模块的 `profile` 就采用了这种“精确路径写在 `{id}` 前面”的方式，见 `config/SecurityConfig.java:114-119`。

## 5.4 管理员方法鉴权

代码位置：

- `controller/AdminProductController.java:26`

```java
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController extends BaseController {
```

`@PreAuthorize` 的原理：

1. Spring Security 开启方法级安全。
2. Controller Bean 被 AOP 代理。
3. 调用 Controller 方法前，代理执行 SpEL 表达式 `hasRole('ADMIN')`。
4. Spring Security 检查当前 `Authentication.getAuthorities()`。
5. 没有 `ROLE_ADMIN` 就拒绝。

`hasRole('ADMIN')` 会自动补 `ROLE_` 前缀，实际匹配 `ROLE_ADMIN`。

## 5.5 Service 数据库二次确认

代码位置：

- `service/impl/AdminProductServiceImpl.java:128-136`

```java
private void requireAdmin(Long userId) {
    if (userId == null) {
        throw new BusinessException(2001, "未登录或登录已过期");
    }
    User user = userMapper.selectById(userId);
    if (user == null || !Objects.equals(user.getRole(), ROLE_ADMIN)) {
        throw new BusinessException(2003, "仅管理员可操作");
    }
}
```

同一个模式也出现在：

- `AdminDemandServiceImpl.java:75`
- `AdminOrderServiceImpl.java:75`

为什么说它是纵深防御？

- Controller 层先判断“当前登录态是否有管理员权限”。
- Service 层再从业务边界确认“数据库当前角色是否仍是管理员”。
- 本项目 JWT 过滤器本身也会重新查库，所以这不是唯一保护，而是冗余防线。

面试中应讲“双层确认”，不要夸大成“只有 Service 这次查库才能挡住旧 token”。

## 5.6 资源级权限：防水平越权（IDOR）

IDOR 指攻击者修改 URL 或请求体中的资源 ID，尝试操作别人的数据。

### 商品归属

代码位置：`ProductServiceImpl.java:154-161`

```java
Product exist = productMapper.selectById(id);
if (exist == null) {
    throw new BusinessException(2002, "商品不存在");
}
if (!exist.getSellerId().equals(userId)) {
    throw new BusinessException(2003, "只能修改自己的货源");
}
```

如果只检查“商品存在”，用户 A 可以把 URL 中的商品 ID 换成用户 B 的商品，修改 B 的价格或库存。

### 需求归属

代码位置：`DemandServiceImpl.java:134` 以后

```java
if (!exist.getBuyerId().equals(userId)) {
    throw new BusinessException(2003, "只能修改自己的需求");
}
```

### 购物车归属

代码位置：`CartServiceImpl.java:174-183`

```java
private Cart getOwnedCart(Long userId, Long id) {
    Cart cart = cartMapper.selectById(id);
    if (cart == null) {
        throw new BusinessException(2002, "购物车项不存在");
    }
    if (!cart.getUserId().equals(userId)) {
        throw new BusinessException(2003, "只能操作自己的购物车");
    }
    return cart;
}
```

修改数量、删除、勾选都会经过这个方法。

### 订单归属

代码位置：`OrdersServiceImpl.java:372-381`

```java
checkBuyer(o, userId);
checkSeller(o, userId);
```

支付、收货、取消、退款要求是买家；发货要求是卖家。

### 问答归属

代码位置：`QaServiceImpl.java:310-315`

```java
private void checkQuestioner(Qa qa, Long userId) {
    if (!qa.getQuestionerId().equals(userId)) {
        throw new BusinessException(2003, "只能操作自己的问题");
    }
}
```

删除回答时还会单独检查 `replierId`。

## 5.7 这一部分应该怎样讲

> 认证、授权和数据归属是三层不同问题。JWT 过滤器负责从 token 得到当前用户并加载最新用户状态，SecurityConfig 控制哪些接口公开、哪些必须登录，管理端再通过 `@PreAuthorize` 检查管理员角色，Service 内还会查库做一次冗余角色确认。对于普通业务数据，每个写操作还要比较资源 owner 和当前用户，例如商品的 sellerId、购物车的 userId、订单的 buyerId/sellerId，从而防止攻击者篡改资源 ID 操作他人数据。

## 5.8 本部分面试题

### 面试题 1：JWT 是什么？由哪几部分组成？

**回答：**

JWT 是三段式字符串：

```text
Header.Payload.Signature
```

- Header：声明算法和类型。
- Payload：保存 claims，例如用户 ID、签发时间、过期时间。
- Signature：用服务端密钥对前两部分签名。

JWT 的 Payload 只是 Base64URL 编码，不是加密。不要在里面放密码等敏感信息。

### 面试题 2：项目为什么采用 JWT，而不是 Session？

**回答：**

JWT 更适合前后端分离和无状态接口：

- 服务端不需要保存 Session。
- 请求通过 `Authorization: Bearer token` 携带身份。
- 服务端验签后即可识别用户。

代价是：

- 注销和强制下线更麻烦。
- token 泄露后在过期前可能被使用。
- 需要处理刷新 token、黑名单或短过期策略。

### 面试题 3：JWT 过滤器怎样把用户信息交给 Controller？

**回答：**

链路是：

```text
JwtAuthenticationFilter 解析 JWT
-> 取 userId
-> UserDetailsServiceImpl.loadUserById()
-> 构造 UsernamePasswordAuthenticationToken
-> 写入 SecurityContextHolder
-> BaseController.getCurrentUserId() 读取 LoginUser
```

一次请求处理结束后，Spring Security 会清理线程上下文，避免线程复用导致身份串号。

### 面试题 4：Authentication 和 Authorization 有什么区别？

**回答：**

- Authentication 认证：确认你是谁。
- Authorization 授权：确认你能不能访问某个接口或资源。

登录成功只完成认证。普通用户访问管理员接口、用户 A 操作用户 B 的购物车，都是授权问题。

### 面试题 5：`@PreAuthorize("hasRole('ADMIN')")` 的原理是什么？

**回答：**

Spring Security 开启方法级安全后，会给 Controller 创建 AOP 代理。调用方法前执行 SpEL 表达式，检查 `Authentication` 中的权限集合。`hasRole('ADMIN')` 会自动加 `ROLE_` 前缀，最终匹配 `ROLE_ADMIN`。

### 面试题 6：为什么管理员接口已经在 Controller 加了鉴权，Service 还要查库？

**回答：**

Controller 的方法是接口入口控制，Service 二次查库是业务边界的冗余防线。本项目 JWT 过滤器本身也会按 `userId` 查询最新用户和角色，所以不能把 Service 二次查询说成唯一的角色过期保护。准确表述是“方法鉴权 + Service 数据库复核形成纵深防御”。

### 面试题 7：什么是 IDOR/水平越权？

**回答：**

用户已经合法登录，但通过修改资源 ID 操作别人的数据。例如：

```text
PUT /api/cart/10
```

如果用户 A 把购物车 ID 改成用户 B 的 ID，而代码只判断购物车存在，就会发生水平越权。正确做法是继续判断：

```java
cart.getUserId().equals(currentUserId)
```

### 面试题 8：401 和 403 有什么区别？

**回答：**

- 401 Unauthorized：没有登录、token 无效或过期。
- 403 Forbidden：已登录，但没有权限访问。

项目 Security 的入口和拒绝处理器分别返回 401 和 403。但注意业务层 `BusinessException` 统一被全局异常处理成 HTTP 400，因此某些业务权限失败可能表现为业务码 2003 + HTTP 400。

### 面试题 9：为什么 Security 路径匹配顺序重要？

**回答：**

Spring Security 按配置顺序匹配。`/api/product/{id}` 会把 `/api/product/my` 也匹配进去。项目目前依靠 `getCurrentUserId()` 在 Controller 层兜底，但严谨写法应把 `/api/product/my` 放在 `{id}` 前，并设置 `authenticated()`。专家 `profile` 就采用了这种顺序。

## 5.9 本部分八股原理

### 八股 1：JWT 签名和加密的区别

- 签名解决“内容有没有被篡改”。
- 加密解决“内容能不能被看到”。
- JWT 常用签名，Payload 默认可解码。
- 签名密钥不能泄露。

### 八股 2：JWT 的优点和缺点

**优点：**

- 无状态。
- 跨服务传递方便。
- 前后端分离友好。

**缺点：**

- 主动失效困难。
- token 一旦泄露，在有效期内有风险。
- token 太长会增加请求体积。

常见方案是短有效期 access token + refresh token + 黑名单。

### 八股 3：Spring Security 过滤器链

请求先经过一系列 Security Filter，再进入 DispatcherServlet。项目通过：

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

把 JWT 过滤器放在用户名密码认证过滤器之前。

### 八股 4：SecurityContextHolder 与 ThreadLocal

`SecurityContextHolder` 默认会把当前认证信息放在 ThreadLocal 中，方便同一请求线程读取。Web 容器会复用线程，因此请求结束后必须清理上下文，Spring Security 的过滤器链负责这件事。

### 八股 5：CSRF 与 CORS

- **CSRF：** 攻击者诱导已登录用户发起非预期请求。传统 Cookie Session 场景风险更高。
- **CORS：** 浏览器跨域访问控制。

项目使用 Authorization Header 传 JWT，并关闭 Session，因此禁用了 CSRF；同时配置 CORS 允许本地前端开发。

### 八股 6：`hasRole` 和 `hasAuthority`

- `hasRole('ADMIN')` 会自动补 `ROLE_`，匹配 `ROLE_ADMIN`。
- `hasAuthority('ROLE_ADMIN')` 按完整字符串匹配。

写错前缀会导致明明有角色却一直 403。

### 八股 7：Spring Security 方法鉴权原理

`@PreAuthorize` 通过 AOP 代理执行，方法调用前先做表达式判断。方法注解能生效的前提是：

- 开启了方法级安全。
- 对象来自 Spring 容器。
- 调用经过代理。

### 八股 8：SQL 注入与 MyBatis 参数绑定

MyBatis 的 `#{}` 使用预编译参数占位符，不直接拼接 SQL，能降低 SQL 注入风险。`${}` 是字符串拼接，除非非常明确且安全，否则不要使用。

### 八股 9：RBAC 与数据权限

RBAC 是角色权限控制，例如管理员、专家、普通用户。数据权限控制“能看哪些数据”，例如只能操作自己的商品和订单。本项目同时使用两者：`@PreAuthorize` 管角色，owner 校验管数据归属。

---

## 6. 主题四：N+1 查询优化与 Map 回填

## 6.1 什么是 N+1

假设商品列表一页有 10 条：

```java
List<Product> products = productMapper.selectPage(...);

for (Product p : products) {
    Category c = categoryMapper.selectById(p.getCategoryId());
    User seller = userMapper.selectById(p.getSellerId());
}
```

SQL 次数：

```text
1 次查商品分页
+ 10 次查分类
+ 10 次查卖家
= 21 次查询
```

一页 100 条就是 201 次，这就是 N+1。

## 6.2 商品列表实际实现链

```text
GET /api/product
-> ProductController.pageList()                 ProductController.java:42
-> ProductServiceImpl.pageList()                ProductServiceImpl.java:56
   -> productMapper.selectPage()                ProductServiceImpl.java:74
   -> categoryMap(page.getRecords())            ProductServiceImpl.java:78
      -> 收集 categoryId 去重
      -> categoryMapper.selectBatchIds()
      -> 转成 id -> Category Map
   -> userMap(page.getRecords())                ProductServiceImpl.java:79
      -> 收集 sellerId 去重
      -> userMapper.selectBatchIds()
      -> 转成 id -> User Map
   -> records.stream().map(toListVO)            ProductServiceImpl.java:81
-> PageResult.of(page, list)                     PageResult.java:30
```

## 6.3 先分页，再批量查关联数据

代码位置：`ProductServiceImpl.java:74-83`

```java
Page<Product> page = productMapper.selectPage(new Page<>(num, size), wrapper);
List<Product> records = page.getRecords();

Map<Long, Category> categoryMap = categoryMap(records);
Map<Long, User> userMap = userMap(records);

List<ProductListVO> list = records.stream()
        .map(p -> toListVO(p, categoryMap, userMap))
        .collect(Collectors.toList());
```

执行顺序很重要：

- 先分页，只拿当前页的商品。
- 再收集当前页商品的关联 ID。
- 只批量查询当前页需要的分类和卖家。

## 6.4 `categoryMap()` 逐行解释

代码位置：`ProductServiceImpl.java:251-261`

```java
Set<Long> ids = records.stream()
        .map(Product::getCategoryId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
```

- `map` 把 `Product` 转成 `categoryId`。
- `filter` 去掉空分类 ID。
- `Collectors.toSet()` 去重。

例如 10 个商品只有 3 个分类：

```text
10 个 categoryId
-> 去重成 3 个
-> 一次 selectBatchIds 查 3 条
```

```java
if (ids.isEmpty()) {
    return Collections.emptyMap();
}
```

如果当前页没有分类 ID，就不执行 `WHERE id IN ()` 这种无意义 SQL。

```java
return categoryMapper.selectBatchIds(ids).stream()
        .collect(Collectors.toMap(Category::getId, Function.identity()));
```

把：

```text
List<Category> [
  {id=1, name=水果},
  {id=2, name=蔬菜}
]
```

转换成：

```text
Map<Long, Category> {
  1 -> {id=1, name=水果},
  2 -> {id=2, name=蔬菜}
}
```

这样 `categoryMap.get(p.getCategoryId())` 的复杂度接近 O(1)，不需要再扫描 List。

## 6.5 `userMap()` 逻辑相同

代码位置：`ProductServiceImpl.java:264-274`

```java
Set<Long> ids = records.stream()
        .map(Product::getSellerId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
```

多个商品可能属于同一个卖家，去重后只查一次。

## 6.6 VO 回填

代码位置：`ProductServiceImpl.java:277-301`

```java
Category c = categoryMap.get(p.getCategoryId());
vo.setCategoryName(c == null ? null : c.getName());

User seller = userMap.get(p.getSellerId());
if (seller != null) {
    UserBriefVO sv = new UserBriefVO();
    sv.setId(seller.getId());
    sv.setRealName(seller.getRealName());
    sv.setFarmName(seller.getFarmName());
    sv.setAvatar(seller.getAvatar());
    sv.setRole(seller.getRole());
    vo.setSeller(sv);
}
```

这里做了两件事：

1. 把关联数据填进列表 VO。
2. 只暴露前端需要的卖家字段，不直接把完整 `User` 返回。

## 6.7 MyBatis-Plus 分页插件

代码位置：`config/MyBatisPlusConfig.java:17-23`

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
}
```

如果没有分页拦截器，`selectPage()` 不会自动追加 `LIMIT`，也不会正确统计总数。

## 6.8 动态查询条件

代码位置：`ProductServiceImpl.java:65-72`

```java
wrapper.eq(Product::getAuditStatus, AUDIT_PASSED)
        .eq(Product::getStatus, STATUS_ON)
        .and(StringUtils.hasText(kw), w -> w.like(Product::getName, kw)
                .or().like(Product::getOrigin, kw))
        .eq(categoryId != null, Product::getCategoryId, categoryId)
        .eq(StringUtils.hasText(org), Product::getOrigin, org)
        .orderByDesc(Product::getCreateTime);
```

布尔开关条件：

```java
eq(categoryId != null, Product::getCategoryId, categoryId)
```

- 第一个参数是 `true`：拼接这个条件。
- 第一个参数是 `false`：跳过这个条件。

为什么 OR 要放进 `and(...)`？

错误写法：

```sql
WHERE audit_status = 1
  AND status = 1
  AND name LIKE '%苹果%'
  OR origin LIKE '%苹果%'
```

由于 `AND` 优先级高于 `OR`，实际含义会变成：

```sql
WHERE (audit_status = 1 AND status = 1 AND name LIKE '%苹果%')
   OR origin LIKE '%苹果%'
```

这样可能搜出未审核或下架商品。正确写法是：

```sql
WHERE audit_status = 1
  AND status = 1
  AND (name LIKE '%苹果%' OR origin LIKE '%苹果%')
```

## 6.9 这一部分应该怎样讲

> 商品列表先通过 MyBatis-Plus 分页拿到当前页数据，再收集 categoryId 和 sellerId，过滤空值、去重后调用 selectBatchIds，一次性查询分类和卖家，并把结果转成 Map。组装 VO 时直接通过 Map 取关联对象，避免逐行查询。分页本身由 PaginationInnerInterceptor 改写 SQL。关键字搜索多个字段时，用 and(...) 把 OR 包起来，防止破坏审核和上架条件。

## 6.10 本部分面试题

### 面试题 1：什么是 N+1 查询？

**回答：**

先查询 N 条主记录，再针对每一条主记录分别查询关联数据，总共产生 1 + N 次或更多 SQL。项目商品列表如果逐条查分类和卖家，一页 10 条就会产生 21 次查询。当前实现是分页查商品，再各用一次批量查询查分类和卖家，SQL 次数近似固定。

### 面试题 2：为什么要先分页，再批量查关联？

**回答：**

如果先查全部关联数据，数据规模不可控。先分页拿到当前页商品，再收集当前页的 `categoryId` 和 `sellerId`，只查询这一页需要的关联记录。

### 面试题 3：`selectBatchIds` 为什么比循环 `selectById` 好？

**回答：**

`selectBatchIds` 一次生成 `WHERE id IN (...)`，减少网络往返和 SQL 次数。循环调用会产生 N 次数据库交互。批量大小也要控制，ID 太多时 `IN` 列表过大会影响 SQL 解析和执行计划。

### 面试题 4：为什么把 List 转成 Map？

**回答：**

关联数据需要按 ID 回填。List 每次查找要遍历，Map 平均 O(1)。转换后可以直接：

```java
categoryMap.get(product.getCategoryId())
```

### 面试题 5：批量查询和 JOIN 应该怎么选？

**回答：**

- JOIN 一次 SQL 完成，减少数据库往返，但结果可能因一对多关系产生重复行，扩展字段时容易变复杂。
- 批量查询把主表和关联表解耦，便于 VO 组装和缓存，但 SQL 次数更多。

项目列表主要是一对一关联，选择两次批量查询配合 Map 回填，逻辑清晰且可控。

### 面试题 6：MyBatis-Plus 分页插件做了什么？

**回答：**

`selectPage()` 传入 `Page` 对象后，`PaginationInnerInterceptor` 会：

1. 改写原 SQL，追加 `LIMIT`。
2. 生成并执行 `COUNT` 查询。
3. 把总数等信息写回 `Page`。

没有这个拦截器，分页行为不会正确生效。

### 面试题 7：为什么多字段 OR 搜索要用括号包起来？

**回答：**

因为 SQL 中 `AND` 优先级高于 `OR`。如果不加括号，关键字 OR 可能把前面的审核、上架条件短路，导致未审核或下架商品被查出来。

### 面试题 8：`LIKE '%关键字%'` 有什么性能问题？

**回答：**

前置 `%` 让普通 B+Tree 索引难以利用，因为索引按前缀有序，无法快速定位任意中间子串。数据量大时可能需要全文索引、搜索引擎或专门的检索服务。当前项目适合教学和小数据量，不等于高并发搜索方案。

### 面试题 9：深分页为什么慢？怎样优化？

**回答：**

`LIMIT 100000, 20` 往往需要扫描并丢弃前 100000 行。可以改游标/Keyset 分页：

```sql
WHERE create_time < #{lastCreateTime}
ORDER BY create_time DESC
LIMIT 20
```

也可以使用覆盖索引减少回表。当前项目使用普通分页，没有做深分页优化。

## 6.11 本部分八股原理

### 八股 1：B+Tree 索引

InnoDB 常用 B+Tree：

- 非叶子节点存索引键。
- 叶子节点存数据或主键。
- 叶子节点有序并相连，适合范围查询和排序。

主键索引通常是聚簇索引，二级索引叶子节点存主键，再通过主键回表查询整行。

### 八股 2：最左前缀原则

联合索引：

```sql
INDEX idx_a_b_c (a, b, c)
```

通常可以支持：

```text
(a)
(a, b)
(a, b, c)
```

不能直接高效支持只按 `b` 或 `c` 查询。范围条件后面的列也未必能继续用于精确索引查找。

### 八股 3：覆盖索引

查询需要的列都包含在索引中，不需要回表。例如：

```sql
SELECT id, name FROM product WHERE category_id = ?
```

如果存在 `(category_id, id, name)` 索引，就可能直接通过索引返回。

### 八股 4：聚簇索引和二级索引

- 聚簇索引：叶子节点直接保存整行数据，通常是主键。
- 二级索引：叶子节点保存索引列和主键值，可能还要回表。

### 八股 5：HashMap 平均 O(1) 的原因

HashMap 根据 key 的哈希值定位桶，理想情况下直接定位目标元素，因此平均查询复杂度为 O(1)。本项目把关联 List 转成 `Map<Long, Entity>`，避免逐行扫描。

### 八股 6：分页 COUNT 的成本

带复杂条件的 `COUNT(*)` 也需要扫描和过滤。数据量大时可以考虑：

- 覆盖索引。
- 近似总数。
- 游标分页。
- 缓存总数。

### 八股 7：动态 SQL

MyBatis-Plus 条件构造器的布尔开关本质上是在 Java 层决定是否拼接条件，相当于 XML 中的 `<if>`。使用 Lambda 方法引用可以让字段改名时编译期报错。

---

## 7. 主题五：商品全生命周期治理

## 7.1 两个状态不能混

商品有两个状态字段：

| 字段 | 含义 | 取值 |
|---|---|---|
| `audit_status` | 内容是否审核通过 | 0 待审核，1 通过，2 驳回 |
| `status` | 卖家是否上架 | 0 下架，1 上架 |

公开列表必须同时满足：

```java
audit_status = 1
status = 1
```

区别：

- `audit_status` 管“平台是否允许展示”。
- `status` 管“卖家是否继续销售”。

## 7.2 发布商品链路

```text
POST /api/product
-> ProductController.create()                    ProductController.java:59
-> @Validated(ValidateGroup.Create.class)
-> ProductServiceImpl.create()                   ProductServiceImpl.java:117
   -> 校验名称
   -> 校验分类
   -> 校验价格
   -> 校验库存
   -> 校验主图
   -> 强制设置 sellerId
   -> 强制设置 auditStatus=0
   -> 强制设置 status=1
   -> productMapper.insert()
-> Result.success(Map.of("id", id))
```

关键代码：`ProductServiceImpl.java:136-150`

```java
p.setSellerId(userId);
p.setAuditStatus(AUDIT_PENDING);
p.setStatus(STATUS_ON);
productMapper.insert(p);
```

为什么必须由服务端覆盖？

- 如果使用前端传入的 `sellerId`，用户就能冒用他人身份发布商品。
- 如果允许前端传入 `auditStatus=1`，就能绕过审核。
- 新商品虽然 `status=1`，但公开列表还要求 `auditStatus=1`，所以审核前不会展示。

## 7.3 修改商品与归属校验

代码位置：`ProductServiceImpl.java:154-185`

执行顺序：

1. 查询商品。
2. 商品不存在，返回 2002。
3. 商品 `sellerId` 不是当前用户，返回 2003。
4. 只校验请求中实际传入的字段。
5. 组装只包含非空字段的更新对象。

```java
Product update = new Product();
update.setId(id);
update.setName(StringUtils.hasText(dto.getName()) ? dto.getName().trim() : null);
update.setPrice(dto.getPrice());
...
productMapper.updateById(update);
```

`updateById()` 默认忽略 `null` 字段，所以：

```text
只传 price -> UPDATE product SET price = ? WHERE id = ?
```

局限：

- 无法通过 `updateById()` 把字段改成 `null`。
- 如果想把描述清空，需要 `UpdateWrapper` 显式 `set("description", null)`。

## 7.4 卖家上下架

代码位置：`ProductServiceImpl.java:189-204`

```java
if (status == null || (status != 0 && status != 1)) {
    throw new BusinessException(2001, "状态参数错误");
}
Product exist = productMapper.selectById(id);
if (!exist.getSellerId().equals(userId)) {
    throw new BusinessException(2003, "只能操作自己的货源");
}
Product update = new Product();
update.setId(id);
update.setStatus(status);
productMapper.updateById(update);
```

卖家上下架只修改 `status`，不修改 `auditStatus`。因此商品审核通过后，卖家可以先下架再上架，不需要重新审核。

## 7.5 管理员审核通过

代码位置：`AdminProductServiceImpl.java:73-83`

```java
requireAdmin(userId);
Product exist = mustGet(id);
if (exist.getAuditStatus() != AUDIT_PENDING) {
    throw new BusinessException(2005, "仅待审核商品可审核");
}
Product update = new Product();
update.setId(id);
update.setAuditStatus(AUDIT_PASSED);
update.setAuditRemark(null);
productMapper.updateById(update);
```

注意第 82 行的真实问题：

```java
update.setAuditRemark(null);
```

由于 `updateById()` 忽略 `null`，这一行不会真正清空数据库中的审核备注。如果要清空，需要使用 `UpdateWrapper` 显式设置。

## 7.6 管理员驳回

代码位置：`AdminProductServiceImpl.java:87-103`

校验顺序：

1. 当前用户是管理员。
2. 商品存在。
3. 驳回原因非空。
4. 驳回原因不超过 255 字。
5. 商品必须待审核。
6. 更新审核状态为 2，写入驳回原因。

为什么“必须待审核”？

- 防止已经通过或已经驳回的商品被重复审核。
- 防止管理员误操作覆盖已有审核结果。

## 7.7 权限区别

| 操作者 | 能否操作他人商品 | 额外检查 |
|---|---|---|
| 卖家 | 不能 | 必须校验 `sellerId` |
| 管理员 | 可以 | 必须校验管理员角色 |

管理员不需要校验商品归属，因为管理员本来就是管理全平台数据。但它必须先通过管理员鉴权。

## 7.8 这一部分应该怎样讲

> 商品有审核和上下架两个正交状态。发布时 sellerId 和 auditStatus 都由服务端强制写入，不能信任前端。公开列表同时筛选审核通过和上架。卖家修改、上下架、删除时校验 sellerId；管理员则通过 @PreAuthorize 和 Service 查库确认角色后操作全平台商品。管理员审核只允许处理待审核记录，驳回必须给出审核意见。当前 updateById 忽略 null，所以审核通过时清空 auditRemark 实际上不会生效。

## 7.9 本部分面试题

### 面试题 1：为什么商品需要审核状态和上下架状态两个字段？

**回答：**

两个状态表达不同业务含义：

- `audit_status`：平台是否允许商品展示，涉及内容合规。
- `status`：卖家是否继续销售，涉及经营选择。

如果合并成一个状态，就无法表达“审核已经通过，但卖家暂时下架”或“卖家已上架，但平台尚未审核”。公开列表必须同时满足 `audit_status=1` 和 `status=1`。

### 面试题 2：发布商品时为什么 sellerId 和 auditStatus 不能由前端传入？

**回答：**

前端参数不可信。如果由前端传 `sellerId`，用户可以冒充其他卖家；如果由前端传 `auditStatus=1`，用户可以绕过审核。因此服务端必须强制设置：

```java
p.setSellerId(userId);
p.setAuditStatus(AUDIT_PENDING);
p.setStatus(STATUS_ON);
```

### 面试题 3：管理员审核为什么只允许处理待审核商品？

**回答：**

审核是状态迁移，不是普通字段修改。只允许 `0 -> 1` 或 `0 -> 2`，可以避免已通过、已驳回商品被重复审核覆盖。更严格的并发实现应使用条件更新：

```sql
UPDATE product
SET audit_status = 1
WHERE id = ? AND audit_status = 0
```

并判断影响行数。

### 面试题 4：`updateById()` 为什么可以只修改传入字段？

**回答：**

MyBatis-Plus 默认字段更新策略会忽略 `null`。新建一个只设置 `id` 和 `price` 的 Product 对象后，更新 SQL 通常只包含 `price`。

代价是：

- 不能通过 `updateById()` 把字段改成 `NULL`。
- 空字符串和 `null` 的语义要额外处理。

项目审核通过时 `update.setAuditRemark(null)` 不会真正清空审核备注，就是这个问题。

### 面试题 5：商品物理删除后，历史订单怎样保证可追溯？

**回答：**

历史订单不依赖商品表当前数据，订单明细已经保存 `productName`、`productImage`、`price` 快照。因此商品被物理删除后，订单仍能展示成交信息。生产中更常见的是软删除或状态停用，避免直接丢失业务关联。

### 面试题 6：卖家和管理员操作商品有什么区别？

**回答：**

- 卖家只能操作 `sellerId=当前用户` 的商品，管理员可以操作全平台商品。
- 卖家上下架只改 `status`。
- 管理员可审核、驳回、强制上下架和删除。
- 两类接口都要鉴权，但检查维度不同：卖家检查 owner，管理员检查角色。

### 面试题 7：为什么审核通过和上下架不是一次操作？

**回答：**

审核通过表示平台允许展示，但卖家仍可以选择下架。上架也不代表平台审核通过。两个维度组合后，公开列表才能正确控制“合规且可售”。

### 面试题 8：如果管理员误把商品审核通过，怎样设计撤回？

**回答：**

可以增加“审核撤回/下架”动作和审核日志，记录操作人、前后状态、原因和时间。当前项目只有审核通过、驳回和强制上下架，没有完整审核历史表。

## 7.10 本部分八股原理

### 八股 1：RESTful 方法与幂等

- GET：查询，幂等。
- POST：新增或提交，通常不幂等。
- PUT：整体更新或替换，语义上幂等。
- PATCH：部分更新，不一定幂等。
- DELETE：删除，语义上幂等。

商品审核和上下架使用 PUT，但当前没有请求幂等键；重复提交可能被状态判断拦截，状态判断不够原子的地方仍有并发窗口。

### 八股 2：软删除与硬删除

**软删除：**

- 增加 `deleted` 标记。
- 查询时过滤。
- 可恢复，便于审计。

**硬删除：**

- 直接 `DELETE`。
- 数据不可恢复。
- 必须考虑关联数据和历史快照。

项目商品删除是物理删除。订单明细快照能保留成交信息，但商品本身不可恢复。

### 八股 3：乐观锁

乐观锁常见实现：

```sql
UPDATE product
SET audit_status = 1, version = version + 1
WHERE id = ? AND audit_status = 0 AND version = ?;
```

或者直接把前置状态放进 WHERE：

```sql
UPDATE product SET audit_status = 1 WHERE id = ? AND audit_status = 0;
```

### 八股 4：索引和查询

商品常见条件：

```text
audit_status
status
category_id
origin
create_time
```

可以考虑组合索引和排序需求，但要注意：

- 索引不是越多越好。
- 范围条件会影响后续列利用。
- `LIKE '%x%'` 难以利用普通 B+Tree。
- 最终应以 `EXPLAIN` 和实际数据量验证。

### 八股 5：数据库列长度与业务校验

数据库列长度是最后一道边界，不应该代替业务校验。例如 `VARCHAR(100)` 在严格模式下会拒绝超长数据，但接口错误会变成数据库异常。更合理的是在 DTO 或 Service 中提前校验并返回明确业务错误。

### 八股 6：DTO 分组校验

分组可以让同一个 DTO 适配新增和修改：

- 新增：名称、分类、价格、库存必填。
- 修改：允许只传要修改的字段。

但必须理解：验证 `Create` 分组时，不会自动验证 `Default` 分组约束。项目这一点仍依赖 Service 复核。

---

## 8. 主题六：问答一致性 + 接口工程

## 8.1 问答表关系

```text
qa 1 ---- N qa_reply
```

`qa` 保存：

- 问题内容。
- 状态：0 待回答、1 已回答。
- 浏览数 `viewCount`。
- 回答数 `replyCount`。

`qa_reply` 保存：

- 所属问题 `qaId`。
- 回答者 `replierId`。
- 是否专家 `isExpert`。

## 8.2 回答问题的完整链路

```text
POST /api/qa/{id}/reply
-> QaController.reply()                         QaController.java:80
-> QaServiceImpl.reply()                        QaServiceImpl.java:181
   -> 校验登录
   -> 校验回答内容
   -> 查询问题
   -> 禁止回答自己的问题
   -> isExpert(userId) 判断专家
   -> 插入 qa_reply
   -> replyCount + 1
   -> 待回答时改成已回答
   -> qaMapper.updateById()
-> Result.success()
```

关键代码：`QaServiceImpl.java:181-205`

```java
QaReply reply = new QaReply();
reply.setQaId(id);
reply.setReplierId(userId);
reply.setContent(dto.getContent().trim());
reply.setIsExpert(isExpert(userId) ? 1 : 0);
qaReplyMapper.insert(reply);

Qa update = new Qa();
update.setId(id);
update.setReplyCount((qa.getReplyCount() == null ? 0 : qa.getReplyCount()) + 1);
if (qa.getStatus() != null && qa.getStatus() == STATUS_WAITING) {
    update.setStatus(STATUS_ANSWERED);
}
qaMapper.updateById(update);
```

`@Transactional` 在同一事务内保护两次写：

```text
INSERT qa_reply
UPDATE qa SET reply_count = reply_count + 1, status = 1
```

如果插入回答成功，但更新问题失败，事务回滚，回答记录也不会留下。

## 8.3 为什么不能回答自己的问题

```java
if (qa.getQuestionerId() != null && qa.getQuestionerId().equals(userId)) {
    throw new BusinessException(2001, "不能回答自己提出的问题");
}
```

这是业务规则，不是权限规则。即使用户有回答接口权限，也不能回答自己的问题。

## 8.4 判断回答者是不是专家

代码位置：`QaServiceImpl.java:317-320`

```java
private boolean isExpert(Long userId) {
    return expertMapper.selectCount(
            new LambdaQueryWrapper<Expert>().eq(Expert::getUserId, userId)) > 0;
}
```

逻辑：

- `expert` 表存在 `user_id = 当前回答者` 的记录。
- 存在就写 `is_expert=1`。
- 不存在就写 `is_expert=0`。

注意：问答模块只是只读 `expert` 表判断身份，不负责专家审核。

## 8.5 删除回答与计数回退

代码位置：`QaServiceImpl.java:255-277`

```java
qaReplyMapper.deleteById(replyId);

Qa qa = qaMapper.selectById(reply.getQaId());
if (qa != null) {
    int count = Math.max(0, (qa.getReplyCount() == null ? 0 : qa.getReplyCount()) - 1);
    Qa update = new Qa();
    update.setId(qa.getId());
    update.setReplyCount(count);
    if (count == 0 && qa.getStatus() != null && qa.getStatus() != STATUS_WAITING) {
        update.setStatus(STATUS_WAITING);
    }
    qaMapper.updateById(update);
}
```

闭环关系：

```text
新增回答：reply_count + 1，状态 0 -> 1
删除回答：reply_count - 1，计数归零时状态 -> 0
```

`Math.max(0, ...)` 是防御性处理：即使历史数据异常，也不会把计数写成负数。

## 8.6 删除问题与级联删除

代码位置：`QaServiceImpl.java:169-177`

```java
@Transactional(rollbackFor = Exception.class)
public void delete(Long userId, Long id) {
    requireLogin(userId);
    Qa qa = mustGet(id);
    checkQuestioner(qa, userId);

    qaReplyMapper.delete(
            new LambdaQueryWrapper<QaReply>().eq(QaReply::getQaId, id));
    qaMapper.deleteById(id);
}
```

顺序必须先删子表回答，再删主表问题。事务保证不会只删一半。

## 8.7 浏览数更新

代码位置：`QaServiceImpl.java:88-95`

```java
Qa qa = mustGet(id);
Qa update = new Qa();
update.setId(id);
update.setViewCount((qa.getViewCount() == null ? 0 : qa.getViewCount()) + 1);
qaMapper.updateById(update);
```

这是“读取旧值 -> 加一 -> 写回”，并发访问时可能丢失浏览数。更高并发下应使用原子 SQL：

```sql
UPDATE qa SET view_count = view_count + 1 WHERE id = ?
```

同理，`replyCount` 也存在并发计数问题。

## 8.8 DTO 分组校验

分组定义：`common/validation/ValidateGroup.java:3-12`

```java
public interface ValidateGroup {
    interface Create {}
    interface Update {}
    interface Register {}
    interface Login {}
}
```

这是一种“接口标记”技巧：

- 新增商品时，Controller 使用 `@Validated(ValidateGroup.Create.class)`。
- 只有标注 `groups = Create.class` 的校验规则才生效。
- 修改商品时不传分组，默认分组规则生效，允许部分字段为空。

商品 DTO 示例：`pojo/dto/ProductDTO.java:13-41`

```java
@NotBlank(groups = ValidateGroup.Create.class)
private String name;

@NotNull(groups = ValidateGroup.Create.class)
private Long categoryId;

@Positive
private BigDecimal price;
```

`@NotBlank` 只在创建时强制名称非空；修改时可以不传名称。

## 8.9 为什么 DTO 校验之后还要 Service 校验

DTO 能表达：

- 非空。
- 长度。
- 数字范围。
- 正数。

DTO 不容易表达：

- 分类 ID 是否存在且 type=1。
- 商品是否属于当前用户。
- 购物车项是否属于当前用户。
- 订单当前状态是否允许发货。
- 回答者是否在专家表。

因此采用“两层校验”：

```text
Controller 的 @Validated -> 格式与基础字段
ServiceImpl              -> 业务上下文与权限
```

### 一个容易误解的分组细节

`ProductController.create()` 使用的是：

```java
@Validated(ValidateGroup.Create.class)
```

Bean Validation 只会验证 `Create` 分组中的约束。`ProductDTO` 中虽然写了：

```java
@Positive
private BigDecimal price;

@Min(0)
private Integer stock;
```

但这两个约束没有声明 `groups = ValidateGroup.Create.class`，它们属于 `Default` 分组，不会因为验证 `Create` 分组而自动执行。

当前代码的实际结果是：

- 创建商品时，`@NotNull(groups = Create.class)` 会执行。
- 未分组的 `@Positive`、`@Min`、`@Size` 主要依赖 Service 再校验。
- `ProductServiceImpl.create()` 确实重新检查了价格和库存，但名称、单位、描述、产地的长度主要依赖数据库列长度或其他路径。

因此这里准确的表述是“DTO 分组校验 + Service 复核的现状”，不能说“所有 DTO 注解在创建路径都一定生效”。

## 8.10 统一返回与分页

### `Result`

代码位置：`common/Result.java:12-32`

```java
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;
}
```

Controller 返回：

```java
return Result.success(data);
return Result.success();
```

### `PageResult`

代码位置：`common/PageResult.java:16-34`

```java
public class PageResult<T> {
    private List<T> list;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer pages;
}
```

`PageResult.of(page, list)` 同时接收：

- `page`：保留 MyBatis-Plus 的 total、current、size、pages。
- `list`：已经转成 VO 的当前页数据。

## 8.11 全局异常处理

代码位置：`common/GlobalExceptionHandler.java:25-83`

处理类型：

| 异常 | 处理结果 |
|---|---|
| `BusinessException` | HTTP 400，body.code 为具体业务码 |
| `MethodArgumentNotValidException` | HTTP 400，返回字段校验错误 |
| `ConstraintViolationException` | HTTP 400，返回参数约束错误 |
| `MissingServletRequestParameterException` | HTTP 400，缺少参数 |
| `HttpMessageNotReadableException` | HTTP 400，JSON 格式错误 |
| `NoResourceFoundException` | HTTP 404 |
| 其他 `Exception` | HTTP 500，日志记录堆栈，客户端只看到通用提示 |

Service 中统一抛：

```java
throw new BusinessException(2002, "商品不存在");
```

异常由全局处理器转换成：

```json
{
  "code": 2002,
  "msg": "商品不存在",
  "data": null
}
```

## 8.12 这一部分应该怎样讲

> 问答模块的核心是回答记录、回答数和问题状态的一致性。回答问题会在同一个事务里插入 qa_reply、增加 reply_count，并在问题处于待回答时改成已回答。删除回答会回退计数，计数归零后恢复待回答；删除问题则先删子表回答再删主记录。接口层使用 DTO 分组校验区分新增和修改，Service 再做业务规则复核，最后通过统一 Result、PageResult、业务错误码和全局异常处理返回。

## 8.13 本部分面试题

### 面试题 1：回答问题为什么需要事务？

**回答：**

回答一次问题至少涉及两次写：

```text
INSERT qa_reply
UPDATE qa SET reply_count = reply_count + 1, status = 1
```

如果回答插入成功而问题计数没更新，页面会显示有回答但回答数为 0；如果只更新计数而回答没插入，会出现虚假计数。因此两次写必须在同一事务。

### 面试题 2：删除回答时怎样保证计数正确？

**回答：**

先确认回答存在且属于当前用户，删除回答后重新查询问题，把 `replyCount` 减一，并用 `Math.max(0, count)` 防止负数。如果减到 0，就把问题状态恢复为待回答。删除和计数回退在同一事务中。

### 面试题 3：删除问题时为什么要先删回答，再删问题？

**回答：**

`qa_reply` 依赖 `qa` 的 `qaId`。如果先删问题再删回答，虽然仍可以按预先拿到的 ID 删除，但业务上会短暂出现孤儿回答；如果第二步失败，数据更不一致。先删子表再删主表，并加事务，可以保证整体成功或失败。

### 面试题 4：当前浏览数 `viewCount + 1` 有什么并发问题？

**回答：**

当前是读取旧值、加一、写回。高并发下可能丢失更新。应改成数据库原子更新：

```sql
UPDATE qa SET view_count = view_count + 1 WHERE id = ?
```

`replyCount` 也有类似问题。

### 面试题 5：为什么不能回答自己提出的问题？

**回答：**

这是业务规则，防止自问自答刷数据或产生无意义互动。它不是 JWT 权限问题，而是在 `reply()` 中比较 `questionerId == userId`。

### 面试题 6：DTO、Entity、VO 有什么区别？

**回答：**

- DTO：接收请求，包含校验规则。
- Entity：映射数据库表，描述持久化结构。
- VO：返回前端，按页面需求组织字段。

直接返回 Entity 可能暴露不该返回的字段，也无法方便地嵌入关联对象。

### 面试题 7：为什么有了 DTO 校验，还要 Service 校验？

**回答：**

DTO 适合格式校验，Service 适合业务校验。例如“分类存在且 type=1”“商品属于当前用户”“订单状态允许发货”都依赖数据库和业务上下文。分层校验可以保证即使 Service 被其他入口调用，业务规则也不会缺失。

### 面试题 8：全局异常处理器做了什么？

**回答：**

`@RestControllerAdvice` 统一捕获 Controller 和 Service 抛出的异常：

- `BusinessException` 转业务错误码。
- DTO 校验异常转字段错误消息。
- JSON 解析错误转 400。
- 未知异常记录日志，客户端只看到 500 通用提示。

这样可以避免每个 Controller 重复写 try-catch。

### 面试题 9：HTTP 状态码和业务 code 有什么区别？

**回答：**

HTTP 状态码表示协议层结果，业务 code 表示业务结果。例如 HTTP 400 + body.code 2002 表示请求没有被成功处理，具体业务原因是“商品不存在”。两者不能混在同一个数字里理解。

### 面试题 10：问答内容需要考虑哪些安全问题？

**回答：**

至少考虑：

- SQL 注入：MyBatis 使用 `#{}` 参数绑定。
- XSS：内容回显时需要前端转义或后端过滤。
- 越权删除：校验 questionerId/replierId。
- 内容长度和敏感词校验。
- 恶意刷浏览量。

当前代码主要覆盖越权和参数基础校验，没有完整的 XSS 过滤、敏感词和防刷机制。

## 8.14 本部分八股原理

### 八股 1：`@RestControllerAdvice` 原理

它基于 Spring MVC 的异常解析机制和 AOP 思路，在全局范围内声明异常处理方法。Controller 抛出的异常会经过 `HandlerExceptionResolver`，匹配到 `@ExceptionHandler` 后转换成统一响应。

### 八股 2：Bean Validation

常见注解：

- `@NotNull`：不能为 null。
- `@NotBlank`：字符串不能为 null、空串或纯空白。
- `@Size`：长度范围。
- `@Min`、`@Max`：数值边界。
- `@Positive`：必须大于 0。

`@Validated` 让 Spring 在方法参数绑定后触发校验。

### 八股 3：分组校验

约束不写 `groups` 时属于 `Default` 分组。显式验证某个分组时，只验证该分组及显式继承的约束。项目商品创建使用 `Create` 分组，因此未分组的约束不会自动参与创建校验。

### 八股 4：数据库外键与级联删除

数据库可以通过：

```sql
FOREIGN KEY (qa_id) REFERENCES qa(id) ON DELETE CASCADE
```

实现数据库级联删除。项目当前是应用层级联：

```text
先 DELETE qa_reply
再 DELETE qa
```

应用层级联更灵活，但必须保证事务；数据库级联更直接，但要求表结构设计完整。

### 八股 5：计数表与原子更新

计数场景常见方案：

- `UPDATE ... SET count = count + 1` 原子更新。
- 单独计数表。
- Redis 计数后异步落库。
- 定时任务校准。

当前项目使用“读值 + 1 后写回”，适合教学但不够并发安全。

### 八股 6：索引对子表查询的作用

常用查询：

```sql
SELECT * FROM qa_reply WHERE qa_id = ?
```

因此 `qa_reply` 需要 `qa_id` 索引。SQL 脚本中已有：

```sql
INDEX idx_qa (qa_id)
```

否则每次查询回答都要全表扫描。

### 八股 7：DTO 与 VO 设计

DTO 关注输入安全，VO 关注输出边界：

- 请求字段不被 Entity 约束。
- 响应字段不暴露数据库全表结构。
- 列表 VO 可以只保留简要字段。
- 详情 VO 可以组合多个表的数据。

### 八股 8：统一响应的利弊

优点：

- 前后端约定稳定。
- 错误处理一致。
- 便于统一日志和追踪。

风险：

- 所有业务错误统一返回 HTTP 400 会掩盖 401、403、404 等语义。
- 需要区分 `success` 与业务错误码。
- 不能为了统一格式而破坏 HTTP 语义。

---

## 9. 三条完整学习路线

### 9.1 路线一：跟着一次下单读全链路

1. `OrderCreateDTO.java:11-20`：请求体有哪些字段。
2. `OrdersController.java:38-40`：接口入口。
3. `BaseController.java:25-43`：当前用户从哪里来。
4. `OrdersServiceImpl.java:80-188`：整个下单业务。
5. `OrdersMapper.java`、`OrderItemMapper.java`：数据库接口。
6. `Orders.java:14-33`、`OrderItem.java:13-23`：订单主从表模型。
7. `Result.java:18-20`：响应包装。

读完后应能回答：

- 为什么按卖家拆单？
- 订单主表和明细表分别保存什么？
- 为什么插入主表后马上能拿到订单 ID？
- 事务回滚会撤销哪些 SQL？
- 当前库存写法为什么仍有并发问题？

### 9.2 路线二：跟着一次取消订单读状态机

1. `OrdersController.java:89-92`：取消接口。
2. `OrdersServiceImpl.java:313-335`：取消业务。
3. `OrdersServiceImpl.java:372-381`：买家/卖家校验。
4. `OrderItemMapper.selectList`：查询订单明细。
5. `ProductMapper.updateById`：回补库存。
6. `OrdersMapper.updateById`：更新取消状态。

读完后应能回答：

- 为什么只有待支付能取消？
- 为什么依据订单明细回补库存？
- 为什么取消方法要加事务？
- 如何防止同一订单并发取消两次？

### 9.3 路线三：跟着商品列表读查询优化

1. `ProductController.java:42-49`：列表入口。
2. `ProductServiceImpl.java:56-84`：分页和条件查询。
3. `MyBatisPlusConfig.java:17-23`：分页插件。
4. `ProductServiceImpl.java:251-274`：批量查分类和卖家。
5. `ProductServiceImpl.java:277-301`：Map 回填 VO。
6. `PageResult.java:30-34`：分页结果转换。

读完后应能回答：

- 什么是 N+1？
- 为什么先分页再查关联？
- 为什么要去重？
- 为什么用 Map 回填？
- 关键字 OR 为什么必须放在 `and(...)` 中？

---

## 10. 代码位置速查

### 10.1 订单

| 内容 | 文件与位置 |
|---|---|
| 创建订单接口 | `controller/OrdersController.java:38-40` |
| 创建订单实现 | `service/impl/OrdersServiceImpl.java:80-188` |
| 地址归属校验 | `OrdersServiceImpl.java:89-93` |
| 购物车批量查询 | `OrdersServiceImpl.java:94-98` |
| 按卖家分组 | `OrdersServiceImpl.java:99-117` |
| 商品快照 | `OrdersServiceImpl.java:135-148` |
| 主表插入 | `OrdersServiceImpl.java:150-163` |
| 明细、库存、购物车 | `OrdersServiceImpl.java:165-179` |
| 支付 | `OrdersServiceImpl.java:267-279` |
| 发货 | `OrdersServiceImpl.java:282-294` |
| 确认收货 | `OrdersServiceImpl.java:297-309` |
| 取消与库存回补 | `OrdersServiceImpl.java:311-336` |
| 退款申请 | `OrdersServiceImpl.java:339-354` |
| 买家/卖家判断 | `OrdersServiceImpl.java:372-381` |
| 订单号生成 | `OrdersServiceImpl.java:389-393` |

### 10.2 权限

| 内容 | 文件与位置 |
|---|---|
| JWT 解析 | `config/security/JwtAuthenticationFilter.java:38-80` |
| 按 userId 查用户 | `config/security/UserDetailsServiceImpl.java:39-47` |
| Security 规则 | `config/SecurityConfig.java:81-157` |
| 当前用户读取 | `controller/BaseController.java:25-43` |
| 管理员方法鉴权 | `controller/AdminProductController.java:26` |
| 管理员 Service 复核 | `service/impl/AdminProductServiceImpl.java:128-136` |
| 购物车归属校验 | `service/impl/CartServiceImpl.java:174-183` |
| 订单买卖双方校验 | `service/impl/OrdersServiceImpl.java:372-381` |
| 问答提问者校验 | `service/impl/QaServiceImpl.java:310-315` |

### 10.3 查询与商品

| 内容 | 文件与位置 |
|---|---|
| 商品列表 | `service/impl/ProductServiceImpl.java:56-84` |
| 分页配置 | `config/MyBatisPlusConfig.java:17-23` |
| 分类批量查询 | `service/impl/ProductServiceImpl.java:251-261` |
| 卖家批量查询 | `service/impl/ProductServiceImpl.java:264-274` |
| VO 回填 | `service/impl/ProductServiceImpl.java:277-301` |
| 发布商品 | `service/impl/ProductServiceImpl.java:117-150` |
| 修改商品 | `service/impl/ProductServiceImpl.java:154-185` |
| 卖家上下架 | `service/impl/ProductServiceImpl.java:189-204` |
| 管理员审核通过 | `service/impl/AdminProductServiceImpl.java:73-83` |
| 管理员驳回 | `service/impl/AdminProductServiceImpl.java:87-103` |

### 10.4 问答与公共设施

| 内容 | 文件与位置 |
|---|---|
| 问答详情与浏览数 | `service/impl/QaServiceImpl.java:88-122` |
| 删除问题 | `service/impl/QaServiceImpl.java:169-177` |
| 回答问题 | `service/impl/QaServiceImpl.java:180-206` |
| 删除回答 | `service/impl/QaServiceImpl.java:254-277` |
| 专家身份判断 | `service/impl/QaServiceImpl.java:317-320` |
| 校验分组 | `common/validation/ValidateGroup.java:3-12` |
| 商品 DTO | `pojo/dto/ProductDTO.java:13-41` |
| 订单 DTO | `pojo/dto/OrderCreateDTO.java:11-20` |
| 购物车 DTO | `pojo/dto/CartAddDTO.java:11-18` |
| 统一返回 | `common/Result.java:12-32` |
| 分页返回 | `common/PageResult.java:16-34` |
| 全局异常 | `common/GlobalExceptionHandler.java:25-83` |

---

## 11. 面试前必须能区分的事实

### 已实现

- Spring Boot 3 分层接口。
- JWT 无状态认证接入。
- `@PreAuthorize` 管理员方法鉴权。
- Service 数据库角色二次确认。
- 商品、需求、购物车、订单、问答资源归属校验。
- 按卖家拆单。
- 订单主从表快照。
- 创建订单和取消订单事务。
- 订单状态迁移判断。
- 取消订单库存回补。
- 问答主表、子表、计数、状态事务联动。
- MyBatis-Plus 分页和批量查询。
- DTO 校验分组、统一 Result/PageResult、全局异常处理。

### 未实现，不能写成已完成

- 高并发库存原子扣减。
- 下单幂等。
- 分布式事务。
- 真实支付和真实退款。
- Redis 缓存、分布式锁。
- MQ 异步处理。
- 微服务拆分。
- 全链路压测和性能提升百分比。

### 可以写成“准备优化”的方向

- 使用条件 SQL 原子扣库存。
- 使用 Redis `INCR`、数据库序列或雪花算法生成订单号。
- 增加订单状态条件更新。
- 增加请求幂等键。
- 用 `Map<Long, Product>` 缓存下单过程中的商品查询。
- 用 `UpdateWrapper` 正确清空审核备注。
- 将浏览数、回答数改成数据库原子增减。

---

## 12. 最后用一条链路串起全部知识

```text
请求进入
-> JWT 过滤器认证
-> SecurityConfig 判断接口是否需要登录
-> Controller 接收 DTO 和当前用户
-> DTO 基础校验
-> Service 业务校验
   -> 登录校验
   -> 数据存在性校验
   -> owner 归属校验
   -> 状态机校验
   -> 库存/金额等业务规则
-> 事务开始
-> Mapper 执行多表 SQL
-> 查询关联数据时使用批量查询和 Map 回填
-> Entity 转 VO
-> 事务提交
-> Result / PageResult 包装
-> 全局异常处理器兜底
-> JSON 返回
```

真正理解这个项目，不是背出“用了 Spring Boot、MyBatis-Plus、JWT”，而是能对任意接口回答：

1. 请求从哪里进来？
2. 当前用户怎样得到？
3. 业务规则在哪一行？
4. 操作了哪些表？
5. 是否需要事务，为什么？
6. 出错时怎样回滚？
7. 有没有越权或 N+1 风险？
8. 当前实现有什么并发局限？

