# j-multi-cache
#### 作者 Author：VEVOLY

🚀 **一个基于 Redis (L2) + Caffeine (L1) 的轻量级、高性能分布式多级缓存框架。**  
🚀 **A lightweight, high-performance distributed multi-level cache framework based on Redis (L2) + Caffeine (L1).**

专为 Spring Boot 设计，旨在解决高并发场景下的缓存难题。提供注解式缓存、防缓存穿透、自动刷新、分布式一致性保障以及多种复杂数据结构支持。  
Designed for Spring Boot to solve caching challenges in high-concurrency scenarios. It offers annotation-based caching, anti-penetration strategies, auto-refresh, distributed consistency, and support for various complex data structures.

---

## ✨ 核心特性 / Key Features

*   **🌞 多级缓存架构 / Multi-level Caching Architecture**  
    *   **L1 (本地缓存 / Local)**：集成 **Caffeine**，极速访问，支持进程内高频读取，微秒级响应。  
    *   **L2 (分布式缓存 / Distributed)**：集成 **Redis (Redisson)**，支持分布式共享，数据持久化，防止应用重启导致缓存雪崩。  
    *   **L1 (Local)**: Integrated with **Caffeine** for ultra-fast access, supporting high-frequency in-process reads with microsecond latency.
    *   **L2 (Distributed)**: Integrated with **Redis (Redisson)** for distributed sharing and data persistence, preventing cache avalanches during application restarts.
*   **⚡️️ 快速开发 / Fast Developer**
    *   **约定优于配置**: 仅需一行 `@JMultiCacheable` 注解，框架即可根据类名自动推断配置，无需任何参数，真正实现零样板代码。
    *   **Convention Over Configuration**: Achieve automatic caching with a single `@JMultiCacheable` line. The framework infers configuration from class names, requiring zero parameters or boilerplate code.
*   **🔄 分布式一致性保障 / Distributed Consistency**
    *   内置 **Redis Pub/Sub 广播机制**。当某个节点执行删除/更新操作时，自动广播通知集群内所有节点清理本地 L1 缓存，有效防止脏读。
    *   Built-in **Redis Pub/Sub broadcast mechanism**. When a node executes a delete/update operation, it automatically broadcasts a notification to all cluster nodes to clear their local L1 cache, effectively preventing dirty reads.
*   **🛡️ 健壮性设计 / Robustness Design**
    *   **防缓存穿透 (Anti-Penetration)**：自动缓存空值（Null Object Pattern），支持配置空值标记和 TTL，防止恶意请求击穿数据库。
    *   **防缓存击穿 (Anti-Breakdown)**：内置分布式锁机制，在高并发下只允许一个线程回源查询 DB。
    *   **无感降级 (Graceful Degradation)**：若未启用配置（`@EnableJMultiCache`），框架自动切换为直连 DB 模式，业务代码无需修改。
    *   **Anti-Breakdown**: Built-in distributed locking mechanism ensures only one thread fetches data from the DB during high concurrency.
    *   **Anti-Penetration**: Automatically caches null values (Null Object Pattern) with configurable markers and TTL to prevent malicious requests from hitting the database.
    *   **Graceful Degradation**: If the configuration (`@EnableJMultiCache`) is not enabled, the framework automatically switches to direct DB connection mode without requiring code changes.
*   **📦 丰富的数据结构支持 / Rich Data Structure Support**
    *   不仅支持普通的 Key-Value (String)，还原生支持 **List**、**Set**、**Map**、**Page**、**Hash** 的序列化存储。
    *   特别优化 Spring Data **Page** 分页对象的序列化与反序列化。
    *   支持用户自定义数据存储结构
    *   Supports not only standard Key-Value (String) but also native serialization for **List**, **Set**, **Map**, **Page**, and **Hash**.
    *   Specially optimized serialization and deserialization for Spring Data **Page** objects.
    *   Supports user-defined storage-data structures.
*   **🛠️ 极致的开发体验 / Ultimate Developer Experience**
    *   **SpEL 表达式**: 支持通过 SpEL 灵活定义缓存 Key（包括带有固定后缀的key），支持多参数组合。用法详见常见问题4.  
    *   **代码生成器**: 提供工具类自动读取 YAML 配置生成 Java 枚举，拒绝在代码中硬编码字符串 Key。
    *   **缓存预热**: 支持应用启动时自动从数据库加载热点数据到 L1/L2。
    *   **SpEL Support**: Flexible cache Key definition via SpEL(including fixed suffix), supporting multi-parameter combinations. For usage, see FAQ 4.  
    *   **Code Generator**: Provides tools to auto-generate Java Enums from YAML configs, eliminating hardcoded string Keys.
    *   **Cache Preloading**: Supports automatic loading of hot data from the database into L1/L2 during application startup.

---

## ⚙️ 架构原理 / Architecture

1.  **读取流程 (Read Flow)**：
    *   请求 -> 检查 Caffeine (L1) -> **命中** -> 返回。
    *   L1 未命中 -> 检查 Redis (L2) -> **命中** -> 回填 L1 -> 返回。
    *   L2 未命中 -> **获取分布式锁** -> 查询 DB -> 回填 L2 & L1 -> 返回。
    *   *注：若 DB 返回空，则写入可自定义的空值占位符 (TTL 较短)，防止穿透。*
    *   Request -> Check Caffeine (L1) -> **Hit** -> Return.
    *   L1 miss -> Check Redis (L2) -> **Hit** -> Write back to L1 -> Return.
    *   L2 miss -> **Acquire Distributed Lock** -> Query DB -> Write back to L2 & L1 -> Return.
    *   *(Note: If DB returns null, a customizable null placeholder with a short TTL is written to prevent penetration.)*

2.  **写入/删除流程 (Write/Evict Flow)**：
    *   业务更新数据 -> 删除 DB 数据（由业务控制）。
    *   调用框架 `evict()` -> 删除 Redis (L2)。
    *   删除本机 Caffeine (L1)。
    *   **发送 Redis 广播消息** -> 其他节点收到消息 -> 删除各自的 Caffeine (L1)。
    *   Business updates data -> Delete DB data (controlled by business).
    *   Call framework `evict()` -> Delete Redis (L2).
    *   Delete local Caffeine (L1).
    *   **Send Redis broadcast message** -> Other nodes receive message -> Delete their own Caffeine (L1).

3.  **缓存预热 (Warm Up)**：
    *   应用启动 -> 扫描 `@JMultiCachePreloadable` 或接口 -> 执行加载逻辑 -> 批量写入 L1 & L2。
    *   Application startup -> Scan `@JMultiCachePreloadable` or interface -> Execute loading logic -> Batch write to L1 & L2.

---

## 📦 快速开始 / Quick Start
#### 可以拉简单测试项目，快速了解如何使用【J-Multi-Cache-Test】https://github.com/vevoly/j-multi-cache-test
#### You can pull the simple test project, as quickly as to start. [J-Multi-Cache-Test](https://github.com/vevoly/j-multi-cache-test)
### 1. 引入依赖 / Add Dependency
#### 1.1 Maven
```xml
<!-- https://mvnrepository.com/artifact/io.github.vevoly/j-multi-cache-spring-boot-starter -->
<dependency>
    <groupId>io.github.vevoly</groupId>
    <artifactId>j-multi-cache-spring-boot-starter</artifactId>
    <version>1.0.2</version>
</dependency>
```
#### 1.2 Gradle
```gradle   
// https://mvnrepository.com/artifact/io.github.vevoly/j-multi-cache-spring-boot-starter
implementation("io.github.vevoly:j-multi-cache-spring-boot-starter:1.0.0")
```

### 2. 启用缓存 / Enable Caching

在 Spring Boot 启动类上添加 `@EnableJMultiCache` 注解。  
Add the `@EnableJMultiCache` annotation to your Spring Boot application class.

```java
@SpringBootApplication
// 默认开启启动时缓存预热，preload = false 关闭缓存预热
// Cache preloading is enabled by default. Set preload = false to disable it.
@EnableJMultiCache 
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 配置文件 / Configuration (application.yml)

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0

j-multi-cache:
  enabled: true # 框架总开关 / Global switch
  
  # 全局默认配置 / Global defaults
  defaults:
    redis-ttl: 1h               # L2 Redis expiration
    local-ttl: 5m               # L1 Local expiration
    local-max-size: 1000        # L1 Max size
    empty-cache-ttl: 10s        # Anti-penetration: Null value TTL
    empty-cache-value: "[BINGO]"  # Null value placeholder

  # 具体缓存项配置 / Specific cache configurations
  configs:
    # 场景 1: 用户信息 (单体对象) / Scenario 1: User Info (Single Object)
    TEST_USER:
      namespace: "app:user:info"
      redis-ttl: 30m
      local-ttl: 1m
      entity-class: "com.example.entity.User" # 指定序列化类型 / Specify serialization type
      key-field: "#id" # SpEL 表达式，取参数中的 id 字段 / SpEL expression to get 'id' from args

    # 场景 2: 用户列表 (List 结构) / Scenario 2: User List (List Structure)
    TEST_USER_LIST:
      namespace: "app:user:list"
      storage-type: list # 声明存储结构为 List / Declare storage type as List
      entity-class: "com.example.entity.User"
      key-field: "#tenantId"
      redis-ttl: 30m    # 配置同下方注释 / ttl setting same blow 
      local-ttl: 0      # ⚠️注意！如果不使用该级别缓存 ttl配置为0，-1 为永久，null将会取默认值 
                        # ⚠️Notice: If not using this level cache, ttl is configured as 0, -1 is permanent, null will take the default value
      local-max-size: 1000

    # 场景 3: 用户信息 (Map 结构) / Scenario 3: User Info (Map Structure)
    TEST_USER_DETAIL:
      namespace: "app:user:detail"
      storage-type: string # 声明存储结构为 string (即Object) / Declare as string (object)
      # storage-type 系统默认值为 string，如果存储类型为Object或String，即可以省略
      entity-class: "com.example.entity.User"
      key-field: "#tenantId + ':' + #userId" # 组合 Key / Composite Key
      redis-ttl: 30m
      local-ttl: 1m
      local-max-size: 1000
```
---

## 💻 使用指南 / Usage Guide

### 1. 注解式使用 (推荐) / Annotation (Recommended)

在 Service 方法上添加 `@JMultiCacheable`，即可自动接管缓存逻辑。  
Add `@JMultiCacheable` to Service methods to automatically handle caching logic.

```java
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 自动查询 L1 -> L2 -> DB，并回填缓存。
     * Key 生成规则: app:user:info:{id}
     * 如果缓存名是类名的大写形式（这里缓存名为TEST_USER），可以省略 configName
     * 
     * Automatically query L1 -> L2 -> DB, and fill the cache.
     * Key generation rule: app:user:info:{id}
     * If the cache name is the uppercase form of the class name (here the cache name is TEST_USER), you can omit configName
     */
    @JMultiCacheable 
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }
    
    /**
     * 复杂 Key 示例
     * Key 生成规则: app:user:detail:{tenantId}:{userId}
     * 
     * Complicated Key Example
     * Key generation rule: app:user:detail:{tenantId}:{userId}
     */
    @JMultiCacheable(configName = "TEST_USER_DETAIL")
    public User getUserByTenant(String tenantId, Long userId) {
        // key为多id组合，在自动模式下入参必须与字段名相同
        // Key is a composite ID; in auto mode, parameter names must match field names
        return userMapper.selectUser(tenantId, userId);
    }
}
```

### 2. 手动 API 调用 / Manual API

对于复杂的业务逻辑，或者无法使用注解的场景，注入 `JMultiCache` 接口。  
For complex logic or scenarios where annotations apply, inject the `JMultiCache` interface.

```java
@Autowired
private JMultiCache jMultiCache;

public User getUserManual(Long id) {
    // fetchData 自动处理了 L1/L2/DB 回源、锁、空值缓存等所有逻辑
    // fetchData automatically handles L1/L2/DB fetching, locking, null caching, etc.
    return jMultiCache.fetchData(
        "TEST_USER_CACHE",               // 配置名 / Config Name
        () -> userMapper.selectById(id), // DB 回源逻辑 / DB Fetch Logic
        String.valueOf(id)               // 动态 Key 参数 / Dynamic Key Params
    );
}
```

### 3. 缓存管理与清理 (Ops) / Management & Ops

注入 `JMultiCacheOps` 进行缓存删除、预热等运维操作。  
Inject `JMultiCacheOps` for operations like cache eviction and preloading.

```java
@Autowired
private JMultiCacheOps cacheOps;

public void updateUser(User user) {
    // 1. 更新数据库 / Update DB
    userMapper.updateById(user);
    
    // 2. 删除缓存 (自动广播通知所有节点清理 L1)
    // Evict cache (Automatically broadcasts to all nodes to clear L1)
    cacheOps.evict("TEST_USER_CACHE", user.getId());
}
```

### 4. 缓存预热 (Preload) / Cache Preloading

实现 `JMultiCachePreload` 接口或者添加 `@JMultiCachePreloadable` 注解。应用启动时，框架会自动扫描并执行预热逻辑。
Implement `JMultiCachePreload` or add `@JMultiCachePreloadable`. The framework scans and executes preloading on startup.

#### 4.1 自动模式 / Automatic Mode

```java
// 如果缓存名是类名的大写形式（这里缓存名为TEST_USER），可以省略类型
// If the cache name is the uppercase of the class name, parameters can be omitted
@Service
@JMultiCachePreloadable 
public class UserService {
    // ...
}
```
#### 4.2 手动模式 / Manual Mode

```java
@Service
public class UserService implements JMultiCachePreload {

    @Autowired
    private JMultiCacheOps cacheOps;

    /**
     * 手动模式需实现 JMultiCachePreload 接口
     * Manual mode requires implementing JMultiCachePreload interface
     */
    @Override
    public int preloadMultiCache() {
        // 1. 从数据库查询热点数据 / Fetch hot data from DB
        List<User> hotUsers = userMapper.selectHotUsers();
        
        // 2. 转换为 Map<RedisKey, Entity>
        // 注意：Key 不需要带 namespace 前缀，框架会自动处理
        // Note: Key does not need namespace prefix; framework handles it.
        
        // 也可以使用框架自带的 stream 工具简化代码
        // Or use the framework's internal stream utils
        Map<String, User> data = StreamUtils.listToMap(hotUsers, User::getId);
            
        // 3. 批量写入 L1 和 L2 / Batch write to L1 & L2
        return cacheOps.preloadMultiCache("TEST_USER", data);
    }
}
```
### 5. 自定义存储结构 (Custom Storage Structure)

如果内置的 `list`, `set`, `zset`, `string`, `hash`, `page`, `union` 无法满足需求（例如需要**压缩存储**大文本，或进行**加密存储**），您可以轻松扩展自定义策略。  
If built-in types like `list`, `set`, `zset` don't meet your needs (e.g., you need **compression** for large text or **encryption**), you can easily extend custom strategies.

#### 5.1 实现策略接口 / Implement Strategy Interface
创建一个类实现 `RedisStorageStrategy<T>` 接口，并定义一个唯一的 `storageType`。  
Create a class that implements `RedisStorageStrategy<T>` and define a unique `storageType`.

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.core.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.core.redis.RedisClient;
import io.github.vevoly.jmulticache.core.strategy.RedisStorageStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component // 方式 A: 使用 @Component 自动扫描 / Method A: Auto-scan via @Component
public class GzipStorageStrategy implements RedisStorageStrategy<Object> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getStorageType() {
        return "gzip"; // 自定义类型名称 / Custom type name
    }

    @Override
    public Object read(RedisClient client, String key, TypeReference<Object> typeRef, ResolvedJMultiCacheConfig config) {
        String base64 = (String) client.get(key);
        if (base64 == null) return null;
        // 解压逻辑 (伪代码) / Decompress logic (Pseudo code)
        String json = GzipUtils.decompress(base64);
        return objectMapper.readValue(json, typeRef);
    }

    @Override
    public void write(RedisClient client, String key, Object value, ResolvedJMultiCacheConfig config) {
        // 压缩逻辑 (伪代码) / Compress logic (Pseudo code)
        String json = objectMapper.writeValueAsString(value);
        String base64 = GzipUtils.compress(json);
        client.set(key, base64, config.getRedisTtl());
    }

    // ... implement other methods (readMulti, writeMulti)
}
```
#### 5.2 注册策略 Bean / Register Strategy Bean
确保您的策略类被 Spring 容器管理。如果您的策略类不在 Spring Boot 主程序的扫描路径下，需要手动导入。  
Ensure your strategy class is managed by Spring. If it's outside the main scan path, import it manually.  
*   方式 A：自动扫描 (推荐) / Method A: Auto Scan (Recommended)  
在类上添加 @Component，并确保它在 @SpringBootApplication 的包或子包下。  
Add @Component and ensure it's under the package of @SpringBootApplication.  
*   方式 B：显式导入 / Method B: Explicit Import  
在配置类上使用 @Import 导入。
Use @Import on your configuration class.
```java
@Configuration
@Import(GzipStorageStrategy.class) // 手动注册 / Manual registration
public class CacheConfig {
}
```
#### 5.3 修改配置 / Configure YAML
在配置文件中，将 storage-type 设置为您定义的名称。  
In application.yml, set storage-type to your defined name.
```yaml
j-multi-cache:
  configs:
    BIG_ARTICLE_CACHE:
      namespace: "app:article:content"
      storage-type: gzip  # 🔥 对应 getStorageType() 的返回值 / Matches return value
      redis-ttl: 24h
      local-ttl: 10m
      entity-class: "com.example.entity.Article"
      key-field: "#id"
```

## 🛠 进阶工具：枚举生成器 / Advanced Tool: Enum Generator

为了避免在代码中手写 `"TEST_USER"` 这种容易出错的字符串，框架提供了代码生成工具。它会读取 `application.yml` 并生成 Java 枚举。  
To avoid hardcoding magic strings like `"TEST_USER"`, the framework provides a code generator that reads `application.yml` and generates Java Enums.

**在单元测试中运行一次即可 / Run once in a Unit Test:**

```java
import org.junit.jupiter.api.Test;
import io.github.vevoly.jmulticache.core.utils.JMultiCacheEnumGeneratorImpl;
import java.io.IOException;

class CodeGenTest {
    @Test
    void generateEnums() throws IOException {
        // 自动扫描 application.yml，生成枚举到 src/main/java 下，具体使用参数请查阅文档
        // Automatically scans application.yml and generates Enums in src/main/java
        new JMultiCacheEnumGeneratorImpl().generateEnum();
    }
}
```

**生成后的效果 / Generated Result:**

```java
public enum JMultiCacheName implements MultiCacheConfigName {
    TEST_USER_CACHE("app:user:info"),
    TEST_USER_LIST("app:user:list");
    // ...
}
```

**在代码中使用枚举（类型安全） / Usage (Type-Safe):**

```java
@JMultiCacheable(configName = JMultiCacheName.TEST_USER)
public User getUser(Long id) {
    // 可以使用枚举类 buildKey 方法构建完整的缓存 key
    // You can use the Enum's buildKey method to construct the full cache key
    String fullKey = JMultiCacheName.TEST_USER.buildKey(id);
}
```

---

## ⚠️ 常见问题 (FAQ)

### 1. 内部调用导致缓存失效？/ Self-invocation causes cache failure?
**现象**：在同一个类中，方法 A 调用带缓存注解的方法 B，缓存不生效。
**Symptom**: Method A calls annotated Method B within the same class, but caching doesn't work.
**原因**：Spring AOP 的代理机制限制，`this` 调用不会经过代理类。
**Reason**: Spring AOP proxy limitations; `this` calls do not pass through the proxy.
**解决**：使用 **自我注入 (Self-Injection)**。
**Solution**: Use **Self-Injection**.

```java
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    @Lazy // 加上 Lazy 防止循环依赖 / Add Lazy to prevent circular dependency
    private UserService self; 

    public void methodA() {
        // ❌ 错误：this.methodB() -> 缓存失效 / Error: cache fails
        // ✅ 正确：self.methodB() -> 走代理，缓存生效 / Correct: passes through proxy
        self.methodB();
    }

    @JMultiCacheable(...)
    public void methodB() { ... }
}
```

### 2. Redis 乱码问题？/ Redis garbled data?
框架底层使用了 `Redisson` 并强制配置了 `StringCodec`。  
The framework uses `Redisson` under the hood and enforces `StringCodec`.  
请确保不要混用 Spring Boot 默认的 `RedisTemplate<Object, Object>`（它使用 JDK 序列化）。  
Please ensure you do not mix it with Spring Boot's default `RedisTemplate<Object, Object>` (which uses JDK serialization).  
**验证数据时，请使用 `StringRedisTemplate`。**  
**Use `StringRedisTemplate` when verifying data.**

### 3. key-field 与 businessKey 的区别？/ What's the difference between key-field and businessKey?
* key-field (YAML 配置 / Config):
  * 作用阶段: 请求前。
  * 数据来源: 方法入参。
  * 语法: SpEL 表达式。
  * 目的: 告诉框架 “怎么生成 Key 去查缓存”。
  * Phase: Before Query.
  * Source: Method Arguments.
  * Syntax: SpEL (e.g., #id).
  * Purpose: Tells the framework "How to build the key to query the cache".
* businessKey (代码参数 / Code Param):
  * 作用阶段: 手动模式批量查库后。
  * 数据来源: 数据库返回的实体对象。
  * 语法: Java 字段名 (String) (如 "userId", "id")。
  * 目的: 告诉框架 “这个查回来的对象属于哪个 Key” (用于将 DB 结果回填到 Redis)。
  * Phase: After Batch Query By Manual.
  * Source: DB Result Entity.
  * Syntax: Java Field Name (e.g., "userId").
  * Purpose: Tells the framework "Which key does this object belong to" (Used to populate Redis after a DB miss).
---

### 4. 如何处理复杂拼接的缓存 Key？/ How to handle complex cache keys?

**场景**：业务需要根据多个参数组合生成 Key，或者 Key 包含固定的后缀。  
**Scenario**: The business logic requires generating a Key based on multiple parameters, or the Key contains a fixed suffix.  
**例如**：`app:user:1001:detail` (Namespace + ID + Suffix)。

框架提供了强大的 **SpEL (Spring Expression Language)** 支持来解决此类问题。
The framework provides powerful **SpEL** support to solve such problems.

#### 4.1 配置 SpEL 表达式 / Configure SpEL

在 `application.yml` 中定义拼接规则。  
Define the concatenation rule in `application.yml`.

```yaml
j-multi-cache:
  configs:
    USER_DETAIL_CACHE:
      namespace: "app:user"
      # 组合参数，并添加固定后缀 / Combine params and add fixed suffix
      # 最终 Key: app:user:{id}:suffix
      key-field: "#id + ':suffix'"
  ```

#### 4.2 注解调用 (自动处理) / Annotation (Auto)
方法参数名需与 SpEL 变量对应。  
Method parameter names must match SpEL variables.
```java
@JMultiCacheable(configName = "USER_DETAIL_CACHE")
public User getUser(Long id) {
    // Framework generates: app:user:1001:suffix
    return userMapper.select(id);
}
```

#### 4.3 手动 API 调用  / Manual API \
不要自己拼接字符串，而是将参数传给框架，让框架根据配置自动生成。   
Do not concatenate strings manually. Pass parameters to the framework, and let it generate the key based on the config.
```java
// 调用方法1 / Call method 1
jMultiCache.fetchData(
    "USER_DETAIL_CACHE",
    () -> userMapper.select(id),
    String.valueOf(id), "detail" // 传入参数，框架自动代入 SpEL
);

// 调用方法2 / Call method 2
// 适用于更为复杂的key拼接规则，用户拼接好完整的 key 字符串，传入参数即可
// Applicable to more complex key concatenation rules. Users can pass parameters after concatenating the complete key string.        
jMultiCache.fetchData("app:user:1001:detail", () -> dbLoader())
```

#### 4.4 获取计算后的 Key / Compute the Key
如果您仅仅想获取最终生成的 Redis Key 字符串，可以使用 computeKey 方法。  
If you just want to get the final generated Redis Key string (e.g., for logging), use the computeKey method.
```java
// 返回 / Returns: "app:user:1001:suffix"
String fullKey = jMultiCacheOps.computeKey("USER_DETAIL_CACHE", 1001, "suffix");
```
#### 4.5 获取简单的字符串拼接 Key / Get simple string concatenation key  
如果您只想获取简单的字符串拼接的key，可以使用 JMultiCacheHelper.buildKey 方法或者手动生成的名称枚举类的buildKey方法  
If you only want to get the simple string concatenation key, you can use the JMultiCacheHelper.buildKey() method or the buildKey method of the manually generated name enumeration class
```java
// 1. 使用 JMultiCacheHelper.buildKey 方法 / Use JMultiCacheHelper.buildKey method
String fullKey = JMultiCacheHelper.buildKey("app:user:", 1001, "suffix");
// 2. 使用名称枚举类的buildKey方法 / Use the buildKey method of the name enumeration class
String fullKey = JMultiCacheNameEnum.USER_DETAIL_CACHE.buildKey(1001, "suffix");
```

## 📝 License

Apache License 2.0