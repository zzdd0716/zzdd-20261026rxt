# 融销通 - 前后端功能对照表

## 说明
- ✅ = 已对应，后端接口有对应前端页面
- ⚠️ = 后端有接口但前端暂不需要（管理员后台通用功能）
- ❌ = 未对应 / 需确认

---

## 一、认证模块

| 前端页面 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| Login.vue 登录页 | 前端A | POST /api/auth/login | 后端A | ✅ |
| Register.vue 注册页 | 前端A | POST /api/auth/register | 后端A | ✅ |
| 登录跳转（按 role 跳不同首页） | 前端A | 登录响应里的 role 字段 | - | ✅ |

---

## 二、首页 Home.vue

| 功能区块 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| 顶部导航栏 | 前端A | - | - | ✅ |
| Banner 轮播图 | 前端A | (图片写死或后端配置) | - | ✅ |
| 热门分类 | 前端A | GET /api/category/1 (商品) | 后端A | ✅ |
| 热销商品 | 前端A | GET /api/product | 后端B | ✅ |
| 热门专家 | 前端A | GET /api/expert | 后端B | ✅ |
| 融资产品展示 | 前端A | GET /api/finance/products | 后端C | ✅ |
| 热门知识展示 | 前端A | GET /api/knowledge/hot | 后端C | ✅ |

---

## 三、农产品交易

| 前端页面 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| ProductList.vue 商品列表 | 前端A | GET /api/product | 后端B | ✅ |
| ProductDetail.vue 商品详情 | 前端A | GET /api/product/{id} | 后端B | ✅ |
| DemandList.vue 需求列表 | 前端A | GET /api/demand | 后端B | ✅ |
| DemandDetail.vue 需求详情 | 前端A | GET /api/demand/{id} | 后端B | ✅ |

---

## 四、购物车与订单

| 前端页面/功能 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| Cart.vue 购物车页面 | 前端A | GET /api/cart | 后端B | ✅ |
| 加入购物车（详情页按钮） | 前端A | POST /api/cart | 后端B | ✅ |
| 修改购物车数量 | 前端A | PUT /api/cart/{id} | 后端B | ✅ |
| 删除购物车项 | 前端A | DELETE /api/cart/{id} | 后端B | ✅ |
| 批量勾选 | 前端A | PUT /api/cart/selected | 后端B | ✅ |
| OrderCreate.vue 确认订单 | 前端A | POST /api/order | 后端B | ✅ |
| OrderListPurchase.vue 我的采购 | 前端A | GET /api/order/purchase | 后端B | ✅ |
| OrderListSale.vue 我的销售 | 前端A | GET /api/order/sale | 后端B | ✅ |
| OrderDetail.vue 订单详情 | 前端A | GET /api/order/{id} | 后端B | ✅ |
| 模拟支付（订单列表按钮） | 前端A | PUT /api/order/{id}/pay | 后端B | ✅ |
| 卖家发货（销售订单按钮） | 前端A | PUT /api/order/{id}/ship | 后端B | ✅ |
| 买家确认收货 | 前端A | PUT /api/order/{id}/finish | 后端B | ✅ |
| 取消订单 | 前端A | PUT /api/order/{id}/cancel | 后端B | ✅ |
| 申请退款 | 前端A | POST /api/order/{id}/refund | 后端B | ✅ |

---

## 五、我的货源与需求

| 前端页面/功能 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| MyProducts.vue 我的货源 | 前端A | GET /api/product/my | 后端B | ✅ |
| 发布货源表单 | 前端A | POST /api/product | 后端B | ✅ |
| 编辑货源表单 | 前端A | PUT /api/product/{id} | 后端B | ✅ |
| 上下架按钮 | 前端A | PUT /api/product/{id}/status | 后端B | ✅ |
| 删除货源 | 前端A | DELETE /api/product/{id} | 后端B | ✅ |
| MyDemands.vue 我的需求 | 前端A | GET /api/demand/my | 后端B | ✅ |
| 发布需求表单 | 前端A | POST /api/demand | 后端B | ✅ |
| 编辑需求表单 | 前端A | PUT /api/demand/{id} | 后端B | ✅ |
| 删除需求 | 前端A | DELETE /api/demand/{id} | 后端B | ✅ |

---

## 六、融资服务

| 前端页面/功能 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| FinanceService.vue 融资首页 | 前端A | GET /api/finance/products | 后端C | ✅ |
| FinanceMatch.vue 智能匹配 | 前端A | GET /api/finance/match | 后端C | ✅ |
| FinanceApply.vue 融资申请表单 | 前端A | POST /api/finance/apply | 后端C | ✅ |
| FinanceMy.vue 我的融资申请 | 前端A | GET /api/finance/my | 后端C | ✅ |
| 修改融资申请（详情页） | 前端A | PUT /api/finance/{id} | 后端C | ✅ |
| 撤销融资申请 | 前端A | DELETE /api/finance/{id} | 后端C | ✅ |
| 查看融资申请详情 | 前端A | GET /api/finance/{id} | 后端C | ✅ |

---

## 七、专家服务

| 前端页面/功能 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| ExpertList.vue 专家列表 | 前端B | GET /api/expert | 后端B | ✅ |
| ExpertDetail.vue 专家详情 | 前端B | GET /api/expert/{id} | 后端B | ✅ |
| 专家可预约时间展示 | 前端B | GET /api/expert/{id}/available-time | 后端B | ✅ |
| AppointmentCreate.vue 创建预约 | 前端B | POST /api/appointment | 后端B | ✅ |
| AppointmentMyUser.vue 用户视角预约 | 前端B | GET /api/appointment/my/user | 后端B | ✅ |
| AppointmentMyExpert.vue 专家视角预约 | 前端B | GET /api/appointment/my/expert | 后端B | ✅ |
| 专家确认预约 | 前端B | PUT /api/appointment/{id}/confirm | 后端B | ✅ |
| 专家标记完成预约 | 前端B | PUT /api/appointment/{id}/complete | 后端B | ✅ |
| 取消预约 | 前端B | PUT /api/appointment/{id}/cancel | 后端B | ✅ |

---

## 八、在线问答

| 前端页面/功能 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| QaList.vue 问答列表 | 前端B | GET /api/qa | 后端B | ✅ |
| QaDetail.vue 问答详情 | 前端B | GET /api/qa/{id} | 后端B | ✅ |
| QaCreate.vue 发起提问 | 前端B | POST /api/qa | 后端B | ✅ |
| 修改自己的问题 | 前端B | PUT /api/qa/{id} | 后端B | ✅ |
| 删除自己的问题 | 前端B | DELETE /api/qa/{id} | 后端B | ✅ |
| 回答问题（详情页） | 前端B | POST /api/qa/{id}/reply | 后端B | ✅ |
| QaMy.vue 我的问答 | 前端B | GET /api/qa/my/question | 后端B | ✅ |
| QaMy.vue 我的回答 | 前端B | GET /api/qa/my/reply | 后端B | ✅ |

---

## 九、农业知识

| 前端页面/功能 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| KnowledgeList.vue 知识列表 | 前端B | GET /api/knowledge | 后端C | ✅ |
| KnowledgeDetail.vue 知识详情 | 前端B | GET /api/knowledge/{id} | 后端C | ✅ |
| 点赞/取消点赞 | 前端B | POST /api/knowledge/{id}/like | 后端C | ✅ |
| KnowledgeCreate.vue 发布知识 | 前端B | POST /api/knowledge | 后端C | ✅ |
| 编辑知识 | 前端B | PUT /api/knowledge/{id} | 后端C | ✅ |
| 删除知识 | 前端B | DELETE /api/knowledge/{id} | 后端C | ✅ |
| KnowledgeMy.vue 我的知识 | 前端B | GET /api/knowledge/my | 后端C | ✅ |

---

## 十、个人中心

| 前端页面/功能 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| UserInfo.vue 个人信息 | 前端A | GET /api/user/info | 后端A | ✅ |
| 修改个人信息 | 前端A | PUT /api/user/info | 后端A | ✅ |
| Password.vue 修改密码 | 前端A | PUT /api/user/password | 后端A | ✅ |
| Address.vue 地址管理 | 前端A | GET /api/user/address | 后端A | ✅ |
| 新增地址 | 前端A | POST /api/user/address | 后端A | ✅ |
| 修改地址 | 前端A | PUT /api/user/address/{id} | 后端A | ✅ |
| 删除地址 | 前端A | DELETE /api/user/address/{id} | 后端A | ✅ |
| ExpertAudit.vue 专家审核状态 | 前端A | GET /api/user/expert/audit | 后端A | ✅ |
| 专家提交认证资料 | 前端A | POST /api/user/expert/apply | 后端A | ✅ |

---

## 十一、管理员后台

| 前端页面/功能 | 前端分工 | 调用后端接口 | 后端分工 | 对应状态 |
|---|---|---|---|---|
| AdminLogin.vue 管理员登录 | 前端B | POST /api/auth/login | 后端A | ✅ |
| AdminDashboard.vue 仪表盘 | 前端B | GET /api/admin/stats/overview | 后端A | ✅ |
| AdminUsers.vue 用户管理 | 前端B | GET /api/admin/users | 后端A | ✅ |
| 启用禁用用户 | 前端B | PUT /api/admin/users/{id}/status | 后端A | ✅ |
| 修改用户角色 | 前端B | PUT /api/admin/users/{id}/role | 后端A | ✅ |
| 删除用户 | 前端B | DELETE /api/admin/users/{id} | 后端A | ✅ |
| AdminExperts.vue 专家审核 | 前端B | GET /api/admin/experts | 后端A | ✅ |
| 审核通过/驳回 | 前端B | PUT /api/admin/experts/{id}/approve + /reject | 后端A | ✅ |
| AdminProducts.vue 商品管理 | 前端B | GET /api/admin/products | 后端A | ✅ |
| 商品审核 | 前端B | PUT /api/admin/products/{id}/approve + /reject | 后端A | ✅ |
| 强制上下架 | 前端B | PUT /api/admin/products/{id}/status | 后端A | ✅ |
| 删除商品 | 前端B | DELETE /api/admin/products/{id} | 后端A | ✅ |
| AdminDemands.vue 需求管理 | 前端B | GET /api/admin/demands | 后端A | ✅ |
| 删除需求 | 前端B | DELETE /api/admin/demands/{id} | 后端A | ✅ |
| AdminOrders.vue 订单管理 | 前端B | GET /api/admin/orders | 后端A | ✅ |
| AdminFinances.vue 融资审核 | 前端B | GET /api/admin/finances | 后端A | ✅ |
| 审核融资申请 | 前端B | PUT /api/admin/finances/{id}/approve + /reject | 后端A | ✅ |
| AdminKnowledges.vue 知识管理 | 前端B | GET /api/admin/knowledges | 后端A | ✅ |
| 下架/上架知识 | 前端B | PUT /api/admin/knowledges/{id}/status | 后端A | ✅ |
| 删除知识 | 前端B | DELETE /api/admin/knowledges/{id} | 后端A | ✅ |
| AdminCategories.vue 分类管理 | 前端B | GET /api/category | 后端A | ✅ |
| 新增分类 | 前端B | POST /api/category | 后端A | ✅ |
| 修改分类 | 前端B | PUT /api/category/{id} | 后端A | ✅ |
| 删除分类 | 前端B | DELETE /api/category/{id} | 后端A | ✅ |

---

## 十二、后端提供但前端未直接调用的（内部功能）

| 后端接口 | 后端分工 | 说明 |
|---|---|---|
| POST /api/common/upload | 后端A | 图片上传，所有涉及图片的页面（商品发布、知识发布、头像修改、专家认证）都会间接调用 |
| POST /api/common/upload/multi | 后端A | 多图上传，商品发布、专家认证会用到 |

---

## 汇总统计

### 按后端模块统计

| 后端 | 提供接口数 | 被前端调用 | 未被前端调用 |
|---|---|---|---|
| 后端A（基础设施+管理员） | 37 | 36 | 1（multi upload 用得少） |
| 后端B（交易+专家） | 40 | 40 | 0 |
| 后端C（知识+融资） | 15 | 15 | 0 |
| **合计** | **约 92** | **约 91** | **约 1** |

### 按前端分工统计

| 前端 | 负责页面数 | 调用后端接口数 | 依赖后端 |
|---|---|---|---|
| 前端A | 约 18 页 | 约 50 个 | A、B、C |
| 前端B | 约 20 页 | 约 45 个 | A、B、C |

---

## 已解决的问题 ✅

**后端 C 的 GET /api/knowledge/my 已提供"我的知识"专用接口**

前端 B 的"我的知识"页面使用 `GET /api/knowledge/my` 即可获取当前登录专家发布的知识，无需在公开列表接口增加 authorId 参数。

---

## 最终结论

✅ **前后端功能基本一一对应，没有遗漏**
- 每个前端页面都有对应的后端接口
- 每个后端接口都有明确的前端调用场景
- 三个后端模块和两个前端页面分工之间没有断点
- 管理员接口已统一归后端 A 管理（第七章 + 第八章），后端 C 不再包含管理员模块
- 知识"我的"页面使用 GET /api/knowledge/my 专用接口，无需 authorId 参数