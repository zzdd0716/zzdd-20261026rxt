# 后端 B 组代码详解（商品 / 需求 / 购物车 / 订单 / 问答 / 三个管理员）

本文针对 `E:\zzdd\20260918rxt\20260918rxt` 这份可运行代码，
逐接口、逐行讲解 B 组负责的 8 个模块共 44 个接口。

专家模块、专家预约不在范围内。

---

## 0. 先建立三个认知

**认知一：分层不是形式，是责任划分。**

| 层 | 文件 | 只负责 | 不该做 |
|---|---|---|---|
| Controller | `controller/XxxController.java` | 收参数、取当前用户、包装返回值 | 写业务规则、直接操作 Mapper |
| Service 接口 | `service/XxxService.java` | 定义方法签名和语义 | 写实现 |
| ServiceImpl | `service/impl/XxxServiceImpl.java` | 业务规则、权限归属、状态判断 | 处理 HTTP 细节 |
| Mapper | `mapper/XxxMapper.java` | 数据库读写 | 写业务判断 |
| Entity | `pojo/entity/*.java` | 映射数据库表 | 出现给前端的字段 |
| DTO | `pojo/dto/*.java` | 接收请求体 + 参数校验 | 出现数据库字段之外的东西 |
| VO | `pojo/vo/*.java` | 定义响应字段 | 暴露敏感字段 |

**认知二：`ServiceImpl` 才是你真正要能讲清的地方。**
Controller 基本是模板代码，老师不会盯着问；业务规则全在 ServiceImpl。

**认知三：这批代码有一个统一的"守卫套路"。**
几乎每个写操作都是同一个三段式：

```text
1. requireLogin(userId)          // 有没有登录
2. selectById(id) + 判空          // 数据在不在
3. 比较 ownerId == userId         // 是不是你的
   ↓
   才执行 insert / update / delete
```

你只要能把这个套路讲出来，再补每个模块的差异点，就讲清楚了大半。

---

## 1. 一次请求的完整生命周期

以 `GET /api/product/1` 为例，按下键到返回，依次经过：

```text
浏览器
  │  GET /api/product/1   Authorization: Bearer eyJhbGci...
  ▼
JwtAuthenticationFilter              (config/security/JwtAuthenticationFilter.java)
  │  1. 读 Authorization 头，截取 "Bearer " 之后的 token
  │  2. jwtUtil.parseToken(token) 验签 + 查过期，失败直接放行（后面按未登录处理）
  │  3. 从 claims 取 userId
  │  4. userDetailsService.loadUserById(userId) 再查一次数据库
  │     —— 防止用户已被删除/禁用但 token 还没过期
  │  5. new UsernamePasswordAuthenticationToken(...) 放进 SecurityContextHolder
  ▼
SecurityConfig 的 authorizeHttpRequests
  │  GET /api/product/{id} 是 permitAll()，直接放行
  ▼
ProductController.getDetail(1)
  │  只做一件事：调 service，再用 Result.success() 包一层
  ▼
ProductServiceImpl.getDetail(1)
  │  查商品 -> 查分类 -> 查卖家 -> 组装 ProductDetailVO
  ▼
ProductMapper（MyBatis-Plus BaseMapper）
  │  selectById(1)  ->  SELECT * FROM product WHERE id = 1
  ▼
MySQL
  ▼
Jackson 序列化（JacksonConfig 决定时间格式）
  ▼
{"code":200,"msg":"操作成功","data":{...}}
```

需要记住的两个关键点：

- **JWT 过滤器放在 `UsernamePasswordAuthenticationFilter` 之前**，
  见 `SecurityConfig.filterChain()` 最后一行的 `addFilterBefore(...)`。
- **解析失败不抛异常**，只是不往 `SecurityContext` 里放东西。
  于是"这个请求算不算已登录"完全交给后面的授权规则决定。

---

## 2. 公共设施（也属于你会被问到的基础）

### 2.1 统一返回 Result

`common/Result.java`：

```java
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) { return new Result<>(200, "操作成功", data); }
    public static <T> Result<T> success()        { return new Result<>(200, "操作成功", null); }
    public static <T> Result<T> error(Integer code, String msg) { return new Result<>(code, msg, null); }
}
```

Controller 里你会看到两种收尾：

```java
return Result.success(productService.pageList(...));   // 有数据
return Result.success();                               // 只表示成功

// 新建资源时手动拼一个 Map 当 data
return Result.success(Map.of("id", id));
```

注意接口文档里写 `"msg": "success"`，代码实际返回 `"操作成功"`。
**只有 msg 文案不一致，字段结构一致，前端只判断 code 不受影响。**
这是可以主动说明的细节，说明你真的对过代码。

### 2.2 统一分页 PageResult

`common/PageResult.java` 有五个字段，和文档完全对齐：

```java
private List<T> list;    // 当前页数据
private Long total;      // 总记录数
private Integer pageNum; // 当前页
private Integer pageSize;// 每页条数
private Integer pages;   // 总页数
```

构造入口是 `PageResult.of(page, list)`：

```java
public static <T, E> PageResult<T> of(Page<E> page, List<T> list) {
    return new PageResult<>(list, page.getTotal(),
            (int) page.getCurrent(), (int) page.getSize(), (int) page.getPages());
}
```

**为什么要传两个参数？**
`page` 里装的是 Entity（数据库那一行），`list` 是你转好的 VO。
因为要展示分类名、卖家信息，Entity 不够用，必须换一层 VO，
但分页信息（total/pages）还得从 MyBatis-Plus 的 `Page` 里取 —— 所以要分别传。

### 2.3 当前登录用户 BaseController

你所有 Controller 都 `extends BaseController`，它提供四个方法：

```java
protected Long getCurrentUserId()        // 取不到就抛 401 未登录
protected Long getCurrentUserIdOrNull()  // 公开接口里可选登录时用
protected User getCurrentUser()          // 多查一次数据库，拿完整 User
protected Integer getCurrentRole()       // 从 LoginUser 直接拿角色，不查库
```

实现关键在 `getCurrentUserIdOrNull()`：

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth == null || !auth.isAuthenticated()) return null;
Object principal = auth.getPrincipal();
if (principal instanceof LoginUser lu) return lu.getUserId();
return null;
```

这就是"**不要自己从请求头解析 token**"的原因：
token 已经在过滤器里解析并换成了 `LoginUser`，直接读上下文即可。

### 2.4 异常统一处理

业务代码统一 `throw new BusinessException(code, message)`，由
`common/GlobalExceptionHandler.java` 转成 HTTP 响应：

| 异常 | HTTP 状态 | body.code | 说明 |
|---|---|---|---|
| `BusinessException` | 400 | 你传的业务码 | 需要单独断言 |
| `MethodArgumentNotValidException` | 400 | 400 | DTO 校验失败，msg 是"字段 + 消息" |
| `ConstraintViolationException` | 400 | 400 | 方法参数校验失败 |
| `MissingServletRequestParameterException` | 400 | 400 | 缺查询参数 |
| `HttpMessageNotReadableException` | 400 | 400 | JSON 格式错 |
| `NoResourceFoundException` | 404 | 404 | 路径不存在 |
| 其他 `Exception` | 500 | 500 | 打全量堆栈，只回"服务端异常" |

所以你会看到文档里写"响应 code 200"，但报错时是
`HTTP 400 + {"code":2002,"msg":"商品不存在"}` —— **HTTP 状态和业务码是两回事**。

### 2.5 时间格式

`config/JacksonConfig.java` 把 `LocalDateTime` 全局改成
`yyyy-MM-dd HH:mm:ss`（默认是带 `T` 的 ISO-8601）。
这就是"请求里写 `2026-10-01 12:00:00` 能直接绑定到 `LocalDateTime deadline`"的原因。

### 2.6 校验分组 ValidateGroup

`common/validation/ValidateGroup.java` 定义了 `Create / Update / Register / Login` 四个空接口。

用途是：**同一个 DTO，新增时必填、修改时可不填**。

```java
@NotBlank(groups = ValidateGroup.Create.class, message = "商品名称不能为空")
private String name;
```

Controller 端对应：

```java
public Result<...> create(@Validated(ValidateGroup.Create.class) @RequestBody ProductDTO dto)
public Result<...> update(@PathVariable Long id, @Validated @RequestBody ProductDTO dto)
```

这就是为什么"修改商品"可以不传 `name`，但"新增商品"必须传。

### 2.7 MyBatis-Plus 的两个核心写法

**（1）条件构造器带开关**

```java
wrapper.eq(auditStatus != null, Product::getAuditStatus, auditStatus)
       .like(StringUtils.hasText(kw), Product::getName, kw);
```

第一个参数是布尔开关：**为 false 时这条条件根本不会拼进 SQL**。
这是动态查询的标准写法，等价于 XML 里的 `<if test="...">`。

`Product::getName` 是方法引用，MyBatis-Plus 靠它反射出列名 `name`，
好处是**字段改名时编译期就会报错**，不会像写字符串 `"name"` 那样悄悄失效。

**（2）分页**

```java
Page<Product> page = productMapper.selectPage(new Page<>(num, size), wrapper);
```

底层靠 `config/MyBatisPlusConfig.java` 注册的 `PaginationInnerInterceptor` 自动改写 SQL，
追加 `LIMIT`，并额外执行一条 `COUNT` 得到 `total`。
**没有这个拦截器，分页会失效，total 永远是 0。**

开启 SQL 日志：`application.yml` 里 `log-impl: StdOutImpl`，
控制台能直接看到真实 SQL，调试和答辩演示都用得上。

---

## 3. 模块一：商品 / 货源

### 3.1 接口与代码位置对照

| 文档 | 方法与路径 | Controller 行 | ServiceImpl 行 |
|---|---|---|---|
| 1.1 列表（公开） | `GET /api/product` | 42 | 56 |
| 1.2 详情（公开） | `GET /api/product/{id}` | 53 | 88 |
| 1.3 发布 | `POST /api/product` | 59 | 117 |
| 1.4 修改 | `PUT /api/product/{id}` | 66 | 154 |
| 1.5 上下架 | `PUT /api/product/{id}/status` | 73 | 189 |
| 1.6 删除 | `DELETE /api/product/{id}` | 80 | 208 |
| 1.7 我的货源 | `GET /api/product/my` | 87 | 221 |

### 3.2 `GET /api/product` 列表（ServiceImpl 56-85）

```java
int num  = pageNum  == null || pageNum  < 1 ? 1  : pageNum;
int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
```

**为什么在 Service 里再兜一次默认值？**
Controller 的 `@RequestParam(defaultValue = "1")` 只覆盖"参数缺失"，
覆盖不了"参数传了 0 或负数"。这两行把脏值也归一化，属于防御性写法。

```java
String kw  = StringUtils.hasText(keyword) ? keyword.trim() : null;
String org = StringUtils.hasText(origin)  ? origin.trim()  : null;
```

`StringUtils.hasText()` 同时排除了 `null`、`""`、`"   "`。
先判断再 `trim()`，避免空指针 —— 注释里专门写了这一点。

查询条件：

```java
wrapper.eq(Product::getAuditStatus, AUDIT_PASSED)   // 必须 = 1
       .eq(Product::getStatus, STATUS_ON)           // 必须 = 1
       .and(hasText(kw), w -> w.like(Product::getName, kw).or().like(Product::getOrigin, kw))
       .eq(categoryId != null, Product::getCategoryId, categoryId)
       .eq(hasText(org), Product::getOrigin, org)
       .orderByDesc(Product::getCreateTime);
```

实际 SQL 形如：

```sql
SELECT * FROM product
WHERE audit_status = 1
  AND status = 1
  AND (name LIKE '%苹果%' OR origin LIKE '%苹果%')
  AND category_id = 1
ORDER BY create_time DESC
LIMIT 0, 10;
```

**`and(...)` 的括号必须要有。**
如果写成 `.like(name).or().like(origin)` 不加 `and(...)` 包一层，
`or` 会把前面的 `audit_status=1 AND status=1` 一起短路掉，
变成"未审核商品也能被搜出来"。这是动态 SQL 最经典的坑。

第 78-79 行的两个 Map：

```java
Map<Long, Category> categoryMap = categoryMap(records);
Map<Long, User>     userMap     = userMap(records);
```

`categoryMap()`（251 行）先 `stream().map(Product::getCategoryId)` 去重，
再 `selectBatchIds(ids)` 一次查回来，然后收集成 `id -> Category`。

**这一步是"避免 N+1 查询"的教科书做法。**
如果写成 `records.forEach(p -> categoryMapper.selectById(p.getCategoryId()))`，
一页 10 条就是 10 次额外查询，一页 100 条就是 100 次 —— 这叫 N+1。
现在无论多少条，只多 2 次查询（分类 1 次 + 卖家 1 次）。


`toListVO()`（277 行）负责把 Entity 翻译成 `ProductListVO`，
响应字段：`id / name / categoryId / categoryName / price / unit / stock / image / origin / createTime / seller`。
其中 `seller` 内嵌 `UserBriefVO`，列表里只给 `id / realName / farmName / avatar / role`。

### 3.3 `GET /api/product/{id}` 详情（88-114）

```java
Product p = productMapper.selectById(id);
if (p == null) throw new BusinessException(2002, "商品不存在");
```

注意：**详情没有校验 `auditStatus` / `status`**。
也就是说，知道 id 就能看到待审核或已下架商品的详情。
列表接口过滤了，详情没有 —— 这是当前实现的真实差异，
如果被问到"下架商品能不能被看到"，据实回答：列表看不到，详情接口没做拦截。

最后一行 `vo.setImages(parseImages(p.getImages()))`（320 行）：

```java
private List<String> parseImages(String images) {
    if (!StringUtils.hasText(images)) return null;
    try {
        return JSON.parseArray(images, String.class);   // fastjson
    } catch (Exception e) {
        return null;                                    // 脏数据不炸接口
    }
}
```

数据库 `images` 是 `TEXT` 存 JSON 数组字符串，前端要数组，所以在这里转换。
**解析失败返回 null 而不是抛异常**，是为了让一条脏数据不至于让整个详情接口 500。

### 3.4 `POST /api/product` 发布（117-151）

```java
requireLogin(userId);                                     // 1. 登录

if (!hasText(dto.getName()))                     -> 2001 商品名称不能为空
if (categoryId == null || !isProductCategory())  -> 2001 商品分类不存在
if (price == null || price <= 0)                 -> 2001 单价必须大于0
if (stock == null || stock < 0)                  -> 2001 库存不能小于0
if (!hasText(dto.getImage()))                    -> 2001 请上传商品主图
```

这里体现了 **DTO 校验 + Service 校验的双层设计**：
`ProductDTO` 上有 `@NotBlank`、`@NotNull`、`@Positive`、`@Min`、`@Size`，
Controller 用 `@Validated(ValidateGroup.Create.class)` 触发；
Service 里再查一遍，因为"分类存在且 type=1"这种规则注解表达不了。

`isProductCategory()`（245 行）：

```java
Category c = categoryMapper.selectById(categoryId);
return c != null && Objects.equals(c.getType(), CATEGORY_TYPE_PRODUCT);
```

分类表 `type` 字段：`1=商品分类 2=知识分类 3=问题分类`。
商品只能挂在 `type=1` 的分类下，防跨模块串分类。

落库前的三个"服务端强制字段"：

```java
p.setSellerId(userId);          // 归属来自登录态，不是请求体
p.setAuditStatus(AUDIT_PENDING); // 0 待审核
p.setStatus(STATUS_ON);          // 1 上架
```

**`sellerId` 绝不接受前端传值。** 否则任何人都能替别人发布商品。
新商品虽然 `status=1`，但公开列表要求 `auditStatus=1`，
所以"审核通过前用户看不到自己的商品" —— 这就是审核闸门的意义。

```java
productMapper.insert(p);
return p.getId();    // MyBatis-Plus 回填自增主键
```

`insert` 之后 `p.getId()` 有值，是因为 `@TableId(type = IdType.AUTO)` +
MyBatis-Plus 的 `useGeneratedKeys` 回填机制。

### 3.5 `PUT /api/product/{id}` 修改（154-186）

```java
Product exist = productMapper.selectById(id);
if (exist == null)                              -> 2002 商品不存在
if (!exist.getSellerId().equals(userId))        -> 2003 只能修改自己的货源
```

**这两行是"水平越权（IDOR）防护"。**
不判断归属的话，A 用户可以把 URL 里的 id 换成 B 的商品 id，改别人的价。

参数校验这次全是"非空才校验"：

```java
if (dto.getCategoryId() != null && !isProductCategory(...)) -> 2001
if (dto.getPrice()  != null && dto.getPrice() <= 0)         -> 2001
if (dto.getStock()  != null && dto.getStock() < 0)          -> 2001
```

随后组装一个"补丁对象"：

```java
Product update = new Product();
update.setId(id);
update.setName(hasText(dto.getName()) ? dto.getName().trim() : null);
update.setCategoryId(dto.getCategoryId());
...
productMapper.updateById(update);
```

**核心技巧：`updateById` 默认只拼接非 null 字段。**
所以没传的字段 `null` 不会出现在 `SET` 里，天然实现"只改传了的字段"。

生成 SQL 大致是：

```sql
UPDATE product SET price = 6.50 WHERE id = 1;
```

**副作用要注意：这个机制也意味着"无法把字段改回 null"。**
比如想把 `description` 清空，传 `null` 等于没传。
这是当前实现的已知局限。

### 3.6 `PUT /api/product/{id}/status` 上下架（189-205）

```java
if (status == null || (status != 0 && status != 1)) -> 2001 状态参数错误
```

直接对 `Integer` 做 `status != 0` 比较，因为 `status` 已判非 null，会自动拆箱。

```java
Product exist = productMapper.selectById(id);   // 存在性
if (!exist.getSellerId().equals(userId))        // 归属
    -> 2003 只能操作自己的货源
update.setStatus(status);                       // 只改一个字段
```

**这里只改 `status`，不动 `auditStatus`。**
所以"下架再上架"不会让商品回到待审核 —— 审核只在发布时进入一次。

### 3.7 `DELETE /api/product/{id}` 删除（208-218）

同样是"查存在 -> 判归属 -> `deleteById`"。

**这是物理删除**（`deleteById` 直接 `DELETE FROM`）。
代价是：已下单的历史订单会失去对应商品，所以订单明细才要存快照（见第 6 节）。

### 3.8 `GET /api/product/my` 我的货源（221-234）

```java

wrapper.eq(Product::getSellerId, userId)                        // 只看自己的
       .eq(auditStatus != null, Product::getAuditStatus, auditStatus)
       .eq(status != null, Product::getStatus, status)
       .orderByDesc(Product::getCreateTime);
return PageResult.of(page, page.getRecords());   // 直接返回 Entity
```

两个特点：

1. 不筛 `auditStatus`，因为**卖家必须能看到自己被驳回的商品和驳回原因**。
2. 返回类型是 `PageResult<Product>`，直接给 Entity，没有做 VO 转换。
   因为这是自己的数据，不需要隐藏任何字段。

### 3.9 商品模块的追问准备

| 追问 | 回答要点 |
|---|---|
| 为什么列表要限 `auditStatus=1 AND status=1` | 审核闸门 + 下架闸门，两道都要过 |
| keyword 的 SQL 为什么要括号 | 否则 `or` 会破坏前面 `and` 的条件 |
| 怎么防止改别人的商品 | `exist.getSellerId().equals(userId)` |
| 改商品为什么可以不传全部字段 | `updateById` 只更新非 null 字段 |
| 为什么新商品 `status=1` 却看不到 | 因为还要求 `auditStatus=1` |
| 列表怎么避免 N+1 | `stream().map(...).distinct` + `selectBatchIds` 批量查，转 Map |
| `images` 为什么要解析 | 数据库存 JSON 字符串，前端要数组 |

---

## 4. 模块二：采购需求

### 4.1 接口与代码位置

| 文档 | 方法与路径 | Controller 行 | ServiceImpl 行 |
|---|---|---|---|
| 2.1 列表（公开） | `GET /api/demand` | 43 | 51 |
| 2.2 详情（公开） | `GET /api/demand/{id}` | 54 | 79 |
| 2.3 发布 | `POST /api/demand` | 60 | 103 |
| 2.4 修改 | `PUT /api/demand/{id}` | 67 | 134 |
| 2.5 删除 | `DELETE /api/demand/{id}` | 74 | 167 |
| 2.6 我的需求 | `GET /api/demand/my` | 81 | 180 |

### 4.2 与商品模块的三处差异

**差异一：没有审核字段。**
需求表只有 `status`（0已关闭 / 1进行中 / 2已完成），没有 `auditStatus`。
发布时 `d.setStatus(STATUS_ONGOING)` 直接给 `1`，无需管理员审核。

**差异二：owner 字段叫 `buyerId` 而不是 `sellerId`。**

```java
if (!exist.getBuyerId().equals(userId)) -> 2003 只能修改自己的需求
```

字段名不同，但归属校验套路完全一样。

**差异三：列表不返回分类名。**
代码注释明确写着"需求列表不返回分类名，按文档字段"。
所以需求列表只批量查了 `userMap`，没有查分类 —— **按文档裁剪查询，不查用不上的数据**。

### 4.3 列表的 keyword 条件（59-64）

```java
wrapper.and(hasText(kw), w -> w.like(Demand::getName, kw)
                               .or().like(Demand::getDescription, kw))
       .eq(categoryId != null, Demand::getCategoryId, categoryId)
       .eq(status    != null, Demand::getStatus, status)
       .orderByDesc(Demand::getCreateTime);
```

商品是"名称或产地"，需求是"名称或描述"。

注意这里用 `if` 单独包了一层：

```java
if (StringUtils.hasText(keyword)) {
    String kw = keyword.trim();
    wrapper.and(w -> w.like(Qa::getTitle, kw).or().like(Qa::getContent, kw));
}
```

（这是问答模块的写法，需求模块用的是带开关的 `.and(条件, lambda)`。）
两种写法等价，后者更紧凑，前者更好调试。

### 4.4 发布需求（103-131）

```java
requireLogin(userId);
名称非空 -> 2001 需求名称不能为空
分类存在且 type=1 -> 2001 分类不存在
quantity > 0 -> 2001 需求数量必须大于0
expectedPrice 非空时必须 > 0 -> 2001 期望单价必须大于0

d.setBuyerId(userId);              // 归属来自登录态
d.setUnit(hasText(unit) ? unit.trim() : "斤");   // 默认单位
d.setStatus(STATUS_ONGOING);       // 固定 1
demandMapper.insert(d);
```

`expectedPrice` 是可填字段，所以判空后才校验正数 —— **可选字段不能无条件校验**。

### 4.5 我的需求为什么不分页（180-186）

```java
wrapper.eq(Demand::getBuyerId, userId).orderByDesc(Demand::getCreateTime);
return demandMapper.selectList(wrapper);   // List，不是 Page
```

返回类型是 `List<Demand>`，Controller 直接 `Result.success(...)`。
接口文档 2.6 的响应示例就是数组而不是分页对象，
历史上有一笔提交 `ca5b7e5 fix(P2): demand/my 返回格式对齐文档（数组不分页）` 专门改的就是这里。
**能说出"这是按文档把分页改回数组的一次修复"，是很加分的细节。**

---

## 5. 模块三：购物车

### 5.1 接口与代码位置

| 文档 | 方法与路径 | Controller 行 | ServiceImpl 行 |
|---|---|---|---|
| 3.1 列表 | `GET /api/cart` | 38 | 43 |
| 3.2 加入 | `POST /api/cart` | 44 | 65 |
| 3.3 改数量 | `PUT /api/cart/{id}` | 51 | 110 |
| 3.4 删除 | `DELETE /api/cart/{id}` | 58 | 132 |
| 3.5 批量勾选 | `PUT /api/cart/selected` | 65 | 139 |

购物车 5 个接口**全部需要登录**，没有公开接口。

### 5.2 列表（43-62）

```java
wrapper.eq(Cart::getUserId, userId).orderByDesc(Cart::getCreateTime);
List<Cart> carts = cartMapper.selectList(wrapper);

Set<Long> productIds = carts.stream().map(Cart::getProductId)
        .filter(Objects::nonNull).collect(Collectors.toSet());
Map<Long, Product> productMap = productIds.isEmpty() ? Collections.emptyMap()
        : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
```

再一次是"批量查 + Map 回填"的 N+1 防护。
注意 `productIds.isEmpty()` 的短路：购物车为空时**不去查数据库**，
因为 `selectBatchIds(空集合)` 会生成非法 SQL。

`toVO()`（186 行）把商品压缩成 4 个字段：
`name / price / image / stock` —— 购物车不需要商品描述和产地。

### 5.3 加入购物车（65-107）—— 本模块最重要的方法

```java
// 1. 基本校验
if (dto.getProductId() == null)                  -> 2001 商品ID不能为空
if (dto.getQuantity() == null || quantity <= 0)  -> 2001 数量必须大于0

// 2. 商品必须存在
Product product = productMapper.selectById(dto.getProductId());
if (product == null)                             -> 2002 商品不存在

// 3. 商品必须"审核通过 + 上架"
if (!Objects.equals(product.getAuditStatus(), AUDIT_PASSED)
        || !Objects.equals(product.getStatus(), STATUS_ON))
                                                 -> 2001 商品已下架或未通过审核

// 4. 同用户同商品是否已在购物车
Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
        .eq(Cart::getUserId, userId)
        .eq(Cart::getProductId, dto.getProductId())
        .last("limit 1"));

// 5. 累加后的总量不能超库存
int newQuantity = exist == null ? dto.getQuantity() : exist.getQuantity() + dto.getQuantity();
if (newQuantity > product.getStock())            -> 2004 库存不足，当前库存 N

// 6. 新增或更新
if (exist == null) { ...insert...; cart.setSelected(1); }
else               { ...updateById(数量=累加值)... }
```

**为什么第 3 步要卡"审核通过 + 上架"？**
如果允许把下架商品加入购物车，用户结算时必然失败，
不如在入口就拦住 —— 这是"把校验放在最早能拦住的地方"。

**为什么第 5 步是"累加后"判断而不是"本次数量"判断？**
购物车里已有 90 件、库存 100，再买 20 件，本次 20 < 100 看着合法，
但累加后 110 > 100。所以必须校验 `exist.getQuantity() + quantity`。
这是最容易被追问的一点。

`Objects.equals` 而不是 `!=`：`auditStatus` 是 `Integer`，
用 `==` 比较的是引用，超出缓存范围（-128~127）就会出错。
**包装类型一律用 `equals` 或用 `Objects.equals`。**

`.last("limit 1")` 是给 SQL 追加 `LIMIT 1`，
因为逻辑上允许一人一商品只有一条记录，这里主动保证只取一条，
避免历史脏数据导致 `selectOne` 抛 `TooManyResultsException`。

### 5.4 改数量（110-129）

```java
if (quantity == null || quantity <= 0) -> 2001 数量必须大于0
Cart cart = getOwnedCart(userId, id);           // 归属校验
Product product = productMapper.selectById(cart.getProductId());
if (product == null)                   -> 2002 商品不存在
if (quantity > product.getStock())     -> 2004 库存不足
```

`getOwnedCart()`（174 行）把"存在 + 归属"抽成了一个私有方法：

```java
private Cart getOwnedCart(Long userId, Long id) {
    Cart cart = cartMapper.selectById(id);
    if (cart == null)                     -> 2002 购物车项不存在
    if (!cart.getUserId().equals(userId)) -> 2003 只能操作自己的购物车
    return cart;
}
```

**这就是"消除重复"的合理抽象**：改数量、删除两个方法都要这段判断。

### 5.5 批量勾选（139-163）

```java
if (dto.getIds() == null || dto.getIds().isEmpty())  -> 2001 请选择要操作的购物车项
if (selected 不是 0/1)                                -> 2001 勾选参数错误

List<Cart> carts = cartMapper.selectBatchIds(dto.getIds());
if (carts.size() != dto.getIds().size())             -> 2002 部分购物车项不存在

for (Cart c : carts) {
    if (!c.getUserId().equals(userId))               -> 2003 只能操作自己的购物车
}
for (Cart c : carts) { ...updateById(selected)... }
```

**两个 for 分开写是有意的：**
先"全部校验通过"，再"全部修改"。
如果边校验边修改，改到第 3 条才发现第 4 条不是自己的，
前 3 条已经改了，数据处于半完成状态。

**`carts.size() != ids.size()` 能查出"部分不存在"**，
因为 `selectBatchIds` 只会返回真实存在的行。
不过它查不出"ids 里有重复 id"（前端传 [1,1] 时 size 会不等而误报），
这是可以主动说明的边界。

### 5.6 购物车模块追问准备

| 追问 | 回答要点 |
|---|---|
| 为什么要累加后判库存 | 否则"已有 90 + 再买 20"能绕过 100 的库存上限 |
| 一个人同一商品会有多条吗 | 逻辑上只有一条，代码用 `.last("limit 1")` 兜底 |
| 批量勾选为什么两个循环 | 先全量校验、再全量修改，避免部分成功 |
| 怎么防改别人的购物车项 | `getOwnedCart()` 比较 `cart.userId` |
| 为什么用 `Objects.equals` | `Integer` 是包装类型，`==` 比引用不可靠 |

---

## 6. 模块四：订单

### 6.1 接口与代码位置

| 文档 | 方法与路径 | Controller 行 | ServiceImpl 行 |
|---|---|---|---|
| 4.1 创建 | `POST /api/order` | 38 | 81 |
| 4.2 采购订单 | `GET /api/order/purchase` | 44 | 191 |
| 4.3 销售订单 | `GET /api/order/sale` | 53 | 210 |
| 4.4 详情 | `GET /api/order/{id}` | 62 | 229 |
| 4.5 支付 | `PUT /api/order/{id}/pay` | 68 | 267 |
| 4.6 发货 | `PUT /api/order/{id}/ship` | 75 | 282 |
| 4.7 确认收货 | `PUT /api/order/{id}/finish` | 82 | 297 |
| 4.8 取消 | `PUT /api/order/{id}/cancel` | 89 | 313 |
| 4.9 申请退款 | `POST /api/order/{id}/refund` | 96 | 339 |

### 6.2 创建订单（81-188）—— 整个 B 模块最核心的方法

方法头：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public OrderCreateVO create(Long userId, OrderCreateDTO dto) {
```

`@Transactional(rollbackFor = Exception.class)` 的含义：
**方法内所有数据库写操作在同一个事务里，任何异常（包括受检异常）都整体回滚。**
默认 `@Transactional` 只对 `RuntimeException` 回滚，加 `rollbackFor` 更安全。

**第 1 步：参数与收货地址（83-93）**

```java
if (dto.getAddressId() == null)                       -> 2001 请选择收货地址
if (cartIds == null || cartIds.isEmpty())              -> 2001 请选择要下单的购物车项

Address addr = addressMapper.selectById(dto.getAddressId());
if (addr == null || !addr.getUserId().equals(userId)) -> 2002 收货地址不存在
```

注意这里错误信息是"收货地址不存在"而不是"无权使用该地址" ——
**故意模糊**，避免泄露"这个 id 确实存在但不属于你"。

**第 2 步：购物车项存在性（95-98）**

```java
List<Cart> carts = cartMapper.selectBatchIds(dto.getCartIds());
if (carts.size() != dto.getCartIds().size()) -> 2002 部分购物车项不存在
```

**第 3 步：逐条校验 + 按卖家分组（100-117）**

```java
Map<Long, List<Cart>> groupBySeller = new LinkedHashMap<>();
for (Cart c : carts) {
    if (!c.getUserId().equals(userId))            -> 2003 只能下单自己的购物车
    Product p = productMapper.selectById(c.getProductId());
    if (p == null)                                -> 2002 商品不存在
    if (p.auditStatus != 1 || p.status != 1)       -> 2001 商品已下架或未通过审核：名称
    if (c.getQuantity() > p.getStock())            -> 2004 库存不足：名称，当前库存 N
    groupBySeller.computeIfAbsent(p.getSellerId(), k -> new ArrayList<>()).add(c);
}
```

**用 `LinkedHashMap` 而不是 `HashMap`：**
保持分组顺序 = 保持用户勾选顺序，生成的订单号顺序稳定，便于测试断言。
`computeIfAbsent` 是"key 不存在就建一个空 List 再放进去"的惯用写法。

**第 4 步：拼接收货地址（119-124）**

```java
String receiverAddress =
      (addr.getProvince() == null ? "" : addr.getProvince())
    + (addr.getCity()     == null ? "" : addr.getCity())
    + (addr.getDistrict() == null ? "" : addr.getDistrict())
    + (addr.getDetail()   == null ? "" : addr.getDetail());
```

**为什么要拼成字符串存进订单？**
因为地址表可以被用户修改或删除。订单必须保存"下单当时的收货地址"，
所以 `orders` 表冗余了 `receiver / receiver_phone / receiver_address` 三个字段。
这是**快照思想**在地址上的应用。

**第 5 步：按卖家循环建单（128-182）**

```java
for (Map.Entry<Long, List<Cart>> entry : groupBySeller.entrySet()) {
    Long sellerId = entry.getKey();
    List<Cart> itemCarts = entry.getValue();

    // 5.1 算小计 + 造明细（内存里，还没落库）
    BigDecimal sellerTotal = BigDecimal.ZERO;
    List<OrderItem> items = new ArrayList<>();
    for (Cart c : itemCarts) {
        Product p = productMapper.selectById(c.getProductId());
        BigDecimal subtotal = p.getPrice().multiply(BigDecimal.valueOf(c.getQuantity()));
        sellerTotal = sellerTotal.add(subtotal);

        OrderItem oi = new OrderItem();
        oi.setProductId(p.getId());
        oi.setProductName(p.getName());     // 名称快照
        oi.setProductImage(p.getImage());   // 主图快照
        oi.setPrice(p.getPrice());          // 单价快照
        oi.setQuantity(c.getQuantity());
        oi.setSubtotal(subtotal);
        items.add(oi);
    }

    // 5.2 插订单主表
    Orders order = new Orders();
    order.setOrderNo(nextOrderNo());
    order.setBuyerId(userId);
    order.setSellerId(sellerId);
    order.setTotalAmount(sellerTotal);
    order.setAddressId(addr.getId());
    order.setReceiver(receiver);
    order.setReceiverPhone(receiverPhone);
    order.setReceiverAddress(receiverAddress);
    order.setRemark(dto.getRemark());
    order.setStatus(STATUS_PENDING_PAY);   // 0
    order.setRefundStatus(REFUND_NONE);    // 0
    ordersMapper.insert(order);

    // 5.3 写明细 + 扣库存 + 清购物车
    for (int i = 0; i < itemCarts.size(); i++) {
        Cart c  = itemCarts.get(i);
        OrderItem oi = items.get(i);
        oi.setOrderId(order.getId());      // 主表插入后才能拿到自增 id
        orderItemMapper.insert(oi);

        Product p = productMapper.selectById(c.getProductId());
        Product stockUpdate = new Product();
        stockUpdate.setId(p.getId());
        stockUpdate.setStock(p.getStock() - c.getQuantity());
        productMapper.updateById(stockUpdate);

        cartMapper.deleteById(c.getId());  // 下单后从购物车移除
    }

    orderNos.add(order.getOrderNo());
    totalAll = totalAll.add(sellerTotal);
}
```

**"拆单"的判定依据是 `sellerId`。**
买家一次勾选 3 个商品，如果来自 2 个卖家，就会生成 2 张订单。
返回体 `OrderCreateVO` 里 `orderNos` 是列表、`totalAmount` 是全部卖家合计。

**明细为什么要存 `productName / productImage / price`？**
订单是**历史凭证**，必须记录下单那一刻的事实。
商品以后改名、换图、调价，历史订单都不应该跟着变 —— 这叫**快照（snapshot）**。
`subtotal = price × quantity` 也是冗余存储，避免以后改价影响历史金额。

**为什么库存是"创建订单就扣"，而不是"支付后扣"？**
这是本项目选定的业务策略：下单即锁定库存，避免超卖。
代价是未支付订单会占库存，所以必须有取消回补库存的逻辑（见 6.5）。
**这个设计选择一定要能讲出来，它是最常被追问的点之一。**

**为什么不能先插明细再插主表？**
`order_item.order_id` 需要主表自增 id，所以必须 `ordersMapper.insert(order)` 之后
`order.getId()` 才有值。

**一个可以主动指出的性能点：**
第 136 行和第 172 行都调了 `productMapper.selectById(c.getProductId())`，
而第 105 行的第一轮循环已经查过一次。
同一商品在创建订单过程中被查了 3 次。
可以改成在第一轮用 `Map<Long, Product>` 缓存商品，减少重复查询 ——
**能说出"这里还有优化空间"，比说"代码完美"更可信。**

### 6.3 采购 / 销售订单列表（191-226）

```java
// 采购：我是买家
wrapper.eq(Orders::getBuyerId, userId)
       .eq(status != null, Orders::getStatus, status)
       .orderByDesc(Orders::getCreateTime);
Page<Orders> page = ordersMapper.selectPage(new Page<>(num, size), wrapper);
Map<Long, User> sellerMap = userMap(page.getRecords(), Orders::getSellerId);
list = page.getRecords().stream()
        .map(o -> toListVO(o, sellerMap.get(o.getSellerId()), null))
        .collect(toList());
```

销售订单完全对称，只把 `BuyerId/SellerId` 对调。

`userMap(...)`（397 行）接收一个 `Function<Orders, Long>` 参数：

```java
private Map<Long, User> userMap(List<Orders> orders, Function<Orders, Long> idGetter)
```

这样"查买家"和"查卖家"能共用同一个方法，只传不同的取值函数 ——
**这是把"变化的部分"参数化的典型手法**，比写两个几乎一样的方法好。

`toListVO(o, seller, buyer)`（410 行）两边都传，谁不为 null 就填谁：

```java
if (seller != null) vo.setSeller(toBrief(seller));
if (buyer  != null) vo.setBuyer(toBrief(buyer));
```

搭配 `OrderListVO` 上的 `@JsonInclude(JsonInclude.Include.NON_NULL)`，
**null 字段不会出现在 JSON 里**，保证采购列表只有 `seller`、销售列表只有 `buyer`，
和接口文档字段一致。

### 6.4 订单详情与查看权限（229-264）

```java
Orders o = ordersMapper.selectById(id);
if (o == null) -> 2002 订单不存在

User user = userMapper.selectById(userId);
boolean isAdmin = user != null && user.getRole() != null && user.getRole() == ROLE_ADMIN;
if (!isAdmin && !o.getBuyerId().equals(userId) && !o.getSellerId().equals(userId)) {
    -> 2003 无权查看该订单
}

List<OrderItem> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
```

**一个接口三种角色：买家、卖家、管理员都能看，其他人 2003。**
查明细用 `order_item.order_id` 作为外键条件，一次查出全部明细再转 VO。

### 6.5 订单状态机

```
                    pay()                ship()              finish()
  0 待支付  ───────────────▶  1 已支付  ─────────▶  2 已发货  ─────────▶  3 已完成
      │
      │ cancel()
      ▼
  4 已取消                     （refund() 只动 refund_status，不动 status）
```

| 动作 | 方法行 | 谁能做 | 前置状态 | 写入 |
|---|---|---|---|---|
| 支付 | 267 | 买家 | 必须 0 | `status=1`, `pay_time=now` |
| 发货 | 282 | 卖家 | 必须 1 | `status=2`, `ship_time=now` |
| 确认收货 | 297 | 买家 | 必须 2 | `status=3`, `finish_time=now` |
| 取消 | 313 | 买家 | 必须 0 | `status=4` + 回补库存 |
| 申请退款 | 339 | 买家 | 不能是 0 或 4 | `refund_status=1`, `refund_remark` |

**四个方法的结构完全一致，是可背诵的模板：**

```java
requireLogin(userId);
Orders o = mustGet(id);          // 存在性，null -> 2002
checkBuyer(o, userId);           // 或 checkSeller，归属，不符 -> 2003
if (o.getStatus() != 前置状态)    // 状态机，不符 -> 2005
    throw new BusinessException(2005, "...");

Orders update = new Orders();
update.setId(id);
update.setStatus(新状态);
update.setXxxTime(LocalDateTime.now());
ordersMapper.updateById(update);
```

**为什么"支付"也要查买家？**
否则 B 用户可以替 A 用户把订单标记为已支付。**任何写操作都要验归属。**

**状态值不合法时用 2005 而不是 2001：**
2001 是"参数错误"，2005 是"当前状态不允许此操作"，语义更准确。

### 6.6 取消订单与库存回补（313-336）

```java
@Transactional(rollbackFor = Exception.class)
public void cancel(Long userId, Long id) {
    requireLogin(userId);
    Orders o = mustGet(id);
    checkBuyer(o, userId);
    if (o.getStatus() != STATUS_PENDING_PAY) -> 2005 仅待支付订单可取消

    List<OrderItem> items = orderItemMapper.selectList(
            new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
    for (OrderItem oi : items) {
        Product p = productMapper.selectById(oi.getProductId());
        if (p != null) {
            Product stockUpdate = new Product();
            stockUpdate.setId(p.getId());
            stockUpdate.setStock(p.getStock() + oi.getQuantity());   // 加回来
            productMapper.updateById(stockUpdate);
        }
    }
    Orders update = new Orders();
    update.setId(id);
    update.setStatus(STATUS_CANCELLED);
    ordersMapper.updateById(update);
}
```

**关键点：回补数量来自 `order_item.quantity`，不是购物车。**
因为创建订单时购物车项已经被删了，只能从明细还原。
这正是 6.2 里"先写明细"的价值所在。

**为什么必须加 `@Transactional`？**
如果 3 个商品回补成功 2 个、第 3 个失败，库存就多算了。
加事务保证"要么全部回补 + 订单变成已取消，要么全部不变"。

`if (p != null)` 的容错：商品可能已被删除（物理删除），此时跳过回补，不阻断取消。

### 6.7 申请退款（339-354）

```java
if (o.getStatus() == STATUS_PENDING_PAY || o.getStatus() == STATUS_CANCELLED)
    -> 2005 当前状态不可申请退款
if (o.getRefundStatus() != REFUND_NONE)
    -> 2005 已有退款申请，请勿重复申请

update.setRefundStatus(REFUND_APPLYING);   // 1
update.setRefundRemark(hasText(remark) ? remark.trim() : "未填写退款原因");
```

两个要点：

1. **只改 `refundStatus`，不改 `status`** —— 退款是独立于订单状态的一条支线。
2. **防重复申请** 用 `refundStatus != 0` 判断，避免用户连点。

`refundStatus` 的完整语义（表注释）：
`0 无退款 / 1 退款申请中 / 2 已退款 / 3 退款拒绝`。
本模块只实现了"申请"这一步，后续 2/3 由管理员流程处理，不在 B 组范围。

### 6.8 订单号生成（389-394）

```java
private String nextOrderNo() {
    String prefix = "RX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    Long todayCount = ordersMapper.selectCount(
            new LambdaQueryWrapper<Orders>().likeRight(Orders::getOrderNo, prefix));
    return prefix + String.format("%03d", todayCount + 1);
}
```

产出形如 `RX20261013001`。

`likeRight` 生成的是 `order_no LIKE 'RX20261013%'`（右模糊），
**能走索引**；写成 `like` 两边模糊就退化成全表扫描了。
`String.format("%03d", ...)` 补零到 3 位。

**并发缺陷（代码注释自己承认了）：**
两个请求同时查到 `todayCount = 5`，都会生成 `...006`，
而 `order_no` 上有 `UNIQUE` 约束，第二个请求会插入失败。
生产环境应改成数据库自增序列、Redis `INCR` 或雪花算法。
**能主动讲出这个缺陷和三种替代方案，是很好的答辩表现。**

### 6.9 订单模块追问准备

| 追问 | 回答要点 |
|---|---|
| 为什么按卖家拆单 | 每个卖家独立发货、独立结算，所以一个卖家一张订单 |
| 事务加在哪、为什么 | `create` 和 `cancel`；涉及多表多行写，失败必须整体回滚 |
| 库存什么时候扣 | 创建订单即扣（锁定库存防超卖），取消时从明细回补 |
| 为什么明细要存商品名和单价 | 订单是历史凭证，商品改价改名不能影响历史订单 |
| 收货地址为什么拼成字符串存 | 地址表可被修改/删除，订单要保存下单时的地址快照 |
| 订单号会重复吗 | 单机低并发不会，高并发会；已识别，需换雪花/Redis 自增 |
| 详情谁能看 | 买家、卖家、管理员；其他用户 2003 |
| 为什么用 LinkedHashMap 分组 | 保持勾选顺序，订单号生成顺序稳定 |

---

## 7. 模块六：在线问答

### 7.1 接口与代码位置

| 文档 | 方法与路径 | Controller 行 | ServiceImpl 行 |
|---|---|---|---|
| 6.1 列表（公开） | `GET /api/qa` | 43 | 60 |
| 6.2 详情（公开） | `GET /api/qa/{id}` | 54 | 88 |
| 6.3 提问 | `POST /api/qa` | 60 | 125 |
| 6.4 改问题 | `PUT /api/qa/{id}` | 67 | 150 |
| 6.5 删问题 | `DELETE /api/qa/{id}` | 74 | 170 |
| 6.6 回答 | `POST /api/qa/{id}/reply` | 80 | 181 |
| 6.7 我的提问 | `GET /api/qa/my/question` | 87 | 209 |
| 6.8 我的回答 | `GET /api/qa/my/reply` | 93 | 227 |
| 6.9 删除回答 | `DELETE /api/qa/reply/{id}` | 99 | 255 |

**路由设计要点：**
`/{id}`、`/my/question`、`/my/reply`、`/reply/{id}` 都挂在 `/api/qa` 下。
Spring 的路径匹配**字面量优先于路径变量**，
所以 `/api/qa/my/question` 不会被 `/{id}` 抢走，
但 `SecurityConfig` 里仍要把公开规则写清楚：

```java
.requestMatchers(HttpMethod.GET, "/api/qa").permitAll()
.requestMatchers(HttpMethod.GET, "/api/qa/{id}").permitAll()
```

**注意这里有个细节：`/api/qa/my/question` 也会匹配 `/{id}` 这个模式**，
所以它在过滤器层面被 `permitAll` 放行了。
真正拦住未登录用户的是 Controller 里的 `getCurrentUserId()`（取不到抛 401）
和 Service 里的 `requireLogin()`。
**"Security 放行 + 业务层兜底"这个双层结构，是这套代码的一个真实特征。**

### 7.2 列表（60-83）

```java
if (StringUtils.hasText(keyword)) {
    String kw = keyword.trim();
    wrapper.and(w -> w.like(Qa::getTitle, kw).or().like(Qa::getContent, kw));
}
wrapper.eq(categoryId != null, Qa::getCategoryId, categoryId)
       .eq(status     != null, Qa::getStatus, status)
       .orderByDesc(Qa::getCreateTime);
```

然后是"提问者 + 指定专家"一起去重查询：

```java
Set<Long> userIds = page.getRecords().stream()
        .flatMap(q -> Stream.of(q.getQuestionerId(), q.getExpertId()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
Map<Long, User> userMap = userMap(userIds);
```

**`flatMap` 的含义**：一条问答记录会产出两个 id（提问者、专家），
把 10 条记录摊平成最多 20 个 id，`Collectors.toSet()` 自动去重。
这样**一次查询**就把所有相关人员查回来，仍然是 N+1 防护。

### 7.3 详情与浏览数（88-122）

```java
@Transactional(rollbackFor = Exception.class)
public QaDetailVO detail(Long id) {
    Qa qa = mustGet(id);

    Qa update = new Qa();
    update.setId(id);
    update.setViewCount((qa.getViewCount() == null ? 0 : qa.getViewCount()) + 1);
    qaMapper.updateById(update);
    qa.setViewCount(qa.getViewCount() + 1);
```

**这是"读操作带写"的场景**：每次看详情都 `view_count + 1`。
因为方法里有写操作，所以加了 `@Transactional`。
(顺带一提：第 95 行 `qa.getViewCount() + 1` 没做 null 判断，
而第 93 行做了 —— 如果数据库里 `view_count` 是 NULL，这里会空指针。
**主动指出这个不一致，说明你是真读懂了代码。**)

随后查询全部回答并按时间正序：

```java
List<QaReply> replies = qaReplyMapper.selectList(
        new LambdaQueryWrapper<QaReply>()
                .eq(QaReply::getQaId, id)
                .orderByAsc(QaReply::getCreateTime));
```

再用一个 `replierMap` 把回答者信息批量查出来，最后组装 `QaDetailVO`：

```java
vo.setReplies(replies.stream()
        .map(r -> toReplyVO(r, replierMap.get(r.getReplierId())))
        .collect(Collectors.toList()));
```

**回答列表用 `orderByAsc`**（最早的在前），而问题和列表都用 `orderByDesc`。
这个方向差异是有意的：**回答要按对话顺序阅读**。

### 7.4 提问（125-147）

```java
requireLogin(userId);
validateQaDTO(dto);      // dto 非空、title 非空、content 非空、categoryId 非空
if (dto.getExpertId() != null && !isExpert(dto.getExpertId()))
    -> 2002 专家不存在

qa.setQuestionerId(userId);        // 归属来自登录态
qa.setExpertId(dto.getExpertId()); // 可空 = 公开提问
qa.setStatus(STATUS_WAITING);      // 0 待回答
qa.setViewCount(0);
qa.setReplyCount(0);
```

**`expertId` 可空** 是设计要点：不传就是公开提问，所有人都能回答。

`isExpert()` 的实现：

```java
private boolean isExpert(Long userId) {
    return expertMapper.selectCount(
            new LambdaQueryWrapper<Expert>().eq(Expert::getUserId, userId)) > 0;
}
```

它查的是 `expert.user_id`。
**所以接口里的 `expertId` 实际语义是"专家对应的 user.id"，
而不是 `expert` 表的主键 id。**
文档 5.1 的响应里 `"id"` 和 `"userId"` 同时存在且值相同，所以两者容易混。
**这是当前实现的一个语义模糊点，值得主动说明。**

### 7.5 回答问题与计数联动（181-206）

```java
@Transactional(rollbackFor = Exception.class)
public void reply(Long userId, Long id, QaReplyDTO dto) {
    requireLogin(userId);
    if (dto == null || !hasText(dto.getContent())) -> 2001 回答内容不能为空

    Qa qa = mustGet(id);
    if (qa.getQuestionerId() != null && qa.getQuestionerId().equals(userId))
        -> 2001 不能回答自己提出的问题

    QaReply reply = new QaReply();
    reply.setQaId(id);
    reply.setReplierId(userId);
    reply.setContent(dto.getContent().trim());
    reply.setIsExpert(isExpert(userId) ? 1 : 0);   // 自动识别专家身份
    qaReplyMapper.insert(reply);

    Qa update = new Qa();
    update.setId(id);
    update.setReplyCount((qa.getReplyCount() == null ? 0 : qa.getReplyCount()) + 1);
    if (qa.getStatus() != null && qa.getStatus() == STATUS_WAITING) {
        update.setStatus(STATUS_ANSWERED);          // 0 -> 1
    }
    qaMapper.updateById(update);
}
```

**"插入回答"和"更新问题计数"必须同事务**，否则可能出现
回答插进去了但 `reply_count` 没加一，或者状态没变。

**`isExpert` 判定的是"回答这个人是不是专家"**，注意这里的参数是 `userId`（回答者），
和 7.4 里传 `expertId`（被指定专家）的用法不同 —— 同一个方法两种语义，
这是需要留意的地方。

**状态只在 `0 -> 1` 单向迁移**：已经是 `1` 就不重复写状态字段。

### 7.6 删除问题（170-177）

```java
@Transactional(rollbackFor = Exception.class)
public void delete(Long userId, Long id) {
    requireLogin(userId);
    Qa qa = mustGet(id);
    checkQuestioner(qa, userId);                    // 只能删自己的

    qaReplyMapper.delete(new LambdaQueryWrapper<QaReply>().eq(QaReply::getQaId, id));
    qaMapper.deleteById(id);
}
```

**级联删除**：先删子表 `qa_reply`，再删主表 `qa`。
顺序不能反 —— 反了会先失去 `qaId` 的查询依据。
**这就是事务的第二个价值点：删一半失败会留下孤儿回答。**

### 7.7 删除回答与计数回退（255-277）

```java
QaReply reply = qaReplyMapper.selectById(replyId);
if (reply == null)                            -> 2002 回答不存在
if (!reply.getReplierId().equals(userId))     -> 2003 只能删除自己的回答

qaReplyMapper.deleteById(replyId);

Qa qa = qaMapper.selectById(reply.getQaId());
if (qa != null) {
    int count = Math.max(0, (qa.getReplyCount() == null ? 0 : qa.getReplyCount()) - 1);
    update.setReplyCount(count);
    if (count == 0 && qa.getStatus() != null && qa.getStatus() != STATUS_WAITING) {
        update.setStatus(STATUS_WAITING);      // 回到 0 待回答
    }
}
```

**这是 7.5 的逆操作，形成了完整的闭环：**

```
回答  -> reply_count + 1，状态 0 -> 1
删回答 -> reply_count - 1，若归零 状态 -> 0
```

`Math.max(0, ...)` 是防御性写法：即使数据不一致出现负数，也不会写进数据库。

### 7.8 我的提问 / 我的回答（209-251）

**我的提问** 直接按 `questioner_id` 查，转成 `QaMyQuestionVO`：
`id / title / status / replyCount / createTime`（不含内容，列表用）。

**我的回答** 需要两步，因为要带回所属问题标题：

```java
List<QaReply> list = qaReplyMapper.selectList(
        new LambdaQueryWrapper<QaReply>().eq(QaReply::getReplierId, userId)
                .orderByDesc(QaReply::getCreateTime));

Set<Long> qaIds = list.stream().map(QaReply::getQaId).collect(Collectors.toSet());
Map<Long, Qa> qaMap = qaIds.isEmpty() ? Collections.emptyMap()
        : qaMapper.selectBatchIds(qaIds).stream()
                .collect(Collectors.toMap(Qa::getId, Function.identity()));
```

**又一次 N+1 防护**：把回答涉及的 `qaId` 去重后一次查回问题表。
`qaIds.isEmpty()` 短路同样是防止空集合生成非法 SQL。

### 7.9 问答模块追问准备

| 追问 | 回答要点 |
|---|---|
| 浏览数怎么加的 | 详情方法里 `viewCount + 1`，方法带 `@Transactional` |
| 为什么不能回答自己的问题 | `reply()` 里比较 `questionerId == userId` |
| `isExpert` 怎么判断 | 查 `expert` 表是否存在 `user_id = 当前用户` |
| 回复数怎么保证准确 | 回答 +1、删回答 -1，都在事务里 |
| 删问题为什么要事务 | 要同时删主表和子表回答 |
| 为什么 `flatMap` | 一条问答要取提问者和专家两个 id，摊平后去重 |

---

## 8. 模块八 / 九 / 十：三个管理员

### 8.1 接口与代码位置

| 文档 | 方法与路径 | Controller | ServiceImpl 行 |
|---|---|---|---|
| 8.1 商品列表 | `GET /api/admin/products` | 35 | 51 |
| 8.2 审核通过 | `PUT /api/admin/products/{id}/approve` | 46 | 73 |
| 8.3 审核驳回 | `PUT /api/admin/products/{id}/reject` | 53 | 87 |
| 8.4 强制上下架 | `PUT /api/admin/products/{id}/status` | 60 | 107 |
| 8.5 删除商品 | `DELETE /api/admin/products/{id}` | 67 | 120 |
| 9.1 需求列表 | `GET /api/admin/demands` | 30 | 43 |
| 9.2 删除需求 | `DELETE /api/admin/demands/{id}` | 40 | 64 |
| 10.1 订单列表 | `GET /api/admin/orders` | 28 | 44 |

### 8.2 双重权限校验

**第一层：类上加注解。**

```java
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController extends BaseController {
```

`@PreAuthorize` 生效需要 `SecurityConfig` 上的 `@EnableMethodSecurity(prePostEnabled = true)`
和 `pom.xml` 里的 `spring-boot-starter-security`。

`hasRole('ADMIN')` 会去比对 `LoginUser.getAuthorities()` 返回的权限字符串，
而 `LoginUser` 里角色映射是：

```java
String roleName = switch (role == null ? 1 : role) {
    case 3 -> "ROLE_ADMIN";
    case 2 -> "ROLE_EXPERT";
    default -> "ROLE_FARMER";
};
```

**`hasRole('ADMIN')` 会自动补 `ROLE_` 前缀**，
所以这里必须放 `ROLE_ADMIN`，写 `ADMIN` 会永远拒绝。这是常见坑。

**第二层：Service 里再查一次数据库。**

```java
private void requireAdmin(Long userId) {
    if (userId == null) -> 2001 未登录或登录已过期
    User user = userMapper.selectById(userId);
    if (user == null || !Objects.equals(user.getRole(), ROLE_ADMIN))
        -> 2003 仅管理员可操作
}
```

**为什么注解已经拦了还要再查一次？**
因为 `LoginUser` 里的角色来自 JWT 签发时刻的数据库快照。
如果管理员刚被降权，旧 token 里仍是 `ROLE_ADMIN`，
注解会放行，但数据库二次校验能拦住。
**这是"纵深防御"，答辩时值得主动讲。**

另外三个 Service 的每个公开方法第一行都调 `requireAdmin(userId)`，
包括列表查询 —— 权限校验不留白。

### 8.3 商品审核：只处理待审核（73-104）

```java
public void approve(Long userId, Long id) {
    requireAdmin(userId);
    Product exist = mustGet(id);                       // null -> 2002
    if (exist.getAuditStatus() != AUDIT_PENDING)       // 必须 = 0
        -> 2005 仅待审核商品可审核
    update.setAuditStatus(AUDIT_PASSED);               // 1
    update.setAuditRemark(null);                       // 清空驳回原因
    productMapper.updateById(update);
}
```

**注意 `update.setAuditRemark(null)` 有个反直觉的地方：**
前面讲过 `updateById` 只更新非 null 字段，
所以这里设 `null` **实际上不会把 `audit_remark` 清成 NULL**。
想让"审核通过"真的清掉历史驳回原因，需要别的手段
（例如 `UpdateWrapper.set("audit_remark", null)`）。
**这是实现里一个真实的不一致，可以主动指出。**

`reject()` 多了三段校验：

```java
if (!hasText(auditRemark))                     -> 2001 请填写驳回原因
if (auditRemark.trim().length() > 255)         -> 2001 驳回原因不能超过255个字符
if (exist.getAuditStatus() != AUDIT_PENDING)   -> 2005 仅待审核商品可审核
```

**长度限制必须和数据库列定义一致**：`audit_remark VARCHAR(255)`。
Service 里判 255，DTO 里 `@Size(max = 500)` —— 两处不一致，
DTO 更宽松，真正生效的是 Service 的 255。

### 8.4 强制上下架与删除（107-124）

```java
public void updateStatus(Long userId, Long id, Integer status) {
    requireAdmin(userId);
    if (status == null || (status != STATUS_OFF && status != STATUS_ON))
        -> 2001 状态参数错误
    mustGet(id);
    update.setStatus(status);
}

public void delete(Long userId, Long id) {
    requireAdmin(userId);
    mustGet(id);
    productMapper.deleteById(id);
}
```

**管理员版和卖家版的关键区别：**
管理员版**不校验归属**（本来就是管全部数据），
但多了 `requireAdmin`；卖家版不验角色，但必须验 `sellerId`。

还有一个细节：管理员强制上下架**不看 `auditStatus`**，
也就是说待审核商品也能被管理员直接下架，这是"强制"的含义。

### 8.5 分页大小为什么写死 10

三个管理员 Controller 都是这样调用的：

```java
return Result.success(adminProductService.pageList(
        getCurrentUserId(), auditStatus, status, keyword, pageNum, 10));
```

**`pageSize` 在 Controller 里硬编码为 10**，没有暴露成请求参数。
好处是管理员列表不会被前端传超大 `pageSize` 拖垮；
代价是失去了灵活性。
接口文档 8.1 里写 `pageSize` 默认 10，所以行为是一致的。
**Service 层仍然保留了 `pageSize` 参数和兜底逻辑**，只是调用方固定传 10。

### 8.6 管理员订单列表的双角色查询（AdminOrderServiceImpl 44-71）

```java
Set<Long> userIds = new HashSet<>();
for (Orders o : page.getRecords()) {
    if (o.getBuyerId()  != null) userIds.add(o.getBuyerId());
    if (o.getSellerId() != null) userIds.add(o.getSellerId());
}
Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
        : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
```

**这里用 `HashSet` 而不是两个 Set 分别查**：
买家和卖家共用一张 `user` 表，合并去重后一次查询就够了。
如果同一个用户既是买家又是卖家，还能自然去重。

---

## 9. 横向专题（把这些讲透，比背接口更有价值）

### 9.1 归属校验：三种写法

**写法一：内联判断**（商品、需求）

```java
if (!exist.getSellerId().equals(userId))
    throw new BusinessException(2003, "只能修改自己的货源");
```

**写法二：私有方法复用**（购物车）

```java
private Cart getOwnedCart(Long userId, Long id) { ... }
```
适合多个方法用同一段校验的场景。

**写法三：拆成 checkXxx**（订单）

```java
private void checkBuyer(Orders o, Long userId)  { ... }
private void checkSeller(Orders o, Long userId) { ... }
```
适合"存在两种角色都需要各自的校验"的场景。

三种写法本质相同，选哪种取决于复用程度。
**能把"为什么这么拆"讲清楚，比记住写法更重要。**

### 9.2 状态字段语义速查

| 表 | 字段 | 取值 |
|---|---|---|
| `product` | `audit_status` | 0 待审核 / 1 通过 / 2 驳回 |
| `product` | `status` | 0 下架 / 1 上架 |
| `demand` | `status` | 0 已关闭 / 1 进行中 / 2 已完成 |
| `cart` | `selected` | 0 未选中 / 1 选中 |
| `orders` | `status` | 0 待支付 / 1 已支付 / 2 已发货 / 3 已完成 / 4 已取消 |
| `orders` | `refund_status` | 0 无退款 / 1 申请中 / 2 已退款 / 3 拒绝 |
| `qa` | `status` | 0 待回答 / 1 已回答 / 2 已关闭 |
| `qa_reply` | `is_expert` | 0 普通用户 / 1 专家 |
| `user` | `role` | 1 农民 / 2 专家 / 3 管理员 |
| `category` | `type` | 1 商品 / 2 知识 / 3 问题 |

**`product` 的两个状态最容易混淆：**
`audit_status` 管"能不能被看到"，`status` 管"卖家要不要卖"。
公开列表要求两个都是 1。

### 9.3 错误码分工

沿用 `BusinessException` 的号段约定，B 组实际用到：

| 码 | 含义 | 典型场景 |
|---|---|---|
| 2001 | 参数错误 / 状态参数非法 / 未登录 | 名称为空、status 不是 0/1 |
| 2002 | 数据不存在 | 商品、需求、订单、购物车项、回答不存在 |
| 2003 | 无权限 / 非本人 | 改别人的商品、勾别人的购物车、看别人的订单 |
| 2004 | 库存不足 | 加入购物车、下单时超库存 |
| 2005 | 当前状态不允许 | 重复支付、非待支付取消、重复退款、非待审核审核 |

### 9.4 避免 N+1：统一套路

几乎每个列表方法都是这五步：

```java
1. 分页查出本页 Entity
2. stream().map(Entity::get外键Id).filter(非空).collect(toSet())   // 收集去重
3. 集合为空 -> Collections.emptyMap()                             // 短路保护
4. selectBatchIds(ids) -> Collectors.toMap(id, Function.identity()) // 一次查回
5. map(实体行 -> VO，用 Map.get 回填关联对象)                        // 组装
```

**用到的所有位置：** 商品列表、需求列表、购物车列表、订单采购/销售列表、
问答列表、问答详情的回答、我的回答、管理员商品/需求/订单列表。

**能说出"这一套在本模块重复了十几次，是我最熟的模式"，效果很好。**

### 9.5 事务边界

当前代码明确加 `@Transactional(rollbackFor = Exception.class)` 的方法：

| 模块 | 方法 | 为什么需要 |
|---|---|---|
| 订单 | `create` | 插订单 + 插明细 + 扣库存 + 删购物车，多表多行 |
| 订单 | `cancel` | 回补库存 + 改状态 |
| 问答 | `detail` | 读 + 写浏览数 |
| 问答 | `delete` | 删问题 + 删全部回答 |
| 问答 | `reply` | 插回答 + 更新问题计数和状态 |
| 问答 | `deleteReply` | 删回答 + 回退计数和状态 |

**判断标准：一个方法里如果有两次以上写库，且它们有业务一致性要求，就应该加事务。**

### 9.6 快照思想

两处快照，都值得单独讲：

1. **订单明细** 存 `product_name / product_image / price`。
2. **订单主表** 存 `receiver / receiver_phone / receiver_address`。

共同点：**关联数据会变、会删，但历史凭证不能变。**

### 9.7 `updateById` 的两面性

**优点**：天然实现"只更新传了的字段"，修改接口可以不传全量。

**缺点**：

1. 无法把字段改成 `null`（清空描述做不到）。
2. 无法写入空字符串覆盖（`hasText` 判断为 false 时给的是 null）。

代码里的应对是：需要改哪个字段，就在 Service 里显式 `set` 那个字段。

### 9.8 公开接口的边界

`SecurityConfig` 里对 B 组放行的公开接口：

```java
GET /api/product            permitAll
GET /api/product/{id}       permitAll
GET /api/demand             permitAll
GET /api/demand/{id}        permitAll
GET /api/qa                 permitAll
GET /api/qa/{id}            permitAll
```

**需要知道的两个细节：**

1. `/api/product/my` 和 `/api/demand/my` 会匹配 `/{id}` 模式，
   在过滤器层面被放行，真正拦人的是 `getCurrentUserId()` 和 `requireLogin()`。
   专家模块处理得更严谨 —— 把 `/api/expert/profile` 显式写在 `{id}` 前面并设为
   `authenticated()`，注释里还专门说明了原因。
2. 商品详情公开且不校验 `audit_status`，所以待审核商品的详情可被直接访问。

---

## 10. 答辩速查

### 10.1 一句话介绍你的工作

> 我负责贸易与问答这条线，共 8 个模块 44 个接口：
> 商品、采购需求、购物车、订单、在线问答，加上商品/需求/订单三个管理员模块。
> 代码入口是 8 个 Controller，业务逻辑集中在对应的 8 个 ServiceImpl 里。

### 10.2 必须能背出来的五段

1. **商品发布的三个强制字段**：`sellerId=当前用户`、`auditStatus=0`、`status=1`。
2. **购物车的累加判库存**：`exist.quantity + dto.quantity > stock` 才拦截。
3. **创建订单的四步**：校验地址 → 校验购物车与库存 → 按卖家分组 → 拆单落库并扣库存清购物车。
4. **订单状态机**：`0→1→2→3`，`0→4`；只有 `0` 能取消，取消回补库存。
5. **问答计数闭环**：回答 `+1` 且 `0→1`；删回答 `-1` 且归零回到 `0`。

### 10.3 主动指出的三个改进点

能主动说出不足，比声称代码完美更可信。这三个都有代码依据：

1. **订单号并发重复**：`nextOrderNo()` 用"当天订单数 + 1"，
   并发下会撞 `order_no` 唯一约束。建议改 Redis `INCR` 或雪花算法。
   代码注释里已经写了这一点。
2. **创建订单重复查询商品**：`ProductServiceImpl` 式的一轮缓存没做，
   同一商品在 `create()` 里被查了 3 次，可以用 `Map<Long, Product>` 缓存。
3. **审核通过清不掉 `audit_remark`**：`update.setAuditRemark(null)` 配合
   `updateById` 的"忽略 null"特性，实际不生效，需要 `UpdateWrapper`。

### 10.4 代码位置速查

```
controller/
  ProductController.java              (1.1-1.7)
  DemandController.java               (2.1-2.6)
  CartController.java                 (3.1-3.5)
  OrdersController.java               (4.1-4.9)
  QaController.java                   (6.1-6.9)
  AdminProductController.java         (8.1-8.5)
  AdminDemandController.java          (9.1-9.2)
  AdminOrderController.java           (10.1)

service/impl/
  ProductServiceImpl.java
  DemandServiceImpl.java
  CartServiceImpl.java
  OrdersServiceImpl.java
  QaServiceImpl.java
  AdminProductServiceImpl.java
  AdminDemandServiceImpl.java
  AdminOrderServiceImpl.java

pojo/dto/    请求体（含校验注解）
pojo/vo/     响应体
pojo/entity/ 数据库映射
mapper/      MyBatis-Plus BaseMapper
```

### 10.5 按接口读代码的方法

拿到任意一个接口，按顺序回答六个问题：

1. 路径和方法是什么，映射到哪个 Controller 的哪一行？
2. 请求参数进到哪个 DTO？DTO 上有哪些校验注解？
3. Controller 怎么拿到 `userId`？
4. ServiceImpl 里做了哪几层判断，每层不符抛哪个错误码？
5. 最终动了哪几张表、生成什么 SQL？
6. 返回哪个 VO，字段从哪来？

能对 44 个接口中的任意一个流畅走完这六步，这个模块就算真的掌握了。
