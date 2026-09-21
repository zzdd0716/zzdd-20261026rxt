# 前端 A - 公共环境配置 + 交易核心 + 个人中心 + 融资

## 一、公共环境配置（所有人都依赖你，必须最先完成）

### 1.1 项目初始化

```bash
npm create vite@6.5.0 . -- --template vue
npm install vue-router@4 element-plus @element-plus/icons-via axios pinia wang-editor
```

### 1.2 依赖清单

| 依赖 | 版本 | 用途 |
|---|---|---|
| vue | ^3.4.0 | 框架 |
| vue-router | ^4.3.0 | 路由 |
| element-plus | ^2.7.0 | UI 组件库 |
| @element-plus/icons-via | ^2.3.0 | 图标 |
| axios | ^1.6.0 | HTTP 请求 |
| pinia | ^2.1.0 | 状态管理 |
| @wangeditor/editor | ^5.6.0 | 富文本编辑器 |
| @wangeditor/editor-for-vue | ^5.1.0 | wangEditor Vue3 封装 |

### 1.3 目录结构

```
src/
├── api/
│   ├── auth.js / user.js / category.js / product.js
│   ├── demand.js / cart.js / order.js / finance.js / common.js
├── router/index.js
├── store/user.js
├── utils/request.js + constants.js
├── components/
│   ├── AppLayout.vue / AppNavbar.vue / AppFooter.vue
│   ├── ImageUpload.vue / CategorySelect.vue / StatusTag.vue
└── views/  (详见各页面章节)
```

### 1.4 utils/request.js

```js
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

const request = axios.create({
  baseURL: 'http://localhost:8088',
  timeout: 10000
})

// 请求拦截器：自动带 JWT
request.interceptors.request.use(config => {
  const store = useUserStore()
  if (store.token) {
    config.headers['Authorization'] = 'Bearer ' + store.token
  }
  return config
})

request.interceptors.response.use(
  res => {
    const { code, msg, data } = res.data
    if (code === 200) return data
    if (code === 401) {
      const store = useUserStore(); store.logout(); router.push('/login')
    }
    if (code === 403) { ElMessage.error('权限不足'); return Promise.reject(res.data) }
    ElMessage.error(msg || '请求失败')
    return Promise.reject(res.data)
  },
  err => {
    if (err.response?.status === 401) { const store = useUserStore(); store.logout(); router.push('/login') }
    else if (err.response?.status === 403) { ElMessage.error('权限不足') }
    else ElMessage.error('网络异常：' + (err.message || '未知错误'))
    return Promise.reject(err)
  }
)
export default request
```

### 1.5 store/user.js

```js
import { defineStore } from 'pinia'
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    isLogin: s => !!s.token,
    role: s => s.userInfo?.role || 0,
    isFarmer: s => s.userInfo?.role === 1,
    isExpert: s => s.userInfo?.role === 2,
    isAdmin: s => s.userInfo?.role === 3
  },
  actions: {
    setLogin(token, userInfo) {
      this.token = token; this.userInfo = userInfo
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    },
    logout() {
      this.token = ''; this.userInfo = null
      localStorage.removeItem('token'); localStorage.removeItem('userInfo')
    }
  }
})
```

### 1.6 utils/constants.js

```js
export const ORDER_STATUS    = { 0:{text:'待支付',type:'warning'}, 1:{text:'已支付',type:'info'}, 2:{text:'已发货',type:'primary'}, 3:{text:'已完成',type:'success'}, 4:{text:'已取消',type:'danger'} }
export const PRODUCT_STATUS  = { 0:{text:'已下架',type:'info'}, 1:{text:'已上架',type:'success'} }
export const PRODUCT_AUDIT   = { 0:{text:'待审核',type:'warning'}, 1:{text:'已通过',type:'success'}, 2:{text:'已驳回',type:'danger'} }
export const FINANCE_STATUS  = { 0:{text:'待审核',type:'warning'}, 1:{text:'已通过',type:'success'}, 2:{text:'已拒绝',type:'danger'} }
export const DEMAND_STATUS   = { 0:{text:'已关闭',type:'info'}, 1:{text:'进行中',type:'success'}, 2:{text:'已完成',type:'primary'} }
export const APPOINTMENT_STATUS = { 0:{text:'待确认',type:'warning'}, 1:{text:'已确认',type:'success'}, 2:{text:'已完成',type:'primary'}, 3:{text:'已取消',type:'danger'} }
export const ROLE_MAP        = { 1:{text:'农民',color:'#67C23A'}, 2:{text:'专家',color:'#409EFF'}, 3:{text:'管理员',color:'#E6A23C'} }
```

### 1.7 main.js

```js
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-via'
const app = createApp(App)
for (const [k, c] of Object.entries(ElementPlusIconsVue)) app.component(k, c)
app.use(createPinia()); app.use(router); app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
```

### 1.8 router/index.js

```js
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/login', component: () => import('@/views/login/Login.vue') },
  { path: '/register', component: () => import('@/views/login/Register.vue') },
  {
    path: '/', component: () => import('@/components/AppLayout.vue'), redirect: '/home',
    children: [
      { path: 'home', component: () => import('@/views/home/Home.vue') },
      { path: 'product', component: () => import('@/views/product/ProductList.vue') },
      { path: 'product/:id', component: () => import('@/views/product/ProductDetail.vue') },
      { path: 'demand', component: () => import('@/views/demand/DemandList.vue') },
      { path: 'demand/:id', component: () => import('@/views/demand/DemandDetail.vue') },
      { path: 'cart', component: () => import('@/views/cart/Cart.vue'), meta: { needLogin: true } },
      { path: 'order/create', component: () => import('@/views/order/OrderCreate.vue'), meta: { needLogin: true } },
      { path: 'order/purchase', component: () => import('@/views/order/OrderListPurchase.vue'), meta: { needLogin: true } },
      { path: 'order/sale', component: () => import('@/views/order/OrderListSale.vue'), meta: { needLogin: true } },
      { path: 'order/:id', component: () => import('@/views/order/OrderDetail.vue'), meta: { needLogin: true } },
      { path: 'my/products', component: () => import('@/views/my/MyProducts.vue'), meta: { needLogin: true } },
      { path: 'my/demands', component: () => import('@/views/my/MyDemands.vue'), meta: { needLogin: true } },
      { path: 'my/info', component: () => import('@/views/my/UserInfo.vue'), meta: { needLogin: true } },
      { path: 'my/password', component: () => import('@/views/my/Password.vue'), meta: { needLogin: true } },
      { path: 'my/address', component: () => import('@/views/my/Address.vue'), meta: { needLogin: true } },
      { path: 'my/expert-audit', component: () => import('@/views/my/ExpertAudit.vue'), meta: { needLogin: true } },
      { path: 'finance', component: () => import('@/views/finance/FinanceService.vue') },
      { path: 'finance/match', component: () => import('@/views/finance/FinanceMatch.vue') },
      { path: 'finance/apply', component: () => import('@/views/finance/FinanceApply.vue'), meta: { needLogin: true } },
      { path: 'finance/my', component: () => import('@/views/finance/FinanceMy.vue'), meta: { needLogin: true } }
    ]
  },
  // 前端 B 负责的路由（已配好，B 只实现组件）
  { path: '/expert', component: () => import('@/components/AppLayout.vue'), children: [
    { path: '', component: () => import('@/views/expert/ExpertList.vue') },
    { path: ':id', component: () => import('@/views/expert/ExpertDetail.vue') } ] },
  { path: '/appointment/create/:id', component: () => import('@/views/appointment/AppointmentCreate.vue'), meta: { needLogin: true } },
  { path: '/appointment/my-user', component: () => import('@/views/appointment/AppointmentMyUser.vue'), meta: { needLogin: true } },
  { path: '/appointment/my-expert', component: () => import('@/views/appointment/AppointmentMyExpert.vue'), meta: { needLogin: true, role: 2 } },
  { path: '/qa', component: () => import('@/components/AppLayout.vue'), children: [
    { path: '', component: () => import('@/views/qa/QaList.vue') },
    { path: 'create', component: () => import('@/views/qa/QaCreate.vue'), meta: { needLogin: true } },
    { path: ':id', component: () => import('@/views/qa/QaDetail.vue') },
    { path: 'my', component: () => import('@/views/qa/QaMy.vue'), meta: { needLogin: true } } ] },
  { path: '/knowledge', component: () => import('@/components/AppLayout.vue'), children: [
    { path: '', component: () => import('@/views/knowledge/KnowledgeList.vue') },
    { path: 'create', component: () => import('@/views/knowledge/KnowledgeCreate.vue'), meta: { needLogin: true } },
    { path: 'my', component: () => import('@/views/knowledge/KnowledgeMy.vue'), meta: { needLogin: true } },
    { path: ':id', component: () => import('@/views/knowledge/KnowledgeDetail.vue') } ] },
  { path: '/admin/login', component: () => import('@/views/admin/AdminLogin.vue') },
  { path: '/admin', component: () => import('@/components/AdminLayout.vue'), redirect: '/admin/dashboard', meta: { needLogin: true, role: 3 },
    children: [
      { path: 'dashboard', component: () => import('@/views/admin/AdminDashboard.vue') },
      { path: 'users', component: () => import('@/views/admin/AdminUsers.vue') },
      { path: 'experts', component: () => import('@/views/admin/AdminExperts.vue') },
      { path: 'products', component: () => import('@/views/admin/AdminProducts.vue') },
      { path: 'demands', component: () => import('@/views/admin/AdminDemands.vue') },
      { path: 'orders', component: () => import('@/views/admin/AdminOrders.vue') },
      { path: 'finances', component: () => import('@/views/admin/AdminFinances.vue') },
      { path: 'knowledges', component: () => import('@/views/admin/AdminKnowledges.vue') },
      { path: 'categories', component: () => import('@/views/admin/AdminCategories.vue') } ] },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to, from, next) => {
  const store = useUserStore()
  if (to.meta.needLogin && !store.isLogin) return next('/login')
  if (to.meta.role && store.role !== to.meta.role) return next('/home')
  if (to.path.startsWith('/admin') && !to.meta.role && store.role !== 3) return next('/login')
  next()
})
export default router
```

### 1.9 公共组件

#### ImageUpload.vue
```vue
<template>
  <el-upload :action="uploadUrl" :headers="headers" :show-file-list="false" :on-success="handleSuccess" :before-upload="beforeUpload">
    <img v-if="modelValue" :src="modelValue" class="preview" />
    <el-icon v-else class="uploader-icon"><Plus /></el-icon>
  </el-upload>
</template>
<script setup>
import { computed } from 'vue'
import { Plus } from '@element-plus/icons-via'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
const props = defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])
const store = useUserStore()
const uploadUrl = 'http://localhost:8088/api/common/upload'
const headers = computed(() => ({ Authorization: store.token ? 'Bearer ' + store.token : '' }))
const beforeUpload = file => {
  const ok = ['image/jpeg', 'image/png', 'image/gif'].includes(file.type) && file.size / 1024 / 1024 < 10
  if (!ok) ElMessage.error('只能上传图片，且不超过 10MB！')
  return ok
}
const handleSuccess = res => { if (res.code === 200) emit('update:modelValue', res.data.url); else ElMessage.error(res.msg) }
</script>
<style scoped>
.preview { width: 120px; height: 120px; object-fit: cover; border-radius: 4px; }
.uploader-icon { font-size: 28px; color: #8c939d; }
</style>
```

#### CategorySelect.vue
```vue
<template>
  <el-select v-model="innerId" :placeholder="placeholder" @change="$emit('change', innerId)">
    <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
  </el-select>
</template>
<script setup>
import { ref, watch, onMounted } from 'vue'
import request from '@/utils/request'
const props = defineProps({
  modelValue: { type: [Number, String], default: '' },
  type: { type: Number, default: 1 },
  placeholder: { type: String, default: '请选择分类' }
})
const emit = defineEmits(['update:modelValue', 'change'])
const innerId = ref(props.modelValue)
watch(() => props.modelValue, v => innerId.value = v)
const categories = ref([])
onMounted(async () => { categories.value = await request.get(`/api/category/${props.type}`) })
</script>
```

#### StatusTag.vue
```vue
<template>
  <el-tag :type="statusMap[status]?.type || 'info'">{{ statusMap[status]?.text || '未知' }}</el-tag>
</template>
<script setup>
import { ORDER_STATUS, PRODUCT_STATUS, FINANCE_STATUS, DEMAND_STATUS, APPOINTMENT_STATUS } from '@/utils/constants'
const props = defineProps({ status: { type: [Number, String], required: true }, type: { type: String, default: 'order' } })
const map = { order: ORDER_STATUS, product: PRODUCT_STATUS, finance: FINANCE_STATUS, demand: DEMAND_STATUS, appointment: APPOINTMENT_STATUS }
const statusMap = map[props.type] || {}
</script>
```

---

## 二、页面详细开发规范

---

### 2.1 登录页 Login.vue

**路由**: `/login`  
**功能**: 用户/专家/管理员统一登录入口，登录成功按 role 跳不同首页

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 用户登录 | POST | `/api/auth/login` | 提交账号密码获取 token |

**提交数据 (Body)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**页面展示字段（来自响应 data）**

| 页面位置 | 字段 | 类型 | 说明 |
|---|---|---|---|
| 登录成功后 | token | string | 存 localStorage 和 store |
| 登录成功后 | user.id | number | 用户ID |
| 登录成功后 | user.username | string | 用户名 |
| 登录成功后 | user.realName | string | 真实姓名（显示在导航栏） |
| 登录成功后 | user.role | number | 1=农民 2=专家 3=管理员，**决定跳转目标** |
| 登录成功后 | user.avatar | string | 头像URL（导航栏显示） |

**用户操作流程**
```
输入用户名密码 → 点击登录
  → 调 POST /api/auth/login
  → 成功: store.setLogin(token, userInfo)
     → role=3 跳转 /admin/dashboard
     → 其他  跳转 /home
  → 失败: ElMessage.error(msg)
```

---

### 2.2 注册页 Register.vue

**路由**: `/register`  
**功能**: 农民/专家自助注册（管理员不开放自助注册）

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 用户注册 | POST | `/api/auth/register` | 提交注册表单 |

**提交数据 (Body)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名，5-20位字母数字 |
| password | string | 是 | 密码，6-20位 |
| realName | string | 是 | 真实姓名 |
| phone | string | 是 | 手机号，11位数字 |
| role | int | 是 | 1=农民 2=专家（下拉选择） |
| email | string | 否 | 邮箱 |
| farmName | string | 否 | 农场/合作社名称（农民填） |

**页面展示字段**

注册成功后只提示"注册成功，请登录"，跳转登录页，无需展示响应数据。

**用户操作流程**
```
填表单 → 选角色（农民/专家）→ 提交
  → 调 POST /api/auth/register
  → 成功: ElMessage.success('注册成功') → router.push('/login')
  → 失败: ElMessage.error(msg)
```

---

### 2.3 首页 Home.vue

**路由**: `/home`  
**功能**: 项目入口页，聚合展示各模块入口和热门数据

**调用后端接口**

| 序号 | 接口 | 方法 | URL | 用途 | 后端属于 |
|---|---|---|---|---|---|
| 1 | 分类列表 | GET | `/api/category/1` | 商品分类（水果/蔬菜/粮食/畜牧） | 后端A |
| 2 | 商品列表 | GET | `/api/product` | 热销商品（取前8条） | 后端B |
| 3 | 专家列表 | GET | `/api/expert` | 热门专家（取前4条） | 后端B |
| 4 | 融资产品列表 | GET | `/api/finance/products` | 最新金融产品 | 后端C |
| 5 | 热门知识 | GET | `/api/knowledge/hot` | 热门知识文章（取前4） | 后端C |

**接口1 请求参数**（商品分类）

| 字段 | 类型 | 值 | 说明 |
|---|---|---|---|
| type | int | 1 | 商品分类 |

**接口1 展示字段**（来自响应 data）

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 分类卡片 | id | 分类ID（跳转商品列表时带参数） |
| 分类卡片 | name | 分类名称 |
| 分类卡片 | icon | 图标URL |

**接口2 请求参数**（热销商品）

| 字段 | 类型 | 值 | 说明 |
|---|---|---|---|
| pageNum | int | 1 | 第一页 |
| pageSize | int | 8 | 取8条 |

**接口2 展示字段**（来自响应 data.list[]）

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 商品卡片 | id | 点击跳转 `/product/{id}` |
| 商品卡片 | name | 商品名称 |
| 商品卡片 | image | 封面图片（后端字段叫 image，不是 coverImage） |
| 商品卡片 | price | 单价 |
| 商品卡片 | unit | 单位 |

**接口3 请求参数**（热门专家）

| 字段 | 类型 | 值 | 说明 |
|---|---|---|---|
| pageNum | int | 1 | |
| pageSize | int | 4 | 取4条 |

**接口3 展示字段**（来自响应 data.list[]）

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 专家卡片 | id | 点击跳转 `/expert/{id}` |
| 专家卡片 | realName | 专家姓名 |
| 专家卡片 | avatar | 头像 |
| 专家卡片 | specialty | 专长 |
| 专家卡片 | rating | 评分 |

**接口4 展示字段**（来自响应 data[]）

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 融资产品卡片 | id | 可点击跳融资列表 |
| 融资产品卡片 | bankName | 银行名称 |
| 融资产品卡片 | name | 产品名称 |
| 融资产品卡片 | interestRate | 年利率% |

**接口5 展示字段**（来自响应 data[]）

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 知识卡片 | id | 点击跳转知识详情 |
| 知识卡片 | title | 文章标题 |
| 知识卡片 | coverImage | 封面（知识的封面字段叫 coverImage） |
| 知识卡片 | viewCount | 浏览量 |

**用户操作流程**
```
页面加载时并发调5个接口
  → 分类数据渲染顶部分类入口
  → 商品数据渲染"热销农产品"区块
  → 专家数据渲染"热门专家"区块
  → 融资数据渲染"融资服务"区块
  → 知识数据渲染"农业知识"区块
点击分类 → router.push('/product?categoryId=' + id)
点击商品 → router.push('/product/' + id)
点击专家 → router.push('/expert/' + id)
```

---

### 2.4 商品列表 ProductList.vue

**路由**: `/product`  
**功能**: 商品浏览，支持分类筛选、关键词搜索、分页

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 商品列表 | GET | `/api/product` | 分页查询商品 |
| 分类下拉 | GET | `/api/category/1` | 左侧分类树 |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 搜索商品名称 |
| categoryId | long | 否 | 点击左侧分类时传入 |
| origin | string | 否 | 产地 |
| pageNum | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页数量，默认 10 |

**展示字段（来自响应 data.list[]）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 商品卡片 | id | 点击跳转 `/product/{id}` |
| 商品卡片 | name | 商品名称 |
| 商品卡片 | image | 商品主图（后端字段叫 image，不是 coverImage） |
| 商品卡片 | price | 单价，显示格式：¥5.80/斤 |
| 商品卡片 | unit | 单位 |
| 商品卡片 | stock | 库存 |
| 商品卡片 | origin | 产地 |
| 商品卡片 | categoryName | 分类名称 |
| 商品卡片 | seller.realName | 卖家姓名 |
| 商品卡片 | createTime | 发布时间 |
| 分页 | total | 总条数（分页组件用） |

**用户操作流程**
```
左侧点分类 → 改变 categoryId → 重新加载
顶部搜索框 → 输入关键词点搜索 → 重新加载
分页切换 → 改变 pageNum → 重新加载
点商品卡片 → router.push('/product/' + id)
```

---

### 2.5 商品详情 ProductDetail.vue

**路由**: `/product/:id`  
**功能**: 展示单个商品完整信息，支持加入购物车和立即购买

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 商品详情 | GET | `/api/product/{id}` | 获取商品完整数据 |
| 加入购物车 | POST | `/api/cart` | 加购 |

**展示字段（来自 GET 响应 data）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 图片轮播 | images | 图片URL数组（多张） |
| 基本信息 | name | 商品名称 |
| | price | 单价 |
| | unit | 单位 |
| | stock | 库存 |
| | description | 商品描述（富文本HTML，用 v-html 渲染） |
| | origin | 产地 |
| | image | 主图（列表也用到） |
| | createTime | 发布时间 |
| 分类 | categoryName | 分类名称 |
| 卖家信息 | seller.id | 卖家用户ID |
| | seller.realName | 卖家姓名 |
| | seller.farmName | 农场名称 |
| | seller.phone | 联系电话（可复制拨打） |

**加购提交数据 (POST /api/cart)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productId | long | 是 | 当前商品ID（来自 route.params.id） |
| quantity | int | 是 | 选择的数量（不能超过 stock） |

**用户操作流程**
```
页面加载 → GET /api/product/{id}
选数量（el-input-number，max=stock）
  → 点"加入购物车"
     → 未登录: router.push('/login')
     → 已登录: POST /api/cart → ElMessage.success('已加入购物车')
  → 点"立即购买"
     → 未登录: router.push('/login')
     → 已登录: POST /api/cart → router.push('/order/create')
```

---

### 2.6 需求列表 DemandList.vue

**路由**: `/demand`  
**功能**: 展示全部采购需求，可按关键词搜索和筛选状态

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 需求列表 | GET | `/api/demand` | 分页查询 |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 搜索需求名称 |
| categoryId | long | 否 | 分类筛选 |
| status | int | 否 | 0已关闭/1进行中/2已完成 |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

**展示字段（来自响应 data.list[]）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 需求卡片 | id | 点击跳转 `/demand/{id}` |
| | name | 需求名称 |
| | quantity | 需求量 |
| | unit | 单位 |
| | expectedPrice | 期望单价 |
| | status | 需求状态（StatusTag type="demand" 渲染） |
| | deadline | 截止日期 |
| | buyer.realName | 发布者 |
| | createTime | 发布时间 |

---

### 2.7 需求详情 DemandDetail.vue

**路由**: `/demand/:id`  
**功能**: 展示需求完整信息

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 需求详情 | GET | `/api/demand/{id}` | |

**展示字段（来自响应 data）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 主体 | name / description / quantity / unit / expectedPrice | 需求名称、详情、数量、单位、期望价 |
| | status | 状态（StatusTag） |
| | deadline | 截止日期 |
| 发布者 | buyer.realName / buyer.phone / buyer.farmName | 发布者信息 |
| 底部 | createTime | 发布时间 |

---

### 2.8 购物车 Cart.vue

**路由**: `/cart`  
**权限**: 需登录  
**功能**: 管理已加购商品，勾选/改数量/删除/去结算

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 购物车列表 | GET | `/api/cart` | 获取当前用户购物车 |
| 修改数量 | PUT | `/api/cart/{id}` | 改数量 |
| 删除购物车项 | DELETE | `/api/cart/{id}` | 删 |
| 批量勾选 | PUT | `/api/cart/selected` | 全选/反选 |

**购物车列表响应字段（来自 data[]）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 每行 | id | 购物车项ID（修改/删除/勾选都要） |
| | productId | 商品ID（点击商品名跳详情） |
| | product.name | 商品名称 |
| | product.image | 商品图片 |
| | product.price | 当前单价 |
| | product.stock | 库存（数量不能超过） |
| | quantity | 已加购数量 |
| | selected | 是否已勾选（0/1） |
| 底部合计 | — | 遍历 selected=1 的项，累加 price×quantity，算总价和总件数 |

**修改数量 PUT /api/cart/{id} 提交数据**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| quantity | int | 是 | 新数量 |

**批量勾选 PUT /api/cart/selected 提交数据**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| ids | array | 是 | 要勾选/取消的购物车ID列表 |
| selected | int | 是 | 1=勾选 0=取消 |

**用户操作流程**
```
进页面 → GET /api/cart → 渲染每行
点勾选框 → 调 PUT /api/cart/{id} 改 selected 字段
点全选 → 调 PUT /api/cart/selected，ids=全部项的id，selected=1/0
改数量 → el-input-number 失焦时调 PUT /api/cart/{id}
删除 → ElMessageBox.confirm 确认后 DELETE /api/cart/{id}
去结算 → 必须有选中项 → router.push('/order/create')
```

---

### 2.9 确认订单 OrderCreate.vue

**路由**: `/order/create`  
**权限**: 需登录  
**功能**: 选地址 + 确认商品 + 提交订单

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 地址列表 | GET | `/api/user/address` | 选收货地址 |
| 创建订单 | POST | `/api/order` | 提交订单 |

**地址列表展示字段（来自 data[]）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 地址卡片 | id | 选中时传给订单的 addressId |
| | receiver | 收件人姓名（后端字段叫 receiver） |
| | phone | 手机号 |
| | province + city + district + detail | 完整地址（拼接显示） |
| | isDefault | 是否默认地址（默认自动选中） |

**提交订单 POST /api/order 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| addressId | long | 是 | 选中的地址ID |
| cartIds | array | 是 | 选中的购物车项ID数组（后端据此拆单、算明细和总价） |
| remark | string | 否 | 订单备注 |

> 注意：后端不接收 payMethod 字段，前端不需要传。

**订单创建响应字段（来自响应 data）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 成功后 | orderNos | string数组（如 `["RX20260920001","RX20260920002"]`），跨卖家会拆成多单 |
| | totalAmount | 全部订单总价合计 |

**用户操作流程**
```
页面加载 → GET /api/user/address
  → 展示默认地址；没地址引导去 /my/address 添加
商品清单 → 由前端从购物车带过来（sessionStorage 或 state）
选地址 → 高亮显示，记 addressId
填备注 → 可选
提交订单 → POST /api/order
  → 成功: ElMessage.success
     → 如果 orderNos 只有一个，router.push('/order/' + orderNos[0])
     → 如果多个，router.push('/order/purchase') 并 toast "已创建N个订单"
  → 失败: ElMessage.error(msg)
```

---

### 2.10 我的采购订单 OrderListPurchase.vue

**路由**: `/order/purchase`  
**权限**: 需登录  
**功能**: 买家视角看自己下的所有订单，按状态 tab 切换

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 采购订单列表 | GET | `/api/order/purchase` | 查当前用户作为买家的订单 |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0待支付/1已支付/2已发货/3已完成/4已取消；空=全部 |
| pageNum | int | 否 | 默认 1 |

**响应字段（来自 data.list[]）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 订单卡片顶部 | orderNo | 订单号（点击跳转详情 `/order/{id}`） |
| | createTime | 下单时间 |
| | status | 订单状态（StatusTag type="order"） |
| | seller.realName | 卖家（显示"卖家：XXX"） |
| 金额区 | totalAmount | 订单总价 |
| 商品区 | items 不存在，列表接口不返回明细 → 点卡片进详情才看得到 | — |
| 操作按钮 | — | 根据 status 动态显示：待支付→去支付；已支付→取消；已发货→确认收货；已完成→申请退款；已取消→无操作 |

> 注意：订单列表接口只返回订单概要，不返回 items 明细。商品明细只有在订单详情里才有。

**状态 Tab 配置**
```
全部(status空) / 待支付(0) / 已支付(1) / 已发货(2) / 已完成(3) / 已取消(4)
```

---

### 2.11 我的销售订单 OrderListSale.vue

**路由**: `/order/sale`  
**权限**: 需登录  
**功能**: 卖家视角，看别人买了自己商品的订单

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 销售订单列表 | GET | `/api/order/sale` | 查当前用户作为卖家的订单 |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 筛选 |
| pageNum | int | 否 | 默认 1 |

**响应字段**

同 OrderListPurchase，区别是显示的是 buyer.realName（买家）。

**操作按钮（卖家视角）**
```
待支付(0) / 已支付(1) → "发货"按钮（发货后 status 变为 2）
已发货(2) → 无额外操作
已完成(3) / 已取消(4) → 无操作
```

---

### 2.12 订单详情 OrderDetail.vue

**路由**: `/order/{id}`  
**权限**: 需登录  
**功能**: 完整订单信息 + 状态时间线 + 操作按钮

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 订单详情 | GET | `/api/order/{id}` | 页面加载时获取 |
| 去支付 | PUT | `/api/order/{id}/pay` | 买家点支付 |
| 取消订单 | PUT | `/api/order/{id}/cancel` | 买家取消待支付订单 |
| 卖家发货 | PUT | `/api/order/{id}/ship` | 卖家点发货 |
| 确认收货 | PUT | `/api/order/{id}/finish` | 买家确认 |
| 申请退款 | POST | `/api/order/{id}/refund` | 买家申请退款 |

**订单详情展示字段（来自响应 data）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 基本信息 | orderNo | 订单号 |
| | buyerId / sellerId | 买家/卖家用户ID |
| | totalAmount | 订单总价 |
| | status | 订单状态（StatusTag） |
| | refundStatus / refundRemark | 退款状态和原因（申请退款后才可能有） |
| | remark | 买家下单备注 |
| 地址区 | receiver | 收件人姓名（平铺字段，不是嵌套 address 对象） |
| | receiverPhone | 收件人手机号 |
| | receiverAddress | 完整收货地址字符串 |
| 商品列表 | items[].productName | 商品名 |
| | items[].productImage | 商品主图（后端叫 productImage） |
| | items[].price | 单价 |
| | items[].quantity | 数量 |
| | items[].subtotal | 小计 |
| 时间线 | createTime | 下单时间 |
| | payTime | 支付时间（已支付后有） |
| | shipTime | 发货时间（已发货后有） |
| | finishTime | 完成时间（已完成后有） |
| 操作按钮 | — | 根据当前 status + 当前用户是买家/卖家动态显示 |

> 注意：后端不返回 payAmount（只有 totalAmount）、trackingNo/trackingCompany（发货接口不接物流号）、cancelTime（取消订单没有单独的时间戳字段）。前端不要期望这些字段。

---

### 2.13 我的货源 MyProducts.vue

**路由**: `/my/products`  
**权限**: 需登录（农民/专家/管理员都可以发布）  
**功能**: 管理自己发布的商品——发布新商品、编辑、上下架、删除、查看审核状态

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 我的商品列表 | GET | `/api/product/my` | |
| 发布商品 | POST | `/api/product` | |
| 编辑商品 | PUT | `/api/product/{id}` | |
| 上下架 | PUT | `/api/product/{id}/status` | |
| 删除 | DELETE | `/api/product/{id}` | |

**列表请求参数 (GET /api/product/my)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditStatus | int | 否 | 0待审核/1通过/2驳回；空=全部 |
| status | int | 否 | 0下架/1上架 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 名称 | name | |
| 价格 | price | |
| 库存 | stock | |
| 审核状态 | auditStatus | StatusTag type="product" 的审核态（0待审核/1通过/2驳回） |
| 审核备注 | auditRemark | auditStatus=2 驳回原因 |
| 上架状态 | status | StatusTag type="product" |
| 发布时间 | createTime | |
| 操作 | — | 编辑 / 下架或上架 / 删除 |

> 注意：后端 `/api/product/my` 返回的列表**没有** image、categoryName、unit、viewCount 这些字段（为了轻量化）。如果前端需要展示图片，可以在列表里不显示图，或者点进详情时再调。核心列表只显示上述字段。

**发布商品 POST /api/product 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 商品名称 |
| categoryId | long | 是 | 用 CategorySelect :type="1" |
| image | string | 是 | 主图URL（先调 ImageUpload 上传） |
| images | string | 否 | 多图JSON字符串，如 '["url1","url2"]' |
| price | decimal | 是 | 单价 |
| unit | string | 否 | 单位，默认"斤" |
| stock | int | 是 | 库存数量 |
| origin | string | 否 | 产地 |
| description | string | 否 | 详细描述 |

**上下架 PUT /api/product/{id}/status 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 是 | 0下架 1上架 |

**用户操作流程**
```
页面加载 → GET /api/product/my
发布 → 点"发布新货源" → 弹 el-dialog 表单 → ImageUpload + CategorySelect → POST → 刷新列表
编辑 → 点"编辑" → 弹表单回显数据 → PUT → 刷新
上下架 → 点"下架/上架" → ElMessageBox.confirm → PUT → 刷新
删除 → 点"删除" → ElMessageBox.confirm → DELETE → 刷新
```

---

### 2.14 我的需求 MyDemands.vue

**路由**: `/my/demands`  
**权限**: 需登录  
**功能**: 管理自己发布的采购需求

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 我的需求列表 | GET | `/api/demand/my` | |
| 发布需求 | POST | `/api/demand` | |
| 编辑需求 | PUT | `/api/demand/{id}` | |
| 删除 | DELETE | `/api/demand/{id}` | |

**列表响应字段（来自 data[]）**

| 表格列 | 字段 | 说明 |
|---|---|---|
| 名称 | name | |
| 数量 | quantity | |
| 状态 | status | 0已关闭/1进行中/2已完成（StatusTag type="demand"） |
| 截止 | deadline | |
| 操作 | — | 编辑 / 删除 |

**发布/编辑需求 POST/PUT `/api/demand` 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 需求名称 |
| categoryId | long | 是 | CategorySelect |
| quantity | int | 是 | 需求量 |
| unit | string | 否 | 单位，默认"斤" |
| expectedPrice | decimal | 否 | 期望单价 |
| description | string | 否 | 详细说明 |
| deadline | string | 否 | 截止日期，格式 "2026-10-01 12:00:00" |

---

### 2.15 融资服务首页 FinanceService.vue

**路由**: `/finance`  
**功能**: 金融产品列表 + 智能匹配入口 + 申请记录入口

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 金融产品列表 | GET | `/api/finance/products` | |

**展示字段（来自响应 data[]）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 产品卡片 | id | |
| | bankName | 银行名称 |
| | name | 产品名称 |
| | minAmount / maxAmount | 额度范围（显示"5万-300万"） |
| | minPeriod / maxPeriod | 期限范围（显示"6-36个月"） |
| | interestRate | 年利率%（突出显示） |
| | purpose | 适用关键词（逗号分隔，渲染成多个 el-tag） |
| | description | 产品简介 |

**用户操作流程**
```
进入页面 → GET /api/finance/products → 渲染产品卡片
点"智能匹配"入口 → router.push('/finance/match')
点"立即申请"（需登录） → router.push('/finance/apply?productId=' + id)
点"我的融资" → router.push('/finance/my')
```

---

### 2.16 融资智能匹配 FinanceMatch.vue

**路由**: `/finance/match`  
**功能**: 用户输入需求，展示匹配到的金融产品及匹配理由

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 智能匹配 | GET | `/api/finance/match` | |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| amount | decimal | 否 | 期望融资金额 |
| purpose | string | 否 | 用途关键词，如 "果园扩建" |
| repayPeriod | int | 否 | 期望期限（月） |

**展示字段（来自响应 data[]）**

| 卡片位置 | 字段 | 说明 |
|---|---|---|
| 匹配得分 | matchScore | 0-100，数字越大越好，突出显示 |
| 匹配理由 | matchReason | 后端返回的自然语言说明，如"关键词匹配:种植/果园，金额符合20万需求" |
| 产品信息 | 同 FinanceService 的字段 | 银行、名称、利率、额度等 |

**用户操作流程**
```
页面顶部 el-form：金额输入框 + 用途输入框 + 期限输入框 + 搜索按钮
点搜索 → GET /api/finance/match + 参数
  → 无结果: 显示"暂无匹配产品"
  → 有结果: 按 matchScore desc 渲染卡片列表
每张卡片底部"立即申请"按钮（需登录，跳 /finance/apply?productId=xxx）
```

---

### 2.17 融资申请 FinanceApply.vue

**路由**: `/finance/apply`  
**权限**: 需登录  
**功能**: 提交融资申请，带默认参数（从列表页点过来时带 productId）

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 提交申请 | POST | `/api/finance/apply` | |
| 融资产品列表 | GET | `/api/finance/products` | 表单"意向产品"下拉选项 |

**请求参数 (POST)**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productId | long | 否 | 意向金融产品ID（下拉选择，可选不指定） |
| title | string | 是 | 项目名称，如"红富士果园扩建" |
| amount | decimal | 是 | 申请金额 |
| purpose | string | 是 | 用途，如"果园扩建" |
| description | string | 否 | 详细说明（el-input type="textarea"） |
| repayPeriod | int | 是 | 期望期限（月） |
| contactName | string | 是 | 联系人（默认回显当前用户的 realName） |
| contactPhone | string | 是 | 联系电话（默认回显当前用户的 phone） |

**用户操作流程**
```
页面加载 → GET /api/finance/products 填下拉选项
如果 route.query.productId 有值 → 表单 productId 字段自动选中
提交 → POST → 成功后 router.push('/finance/my')
```

---

### 2.18 我的融资 FinanceMy.vue

**路由**: `/finance/my`  
**权限**: 需登录  
**功能**: 我的融资申请列表 + 详情弹窗 + 修改 + 撤销

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 融资列表 | GET | `/api/finance/my` | |
| 申请详情 | GET | `/api/finance/{id}` | 详情弹窗 |
| 修改申请 | PUT | `/api/finance/{id}` | 仅 status=0 待审核可改 |
| 撤销申请 | DELETE | `/api/finance/{id}` | 仅 status=0 可撤销 |

**列表请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0待审核/1通过/2拒绝；空=全部 |
| pageNum | int | 否 | 默认 1 |

**列表响应字段（来自 data.list[]）**

| 列 | 字段 | 说明 |
|---|---|---|
| 项目名称 | title | |
| 金融产品 | product.bankName + product.name | 拼接显示，无产品则显示"未指定" |
| 金额 | amount | |
| 用途 | purpose | |
| 期限 | repayPeriod | 显示"XX个月" |
| 状态 | status | StatusTag type="finance" |
| 审核备注 | reviewRemark | 审核后才可能有值 |
| 申请时间 | createTime | |
| 操作 | — | 待审核→编辑/撤销；已通过/拒绝→查看详情 |

**详情展示字段（来自 GET /api/finance/{id} 响应 data）**

| 位置 | 字段 | 说明 |
|---|---|---|
| 基本信息 | title / amount / purpose / description / repayPeriod | 完整数据 |
| 联系人 | contactName / contactPhone | |
| 产品信息 | product.bankName / product.name / product.interestRate | |
| 审核信息 | status / reviewRemark / reviewTime | 审核后才有 |
| 时间 | createTime | 提交时间 |

**修改申请 PUT /api/finance/{id} 请求参数**

同 POST 提交申请的字段，只传要修改的字段（但 title/amount/purpose/repayPeriod 仍必填）。

---

### 2.19 个人信息 UserInfo.vue

**路由**: `/my/info`  
**权限**: 需登录  
**功能**: 查看和修改个人资料

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 获取信息 | GET | `/api/user/info` | 页面加载回显 |
| 修改信息 | PUT | `/api/user/info` | 保存修改 |

**GET 回显字段（来自响应 data）**

| 表单字段 | 来自响应字段 | 类型 | 说明 |
|---|---|---|---|
| 头像 | avatar | string | ImageUpload 组件回显 |
| 用户名 | username | string | 只读 |
| 真实姓名 | realName | string | 可编辑 |
| 邮箱 | email | string | 可编辑 |
| 手机号 | phone | string | 可编辑 |
| 农场名称 | farmName | string | 仅农民角色有，可编辑 |

**PUT 修改请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| avatar | string | 否 | 头像URL |
| realName | string | 否 | |
| email | string | 否 | |
| phone | string | 否 | |
| farmName | string | 否 | 仅农民角色 |

---

### 2.20 修改密码 Password.vue

**路由**: `/my/password`  
**权限**: 需登录  
**功能**: 修改密码

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 修改密码 | PUT | `/api/user/password` | |

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| oldPassword | string | 是 | 原密码 |
| newPassword | string | 是 | 新密码，6-20位 |
| confirmPassword | string | — | 再次输入新密码（只前端校验一致性，**不提交给后端**） |

**用户操作流程**
```
填原密码 → 填新密码 → 确认新密码（前端校验一致）
→ 提交 → PUT /api/user/password（只传 oldPassword + newPassword）
→ 成功: ElMessage.success → 清空表单
→ 失败: ElMessage.error(msg)（原密码错误等）
```

---

### 2.21 收货地址管理 Address.vue

**路由**: `/my/address`  
**权限**: 需登录  
**功能**: 收货地址增删改查，支持设默认

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 地址列表 | GET | `/api/user/address` | 页面加载 |
| 新增 | POST | `/api/user/address` | |
| 修改 | PUT | `/api/user/address/{id}` | |
| 删除 | DELETE | `/api/user/address/{id}` | |

**列表展示字段（来自 data[]）**

| 表格行 | 字段 | 说明 |
|---|---|---|
| 收件人 | receiver | 后端字段叫 receiver（不是 receiverName） |
| 手机号 | phone | |
| 完整地址 | province + city + district + detail | 拼接显示 |
| 默认标识 | isDefault | 1显示 el-tag"默认" |
| 操作 | — | 编辑 / 设为默认 / 删除 |

**新增/修改请求参数（POST/PUT Body）**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| receiver | string | 是 | 收件人姓名（后端字段叫 receiver） |
| phone | string | 是 | 手机号 |
| province | string | 是 | 省 |
| city | string | 是 | 市 |
| district | string | 是 | 区/县 |
| detail | string | 是 | 详细地址 |
| isDefault | int | 否 | 0=非默认 1=默认，默认0 |

**设默认地址**：调 PUT /api/user/address/{id} 传 `{ isDefault: 1 }`（后端会把其他地址的 isDefault 清零）。

---

### 2.22 专家审核状态 ExpertAudit.vue

**路由**: `/my/expert-audit`  
**权限**: 需登录（role=2 专家才显示完整页面，role=1 农民显示"申请成为专家"按钮）  
**功能**: 查看专家认证状态 + 提交专家认证申请

**调用后端接口**

| 接口 | 方法 | URL | 用途 |
|---|---|---|---|
| 查看审核状态 | GET | `/api/user/expert/audit` | |
| 提交认证资料 | POST | `/api/user/expert/apply` | 首次申请或被驳回后重新提交 |

**状态回显字段（来自 GET 响应 data）**

| 页面位置 | 字段 | 说明 |
|---|---|---|
| 状态标题 | auditStatus | 0待审核/1通过/2驳回 |
| 驳回原因 | auditRemark | auditStatus=2 驳回时显示 |
| 提交时间 | submitTime | |

**提交 POST `/api/user/expert/apply` 请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| specialty | string | 是 | 专长领域（如"蔬菜种植、病虫害防治"） |
| title | string | 否 | 职称（如"高级农艺师"） |
| introduction | string | 否 | 个人简介 |
| consultPrice | decimal | 否 | 咨询价格，默认 0 |
| certification | string | 否 | 资质证书图片URL（ImageUpload 上传） |

> 注意：后端此接口**不接收** realName 和 phone 字段（因为专家记录通过 user_id 关联 user 表，realName/phone 从 user 表取），前端不要传。后端还多接收一个 consultPrice 字段，前端要带上。

**业务逻辑**
```
未申请(auditStatus 不存在或接口返回 data=null) → 显示完整表单，可提交
待审核(auditStatus=0) → 显示"资料审核中..."提示，表单禁用
通过(auditStatus=1) → 显示"恭喜，已成为认证专家"，表单禁用
驳回(auditStatus=2) → 显示驳回原因 + 允许修改重提（重新提交会覆盖上次的资料）
农民角色(role=1) → 也可以填写这个表单申请成为专家
```

---

## 三、两个人配合约定

| 事情 | 做法 |
|---|---|
| 公共组件 | ImageUpload / CategorySelect / StatusTag 直接用 |
| 用户信息 | `import { useUserStore } from '@/store/user'` 然后 `store.userInfo` / `store.role` / `store.isAdmin` |
| 登录跳转 | 登录成功后 role=3 跳 `/admin/dashboard`，其他跳 `/home` |
| 退出登录 | `store.logout()` + `router.push('/login')` |
| 前端 B 的路由 | 已在 router/index.js 配好，B 只需创建对应 vue 文件 |
| api/ 函数 | 各自写自己模块的 api/*.js，category.js / common.js A 写好后 B 直接 import |
| 商品图片字段 | 后端统一叫 `image`，不是 coverImage（知识封面才叫 coverImage） |

---

## 四、后端接口对应总表

| A 的页面 | 调后端接口 | 后端属于 |
|---|---|---|
| Login / Register | auth/login, auth/register | 后端A |
| Home | category/1, product, expert, finance/products, knowledge/hot | A+B+C |
| ProductList / ProductDetail | product 全部 | 后端B |
| DemandList / DemandDetail | demand 全部（只读） | 后端B |
| Cart | cart 全部 | 后端B |
| OrderCreate | order + user/address | B+A |
| OrderListPurchase / Sale / Detail | order 全部 | 后端B |
| MyProducts | product/my + product CRUD | 后端B |
| MyDemands | demand/my + demand CRUD | 后端B |
| FinanceService / Match / Apply / My | finance 全部 | 后端C |
| UserInfo / Password / Address | user 全部 | 后端A |
| ExpertAudit | user/expert/audit, user/expert/apply | 后端A |