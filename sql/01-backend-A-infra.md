# 后端 A - 公共层 + 基础设施

## 负责范围
Result / Constants / BusinessException / ExceptionHandler / CorsConfig / SecurityConfig / UserContext / FileUtil / 认证 / 分类 / 用户管理

---

## 技术选型

| 项 | 方案 |
|---|---|
| 认证框架 | Spring Security + JWT（无状态） |
| 会话管理 | 关闭 HttpSession，使用 JWT 自包含认证 |
| JWT 库 | jjwt 0.12.5（`io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson`） |
| 密码加密 | BCryptPasswordEncoder |
| 自定义认证 | 实现 `UserDetailsService#loadUserByUsername()` 从 user 表查 |
| 权限控制 | 基于 user.role（1=农民/2=专家/3=管理员），接口层用 `@PreAuthorize` |
| CORS | 允许前端 `http://localhost:5173` 跨域 |
| Token 有效期 | AccessToken 24 小时，可按需调整 |

---

## 一、Spring Security 自定义认证核心配置

### 1.1 SecurityConfig（核心配置类）

```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 关闭 session，完全使用 JWT 无状态认证
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公开接口（无需 JWT）
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/category/**",
                    "/api/product", "/api/product/**",
                    "/api/demand", "/api/demand/**",
                    "/api/expert", "/api/expert/**",
                    "/api/knowledge", "/api/knowledge/**",
                    "/api/qa", "/api/qa/**",
                    "/api/finance/products",
                    "/api/finance/match",
                    "/api/common/upload",
                    "/error", "/doc.html", "/swagger-ui/**", "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // 禁用 formLogin，登录逻辑自己在 AuthController 里写
            .formLogin(form -> form.disable())
            // 禁用 logout（JWT 无需服务端登出，前端清 token 即可）
            .logout(logout -> logout.disable())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":403,\"msg\":\"权限不足\",\"data\":null}");
                })
            )
            // 在 UsernamePasswordAuthenticationFilter 之前插入 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.addAllowedOrigin("http://localhost:5173");
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

### 1.2 自定义 UserDetailsService（核心——从数据库查用户）

```java
@Service
public class DbUserDetailsService implements UserDetailsService {

    @Autowired private UserMapper userMapper;
    @Autowired private ExpertMapper expertMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectByUsername(username);
        if (user == null) throw new UsernameNotFoundException("用户不存在");
        if (user.getStatus() == 0) throw new DisabledException("账号已被禁用");

        // 把 role 数字 + Expert 扩展信息塞进 UserDetails 的 authorities 和 extraDetails
        List<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + roleToAuthority(user.getRole()))
        );

        return new LoginUser(user, authorities);
    }

    // role 1/2/3 → FARMER / EXPERT / ADMIN
    private String roleToAuthority(int role) {
        switch (role) {
            case 1: return "FARMER";
            case 2: return "EXPERT";
            case 3: return "ADMIN";
            default: return "USER";
        }
    }
}
```

### 1.3 LoginUser（UserDetails 实现类，承载完整用户信息）

```java
public class LoginUser implements UserDetails {
    private final User user;
    private final List<GrantedAuthority> authorities;

    public LoginUser(User user, List<GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    public User getUser() { return user; }

    @Override public String getUsername() { return user.getUsername(); }
    @Override public String getPassword() { return user.getPassword(); }
    @Override public boolean isEnabled() { return user.getStatus() == 1; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
```

### 1.4 JwtUtil（JWT 工具类）

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret:rongxiaotong-secret-key-please-change-in-production-minimum-32-chars}")
    private String secret;

    @Value("${jwt.expire-hours:24}")
    private long expireHours;

    // 生成 JWT，claims 里塞 userId / username / role
    public String generateToken(LoginUser loginUser) {
        User u = loginUser.getUser();
        return Jwts.builder()
            .subject(u.getUsername())
            .claim("userId", u.getId())
            .claim("role", u.getRole())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expireHours * 3600 * 1000))
            .signWith(getKey())
            .compact();
    }

    // 解析 JWT，失败返回 null
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```

**pom.xml 依赖**:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

### 1.5 JwtAuthenticationFilter（每个请求的 JWT 拦截器）

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final DbUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtUtil.parseToken(token);
            if (claims != null) {
                String username = claims.getSubject();
                // 再从数据库查一次，确保用户存在且未被禁用
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

### 1.6 在 Controller 里拿当前登录用户

```java
public class BaseController {
    protected User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser)) return null;
        return ((LoginUser) auth.getPrincipal()).getUser();
    }
    protected Long getCurrentUserId() {
        User u = getCurrentUser(); return u == null ? null : u.getId();
    }
}
```

---

## 二、接口层统一认证标注约定

前端请求头带 `Authorization: Bearer <token>`，JwtAuthenticationFilter 自动解析并设置 SecurityContext。Controller 层用注解声明谁能访问：

| 注解 | 含义 | 示例 |
|---|---|---|
| （无注解） | 登录即可访问 | `GET /api/order/purchase` |
| `@PreAuthorize("hasRole('ADMIN')")` | 仅管理员 | `GET /api/admin/users` |
| `@PreAuthorize("hasAnyRole('FARMER','EXPERT','ADMIN')")` | 任何登录角色 | 等价于已登录 |
| 公开（不进 Spring Security 拦截链） | 在 SecurityConfig 里 `.permitAll()` | `GET /api/product` |

获取当前用户一律用 `getCurrentUserId()` / `getCurrentUser()`，**不要从请求头手动解析 token**。

---

## 三、认证模块接口（前端调用的登录/注册/登出/当前用户）

### 3.1 用户注册

**请求**
```
POST /api/auth/register
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名，5-20位字母数字，唯一 |
| password | string | 是 | 密码，6-20位（后端会用 BCryptPasswordEncoder 加密后存库） |
| realName | string | 是 | 真实姓名 |
| phone | string | 是 | 手机号，11位数字 |
| role | int | 是 | 1=农民 2=专家（管理员role=3不开放注册） |
| email | string | 否 | 邮箱 |
| farmName | string | 否 | 农场/合作社名称（农民填） |

**请求示例**
```json
{
  "username": "lisi",
  "password": "123456",
  "realName": "李四",
  "phone": "13800000001",
  "role": 1,
  "email": "lisi@rx.com",
  "farmName": "山东烟台红富士果园"
}
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 2,
    "username": "lisi",
    "realName": "李四",
    "role": 1,
    "farmName": "山东烟台红富士果园"
  }
}
```

---

### 3.2 用户登录（自己写 AuthController，用 Spring Security 的 AuthenticationManager 认证后颁发 JWT）

**请求**
```
POST /api/auth/login
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名 |
| password | string | 是 | 明文密码，后端 BCrypt 校验 |

**请求示例**
```json
{
  "username": "admin",
  "password": "123456"
}
```

**登录成功响应**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInVzZXJJZCI6MSwicm9sZSI6MywiaWF0IjoxNzY5MDAwMDAwLCJleHAiOjE3NjkwODY0MDB9.xxxxxxxx",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "username": "admin",
      "realName": "超级管理员",
      "role": 3,
      "avatar": null
    }
  }
}
```

**登录失败响应**
```json
{ "code": 400, "msg": "用户名或密码错误", "data": null }
```

**账号禁用响应**
```json
{ "code": 400, "msg": "账号已被禁用", "data": null }
```

**AuthController 登录实现**
```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto) {
        // 用 Spring Security 的 AuthenticationManager 做认证
        // 它内部会调用我们写的 DbUserDetailsService.loadUserByUsername() 从数据库查用户
        // 然后用 BCryptPasswordEncoder 校验密码
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword())
        );

        // 认证通过，拿到 LoginUser（包含完整 user 实体）
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        User u = loginUser.getUser();

        // 生成 JWT
        String token = jwtUtil.generateToken(loginUser);

        // 组装返回
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", u.getId());
        userInfo.put("username", u.getUsername());
        userInfo.put("realName", u.getRealName());
        userInfo.put("role", u.getRole());
        userInfo.put("avatar", u.getAvatar());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("tokenType", "Bearer");
        data.put("expiresIn", 24 * 3600);
        data.put("user", userInfo);

        return Result.success(data);
    }
}
```

**LoginDTO**
```java
@Data
public class LoginDTO {
    @NotBlank private String username;
    @NotBlank private String password;
}
```

**后续请求使用**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

### 3.3 用户登出（JWT 无状态，前端清本地 token 即可）

**说明**：因为 JWT 是无状态的，服务端不保存 token，所以登出只需要前端把 localStorage 里的 token 清掉。后端不需要做任何事。

> 前端做法：`store.logout()` → 清空 localStorage 的 token 和 userInfo → 跳转登录页。

---

### 3.4 获取当前登录用户信息

**请求**
```
GET /api/auth/me
Authorization: Bearer <token>
```

> 前端带 token 即可，JwtAuthenticationFilter 自动解析。

**已登录响应**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "admin",
    "realName": "超级管理员",
    "phone": "13800000000",
    "email": "admin@rx.com",
    "avatar": null,
    "role": 3,
    "farmName": null
  }
}
```

**未登录响应**
```json
{ "code": 401, "msg": "未登录或登录已过期", "data": null }
```

**实现**
```java
@GetMapping("/me")
public Result<User> me() {
    User u = getCurrentUser();
    if (u == null) return Result.error(401, "未登录");
    return Result.success(u);
}
```

---

## 四、分类管理

### 4.1 查询分类列表（公开）

**请求**
```
GET /api/category/{type}
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| type | int | 是 | 1=商品分类 2=知识分类 3=问题分类 |

**请求示例**
```
GET /api/category/1
```

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": [
    { "id": 1, "name": "水果", "type": 1, "icon": "/images/cat1.jpg", "sort": 1 },
    { "id": 2, "name": "蔬菜", "type": 1, "icon": "/images/cat2.jpg", "sort": 2 },
    { "id": 3, "name": "粮食", "type": 1, "icon": "/images/cat3.jpg", "sort": 3 }
  ]
}
```

---

### 4.2 所有分类（管理员）

**请求**
```
GET /api/category
需登录 + 管理员（@PreAuthorize("hasRole('ADMIN')")）
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| type | int | 否 | 1/2/3筛选，不传返回全部 |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      { "id": 1, "name": "水果", "type": 1, "status": 1, "createTime": "2026-09-20 10:00:00" }
    ],
    "total": 9, "pageNum": 1, "pageSize": 10, "pages": 1
  }
}
```

---

### 4.3 新增分类（管理员）

**请求**
```
POST /api/category
需登录 + 管理员
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 分类名称 |
| type | int | 是 | 1=商品 2=知识 3=问题 |
| icon | string | 否 | 图标URL |
| sort | int | 否 | 排序，默认 0 |
| status | int | 否 | 0=禁用 1=启用，默认 1 |

**请求示例**
```json
{
  "name": "渔业水产",
  "type": 1,
  "sort": 5,
  "status": 1
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": { "id": 10 } }
```

---

### 4.4 修改分类（管理员）

**请求**
```
PUT /api/category/{id}
需登录 + 管理员
Content-Type: application/json
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 分类ID |

**请求参数 (Body)** 同新增，只传要修改的字段。

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 4.5 删除分类（管理员）

**请求**
```
DELETE /api/category/{id}
需登录 + 管理员
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 五、文件上传

### 5.1 单文件上传

**请求**
```
POST /api/common/upload
Content-Type: multipart/form-data
需登录（SecurityConfig 里可配置成 permitAll）
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| file | MultipartFile | 是 | 图片文件（jpg/png/jpeg/gif/webp，最大10MB） |

**响应示例**
```json
{
  "code": 200,
  "msg": "success",
  "data": { "url": "/images/20260920/abc123.jpg" }
}
```

---

### 5.2 多文件上传

**请求**
```
POST /api/common/upload/multi
Content-Type: multipart/form-data
需登录
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| files | MultipartFile[] | 是 | 多个图片文件 |

**响应示例**
```json
{
  "code": 200,
  "data": [
    { "url": "/images/20260920/abc1.jpg" },
    { "url": "/images/20260920/abc2.jpg" }
  ]
}
```

---

## 六、用户管理（个人 + 管理员）

### 6.1 获取当前用户信息（个人）

**请求**
```
GET /api/user/info
需登录
```

**实现方式**：Controller 里 `Long uid = getCurrentUserId(); User u = userMapper.selectById(uid);`

**响应示例（农民）**
```json
{
  "code": 200,
  "data": {
    "id": 2,
    "username": "lisi",
    "realName": "李四",
    "phone": "13800000001",
    "email": "lisi@rx.com",
    "avatar": "/images/avatar/u2.jpg",
    "role": 1,
    "farmName": "山东烟台红富士果园"
  }
}
```

**响应示例（专家，含扩展）**
```json
{
  "code": 200,
  "data": {
    "id": 5,
    "username": "expert01",
    "realName": "陈农师",
    "role": 2,
    "expert": {
      "specialty": "水稻、小麦",
      "title": "高级农艺师",
      "introduction": "从事粮食作物研究30年",
      "consultPrice": 100.00,
      "auditStatus": 1
    }
  }
}
```

---

### 6.2 修改个人信息

**请求**
```
PUT /api/user/info
需登录
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| realName | string | 否 | 不传则不改 |
| phone | string | 否 | 不传则不改 |
| email | string | 否 | 不传则不改 |
| avatar | string | 否 | 头像URL |
| farmName | string | 否 | 农场名称 |

**请求示例**
```json
{
  "phone": "13899999999",
  "farmName": "山东烟台红富士精品果园"
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 6.3 修改密码

**请求**
```
PUT /api/user/password
需登录
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| oldPassword | string | 是 | 原密码（明文） |
| newPassword | string | 是 | 新密码，6-20位（明文，后端 BCrypt 加密存库） |

**实现**
```java
User u = getCurrentUser();
// 用 PasswordEncoder 校验原密码
if (!passwordEncoder.matches(oldPassword, u.getPassword())) {
    throw new BusinessException("原密码错误");
}
u.setPassword(passwordEncoder.encode(newPassword));
userMapper.updateById(u);
```

**请求示例**
```json
{
  "oldPassword": "123456",
  "newPassword": "654321"
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 6.4 收货地址列表

**请求**
```
GET /api/user/address
需登录
```

**响应示例**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1, "receiver": "李四", "phone": "13800000001",
      "province": "山东省", "city": "烟台市", "district": "栖霞区",
      "detail": "庄园路88号果园", "isDefault": 1
    }
  ]
}
```

---

### 6.5 新增收货地址

**请求**
```
POST /api/user/address
需登录
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| receiver | string | 是 | 收货人姓名 |
| phone | string | 是 | 手机号 |
| province | string | 是 | 省 |
| city | string | 是 | 市 |
| district | string | 是 | 区/县 |
| detail | string | 是 | 详细地址 |
| isDefault | int | 否 | 0=非默认 1=默认，默认0 |

**请求示例**
```json
{
  "receiver": "李四",
  "phone": "13800000001",
  "province": "山东省",
  "city": "烟台市",
  "district": "莱山区",
  "detail": "滨海路123号",
  "isDefault": 0
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": { "id": 2 } }
```

---

### 6.6 修改收货地址

**请求**
```
PUT /api/user/address/{id}
需登录
Content-Type: application/json
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 地址ID |

**请求参数 (Body)** 同新增，只传要改的字段。

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 6.7 删除收货地址

**请求**
```
DELETE /api/user/address/{id}
需登录
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 6.8 专家查看审核状态

**请求**
```
GET /api/user/expert/audit
需登录
```

**响应示例**
```json
{
  "code": 200,
  "data": {
    "auditStatus": 0,
    "auditRemark": null,
    "submitTime": "2026-09-20 10:00:00"
  }
}
```

---

### 6.9 专家提交认证资料

**请求**
```
POST /api/user/expert/apply
需登录
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| specialty | string | 是 | 专长领域，如"水稻、果树栽培" |
| title | string | 否 | 职称 |
| introduction | string | 否 | 个人简介 |
| consultPrice | decimal | 否 | 咨询价格，默认 0 |
| certification | string | 否 | 资质证书图片URL |

**请求示例**
```json
{
  "specialty": "水稻、小麦",
  "title": "高级农艺师",
  "introduction": "从事粮食作物研究30年",
  "consultPrice": 100.00,
  "certification": "/images/cert/expert01.jpg"
}
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 七、管理员用户管理

### 7.1 用户列表（管理员）

**请求**
```
GET /api/admin/users
需登录 + 管理员（@PreAuthorize("hasRole('ADMIN')")）
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 用户名/真实姓名模糊搜索 |
| role | int | 否 | 1/2/3 |
| status | int | 否 | 0禁用/1启用 |
| pageNum | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 2, "username": "lisi", "realName": "李四",
        "phone": "13800000001", "role": 1, "status": 1,
        "createTime": "2026-09-19 10:00:00"
      }
    ],
    "total": 128, "pageNum": 1, "pageSize": 10, "pages": 13
  }
}
```

---

### 7.2 启停用用户（管理员）

**请求**
```
PUT /api/admin/users/{id}/status
需登录 + 管理员
Content-Type: application/json
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 用户ID |

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 是 | 0禁用 1启用 |

**请求示例**
```json
{ "status": 0 }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 7.3 修改用户角色（管理员）

**请求**
```
PUT /api/admin/users/{id}/role
需登录 + 管理员
Content-Type: application/json
```

**请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| role | int | 是 | 1农民 2专家 3管理员 |

**请求示例**
```json
{ "role": 2 }
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 7.4 删除用户（管理员）

**请求**
```
DELETE /api/admin/users/{id}
需登录 + 管理员
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 7.5 专家列表 + 审核（管理员）

**请求**
```
GET /api/admin/experts
需登录 + 管理员
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditStatus | int | 否 | 0待审核/1已通过/2已驳回；空=全部 |
| pageNum | int | 否 | 默认 1 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "user": { "id": 5, "username": "expert01", "realName": "陈农师", "phone": "13811111111" },
        "specialty": "水稻、小麦",
        "title": "高级农艺师",
        "consultPrice": 100.00,
        "auditStatus": 0,
        "auditRemark": null,
        "submitTime": "2026-09-20 10:00:00"
      }
    ],
    "total": 3, "pageNum": 1, "pageSize": 10, "pages": 1
  }
}
```

---

### 7.6 专家审核通过 / 驳回（管理员）

**请求**
```
PUT /api/admin/experts/{id}/approve    # 通过
PUT /api/admin/experts/{id}/reject     # 驳回
需登录 + 管理员
```

**驳回请求参数 (Body)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| auditRemark | string | 驳回时必填 | 驳回原因 |

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 7.7 商品列表 + 审核（管理员）

**请求**
```
GET /api/admin/products
需登录 + 管理员
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 商品名搜索 |
| auditStatus | int | 否 | 0待审核/1已通过/2已驳回 |
| status | int | 否 | 0下架/1上架 |
| pageNum | int | 否 | 默认 1 |

**响应示例**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1, "name": "山东红富士苹果",
        "image": "/images/apple1.jpg",
        "price": 5.80,
        "seller": { "id": 2, "realName": "李四" },
        "auditStatus": 1, "status": 1,
        "createTime": "2026-09-18 10:00:00"
      }
    ],
    "total": 10, "pageNum": 1, "pageSize": 10, "pages": 1
  }
}
```

---

### 7.8 商品审核通过 / 驳回 / 强制下架 / 删除（管理员）

**请求**
```
PUT /api/admin/products/{id}/approve      # 通过
PUT /api/admin/products/{id}/reject       # 驳回  （Body: { auditRemark: "驳回原因" }）
PUT /api/admin/products/{id}/status       # 强制下架（Body: { status: 0 }）
DELETE /api/admin/products/{id}           # 删除
需登录 + 管理员
```

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 八、管理员扩展接口（需求 / 订单 / 融资 / 知识 / 统计）

> 本章接口由后端 C 迁移而来，统一归后端 A 管理。URL 风格与第七章一致（审核类用 `/approve` + `/reject` 分开两个端点）。

### 8.1 需求列表（管理员）

**请求**
```
GET /api/admin/demands
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0已关闭/1进行中/2已完成 |
| keyword | string | 否 | 搜索需求名称 |
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
        "name": "需要苹果纸箱",
        "quantity": 5000,
        "expectedPrice": 2.50,
        "status": 1,
        "buyer": { "id": 3, "realName": "王五" },
        "createTime": "2026-09-10 10:00:00"
      }
    ],
    "total": 45,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 5
  }
}
```

---

### 8.2 删除需求（管理员）

**请求**
```
DELETE /api/admin/demands/{id}
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 需求ID |

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 8.3 订单列表（管理员）

**请求**
```
GET /api/admin/orders
```

**请求参数 (Query)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | int | 否 | 0待支付/1已支付/2已发货/3已完成/4已取消 |
| orderNo | string | 否 | 订单号搜索 |
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
        "orderNo": "RX20260920001",
        "totalAmount": 290.00,
        "status": 3,
        "refundStatus": 0,
        "buyer": { "id": 3, "realName": "王五" },
        "seller": { "id": 2, "realName": "李四" },
        "createTime": "2026-09-18 10:28:00"
      }
    ],
    "total": 1203,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 121
  }
}
```

---

### 8.4 融资申请列表（管理员）

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

### 8.5 融资审核（管理员）

**请求**
```
PUT /api/admin/finances/{id}/approve      # 通过
PUT /api/admin/finances/{id}/reject       # 驳回  （Body: { reviewRemark: "驳回原因" }）
需登录 + 管理员
```

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 融资申请ID |

**请求参数 (Body — 仅 reject 需要)**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| reviewRemark | string | 否 | 审核备注 |

**响应示例**
```json
{ "code": 200, "msg": "success", "data": null }
```

---

### 8.6 知识列表（管理员）

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

### 8.7 知识上下架（管理员）

**请求**
```
PUT /api/admin/knowledges/{id}/status
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

### 8.8 删除知识（管理员）

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

### 8.9 数据统计概览（管理员）

**请求**
```
GET /api/admin/stats/overview
```

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
`user`、`expert`、`address`、`category`、`demand`（管理）、`orders`（管理）、`order_item`（管理）、`finance_apply`（管理）、`finance_product`（管理）、`knowledge`（管理）

## application.yml 关键配置
```yaml
jwt:
  secret: rongxiaotong-secret-key-please-change-in-production-minimum-32-chars
  expire-hours: 24

spring:
  mvc:
    throw-exception-if-no-handler-found: true
```

## 本次 Spring 组件清单

| 类名 | 作用 | 位置建议 |
|---|---|---|
| `SecurityConfig` | Security 全局配置 + 无状态 + JWT 过滤器 | `config/` |
| `DbUserDetailsService` | **自定义认证逻辑（从 DB 查 user 表）** | `service/security/` |
| `LoginUser` | UserDetails 实现类，承载 User 实体 + authorities | `service/security/` |
| `JwtUtil` | JWT 生成 / 解析 / 验证 | `utils/` |
| `JwtAuthenticationFilter` | 每个请求拦截，解析 token 设置 SecurityContext | `config/security/` |
| `AuthController` | 登录 / 注册 / 当前用户 接口 | `controller/` |
| `BaseController` | 统一获取当前用户 | `controller/` 基类 |
| `PasswordEncoder` 配置 | BCryptPasswordEncoder | `SecurityConfig` 内 `@Bean` |
| `AuthenticationManager` 配置 | 暴露给 AuthController 做认证 | `SecurityConfig` 内 `@Bean` |

## 认证流程图解
```
前端登录请求 POST /api/auth/login
    │
    ▼
AuthController.login()
    │
    ▼
authenticationManager.authenticate(usernamePasswordToken)
    │
    ▼
DbUserDetailsService.loadUserByUsername(username)
    │
    ├── userMapper.selectByUsername()  ← 从数据库查 user 表
    ├── 校验 status != 0
    └── 返回 LoginUser(user, authorities)
    │
    ▼
BCryptPasswordEncoder.matches(明文, 数据库密文)  ← 密码校验
    │
    ├── 失败 → 抛 BadCredentialsException → 全局异常处理返回 400
    └── 成功 → JwtUtil.generateToken(loginUser) → 返回 JWT 给前端

后续请求（带 Authorization: Bearer <token>）
    │
    ▼
JwtAuthenticationFilter.doFilterInternal()
    │
    ├── 解析 token → 取出 username
    ├── DbUserDetailsService.loadUserByUsername(username)  ← 再次从数据库查（防用户被删/禁用）
    └── 设置 SecurityContext → Spring Security 放行
```