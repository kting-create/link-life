# Link-Life P0 基础框架实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 Link-Life 后端基础框架：Spring Boot 3 模块化单体骨架、用户/圈子模块、JWT 鉴权、AI 网关骨架、Docker Compose 单机部署。

**Architecture:** 模块化单体（com.linklife 下按 user/circle/auth/common/ai 分包），MySQL 8 + MyBatis-Plus + Flyway 管理表结构，JWT 无状态鉴权，AI 能力收口在基于 Spring AI 的网关层。

**Tech Stack:** Java 17、Spring Boot 3.3.x、MyBatis-Plus 3.5.7、Flyway、jjwt 0.12.6、Spring AI 1.0.0（仅骨架）、MySQL 8、Testcontainers、Docker Compose。

**Spec:** `docs/superpowers/specs/2026-09-16-link-life-design.md`（本计划从 spec 推导，执行时两份文档一起读）

## Global Constraints

- Java 17，Spring Boot 3.3.x，Maven 构建，包根 `com.linklife`
- 仓库目录结构：`server/`（后端）、`deploy/`（部署配置）、`docs/`（文档）；小程序与 Web 前端目录在 P1 创建
- 所有 API 响应统一包装 `Result<T>`：`{"code":0,"message":"ok","data":...}`，业务错误 code 非 0
- 公开端点（免 JWT）：`/api/health`、`/api/auth/**`；其余全部需要 Bearer Token
- JWT：HS256，secret ≥ 32 字节经环境变量注入，access token 7 天，refresh token 30 天
- 数据库表名与 spec 一致：`user`、`circle`、`circle_member`、`user_profile`、`binding_code`、`ai_call_log`
- 测试：JUnit 5 + MockMvc + Testcontainers（mysql:8.0 镜像，**本机需安装 Docker**）；纯逻辑类（如 JwtService）用单元测试
- 表结构变更一律走 Flyway 迁移脚本，禁止手动改库
- 部署资源约束：JVM `-Xmx768m`，不上 Redis，日志 logback 滚动文件
- 每个任务以 commit 结束，commit 前测试必须全绿：`cd server && mvn -q test`

---

### Task 1: 工程骨架 + 统一响应 + 健康检查

**Files:**
- Create: `server/pom.xml`
- Create: `server/src/main/java/com/linklife/LinkLifeApplication.java`
- Create: `server/src/main/java/com/linklife/common/web/Result.java`
- Create: `server/src/main/java/com/linklife/common/web/HealthController.java`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/main/resources/logback-spring.xml`
- Test: `server/src/test/java/com/linklife/HealthControllerTest.java`

**Interfaces:**
- Consumes: 无（首个任务）
- Produces: `Result<T>`（`Result.ok(data)` / `Result.ok()` / `Result.error(code, msg)`），后续所有 Controller 返回值统一用它

- [ ] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>
    <groupId>com.linklife</groupId>
    <artifactId>link-life-server</artifactId>
    <version>0.1.0</version>
    <name>link-life-server</name>
    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <jjwt.version>0.12.6</jjwt.version>
        <spring-ai.version>1.0.0</spring-ai.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建启动类、Result、HealthController、application.yml、logback-spring.xml**

`LinkLifeApplication.java`:

```java
package com.linklife;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.linklife.**.mapper")
public class LinkLifeApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinkLifeApplication.class, args);
    }
}
```

`Result.java`:

```java
package com.linklife.common.web;

public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }
    public static Result<Void> ok() {
        return ok(null);
    }
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
```

`HealthController.java`:

```java
package com.linklife.common.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of("status", "UP"));
    }
}
```

`application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/linklife?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USER:linklife}
    password: ${DB_PASSWORD:linklife}
  flyway:
    enabled: true
    locations: classpath:db/migration

link:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-key-must-be-at-least-32-bytes!}
    access-ttl-hours: 168
    refresh-ttl-days: 30
  wx:
    appid: ${WX_APPID:}
    secret: ${WX_SECRET:}
    api-base: ${WX_API_BASE:https://api.weixin.qq.com}

logging:
  file:
    name: logs/link-life.log
```

`logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/link-life.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/link-life.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>20MB</maxFileSize>
            <maxHistory>14</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

- [ ] **Step 3: 编写失败测试**

`HealthControllerTest.java`:

```java
package com.linklife;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
```

注意：此时还没有 Flyway/数据库配置生效，`spring.flyway.enabled=true` 会因连不上数据库而失败。将 `src/test/resources/application-test.yml` 暂时禁用 flyway 和 datasource 自动配置（Task 3 引入 Testcontainers 后移除）：

`server/src/test/resources/application.yml`:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
  flyway:
    enabled: false
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && mvn -q test`
Expected: PASS（1 个测试）。这是骨架验证测试，允许先通过（无 TDD 红灯，因其为冒烟性质）。

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "feat: project skeleton with unified Result and health endpoint"
```

---

### Task 2: 全局异常处理与错误码

**Files:**
- Create: `server/src/main/java/com/linklife/common/exception/ErrorCode.java`
- Create: `server/src/main/java/com/linklife/common/exception/BusinessException.java`
- Create: `server/src/main/java/com/linklife/common/exception/GlobalExceptionHandler.java`
- Test: `server/src/test/java/com/linklife/common/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `Result<T>`（Task 1）
- Produces: `ErrorCode` 枚举（后续任务的错误统一在此追加）、`BusinessException(ErrorCode)` / `BusinessException(ErrorCode, String detail)`；参数校验失败返回 `{"code":400,...}`，业务错误返回对应枚举 code，未捕获异常返回 `{"code":500}`

- [ ] **Step 1: 编写失败测试**

```java
package com.linklife.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linklife.common.web.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowController {
        @GetMapping("/test/biz-error")
        public Result<Void> biz() {
            throw new BusinessException(ErrorCode.CIRCLE_NOT_FOUND);
        }
        @GetMapping("/test/raw-error")
        public Result<Void> raw() {
            throw new IllegalStateException("boom");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessErrorMapped() throws Exception {
        mockMvc.perform(get("/test/biz-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("圈子不存在"));
    }

    @Test
    void unexpectedErrorMapped() throws Exception {
        mockMvc.perform(get("/test/raw-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && mvn -q test -Dtest=GlobalExceptionHandlerTest`
Expected: 编译失败（`ErrorCode`、`BusinessException` 不存在）

- [ ] **Step 3: 实现**

`ErrorCode.java`:

```java
package com.linklife.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    CIRCLE_NOT_FOUND(1001, "圈子不存在", HttpStatus.BAD_REQUEST),
    NOT_CIRCLE_MEMBER(1002, "你不是该圈子成员", HttpStatus.FORBIDDEN),
    INVITE_CODE_INVALID(1003, "邀请码无效", HttpStatus.BAD_REQUEST),
    ALREADY_MEMBER(1004, "已是圈子成员", HttpStatus.BAD_REQUEST),
    BINDING_CODE_INVALID(1005, "绑定码无效或已过期", HttpStatus.BAD_REQUEST),
    WX_LOGIN_FAILED(2001, "微信登录失败", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(2002, "登录状态无效", HttpStatus.UNAUTHORIZED);

    public final int code;
    public final String message;
    public final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
```

`BusinessException.java`:

```java
package com.linklife.common.exception;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

`GlobalExceptionHandler.java`:

```java
package com.linklife.common.exception;

import com.linklife.common.web.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus
    public Result<Void> handleBusiness(BusinessException e) {
        return new Result<>(e.getErrorCode().code, e.getMessage(), null) != null
                ? Result.error(e.getErrorCode().code, e.getMessage())
                : null;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst().orElse("参数错误");
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return Result.error(500, "服务器内部错误");
    }
}
```

注意 `handleBusiness` 的 `@ResponseStatus` 需要按异常携带的 httpStatus 动态设置，用如下写法替换上面的 `handleBusiness`（`@ResponseStatus` 注解是静态的，改用 `ResponseEntity`）：

```java
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().httpStatus)
                .body(Result.error(e.getErrorCode().code, e.getMessage()));
    }
```

（同时导入 `org.springframework.http.ResponseEntity`，`handleBusiness` 方法体以这个版本为准。）

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && mvn -q test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS（2 个测试）

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "feat: global exception handling with error codes"
```

---

### Task 3: 数据库迁移 + MyBatis-Plus 实体与 Mapper

**Files:**
- Create: `server/src/main/resources/db/migration/V1__init.sql`
- Create: `server/src/main/java/com/linklife/user/entity/User.java`
- Create: `server/src/main/java/com/linklife/user/entity/UserProfile.java`
- Create: `server/src/main/java/com/linklife/user/mapper/UserMapper.java`
- Create: `server/src/main/java/com/linklife/user/mapper/UserProfileMapper.java`
- Create: `server/src/main/java/com/linklife/circle/entity/Circle.java`
- Create: `server/src/main/java/com/linklife/circle/entity/CircleMember.java`
- Create: `server/src/main/java/com/linklife/circle/mapper/CircleMapper.java`
- Create: `server/src/main/java/com/linklife/circle/mapper/CircleMemberMapper.java`
- Create: `server/src/main/java/com/linklife/auth/entity/BindingCode.java`
- Create: `server/src/main/java/com/linklife/auth/mapper/BindingCodeMapper.java`
- Create: `server/src/main/java/com/linklife/ai/entity/AiCallLog.java`
- Create: `server/src/main/java/com/linklife/ai/mapper/AiCallLogMapper.java`
- Create: `server/src/test/java/com/linklife/IntegrationTestBase.java`
- Modify: Delete `server/src/test/resources/application.yml`（Task 1 的临时排除配置，改由 Testcontainers 提供）

**Interfaces:**
- Consumes: Task 1 的 MyBatis-Plus 依赖与 `@MapperScan`
- Produces: 六张表的实体类与 Mapper（后续任务依赖）：`UserMapper.selectByOpenid(String)`、`BindingCodeMapper.selectByCode(String)` 等；`IntegrationTestBase` 作为所有集成测试的基类

- [ ] **Step 1: 编写迁移脚本 V1__init.sql**

```sql
CREATE TABLE `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `openid` VARCHAR(64) NOT NULL,
    `unionid` VARCHAR(64) NULL,
    `nickname` VARCHAR(64) NULL,
    `avatar` VARCHAR(512) NULL,
    `phone` VARCHAR(20) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `uk_unionid` (`unionid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_profile` (
    `user_id` BIGINT PRIMARY KEY,
    `taste_prefs` JSON NULL,
    `ai_memory` TEXT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `circle` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL,
    `owner_id` BIGINT NOT NULL,
    `invite_code` VARCHAR(16) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `circle_member` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `circle_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `role` VARCHAR(16) NOT NULL COMMENT 'OWNER / MEMBER',
    `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_circle_user` (`circle_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `binding_code` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` CHAR(6) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `used_at` DATETIME NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ai_call_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NULL,
    `scene` VARCHAR(32) NOT NULL COMMENT '调用场景：recipe_generate / taste_feedback / photo_analysis 等',
    `provider` VARCHAR(32) NOT NULL,
    `model` VARCHAR(64) NOT NULL,
    `prompt_tokens` INT NULL,
    `completion_tokens` INT NULL,
    `ok` TINYINT(1) NOT NULL,
    `error_msg` VARCHAR(512) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 创建实体与 Mapper（MyBatis-Plus 风格，驼峰自动映射下划线）**

`User.java`:

```java
package com.linklife.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("`user`")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatar;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`UserProfile.java`:

```java
package com.linklife.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_profile")
public class UserProfile {
    @TableId
    private Long userId;
    private String tastePrefs;
    private String aiMemory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`Circle.java`:

```java
package com.linklife.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("circle")
public class Circle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long ownerId;
    private String inviteCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`CircleMember.java`:

```java
package com.linklife.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("circle_member")
public class CircleMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long circleId;
    private Long userId;
    private String role;
    private LocalDateTime joinTime;
}
```

`BindingCode.java`:

```java
package com.linklife.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("binding_code")
public class BindingCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long userId;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
```

`AiCallLog.java`:

```java
package com.linklife.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_call_log")
public class AiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String scene;
    private String provider;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Boolean ok;
    private String errorMsg;
    private LocalDateTime createdAt;
}
```

Mapper（五个文件，同构）:

```java
package com.linklife.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.user.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM `user` WHERE openid = #{openid}")
    User selectByOpenid(@Param("openid") String openid);
}
```

```java
package com.linklife.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.user.entity.UserProfile;

public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
```

```java
package com.linklife.circle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.circle.entity.Circle;

public interface CircleMapper extends BaseMapper<Circle> {
}
```

```java
package com.linklife.circle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.circle.entity.CircleMember;

public interface CircleMemberMapper extends BaseMapper<CircleMember> {
}
```

```java
package com.linklife.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.auth.entity.BindingCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BindingCodeMapper extends BaseMapper<BindingCode> {
    @Select("SELECT * FROM binding_code WHERE code = #{code} AND used_at IS NULL ORDER BY id DESC LIMIT 1")
    BindingCode selectByCode(@Param("code") String code);
}
```

```java
package com.linklife.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.ai.entity.AiCallLog;

public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
}
```

- [ ] **Step 3: 创建集成测试基类**

`IntegrationTestBase.java`:

```java
package com.linklife;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("linklife")
            .withUsername("linklife")
            .withPassword("linklife");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
```

并将 Task 1 的 `HealthControllerTest` 改为继承 `IntegrationTestBase`（删除其自身的 `@SpringBootTest`、`@AutoConfigureMockMvc` 注解），然后删除 `server/src/test/resources/application.yml`。

- [ ] **Step 4: 编写失败测试验证迁移与 Mapper**

`server/src/test/java/com/linklife/user/UserMapperTest.java`:

```java
package com.linklife.user;

import com.linklife.IntegrationTestBase;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserMapperTest extends IntegrationTestBase {
    @Autowired
    private UserMapper userMapper;

    @Test
    void insertAndSelectByOpenid() {
        User u = new User();
        u.setOpenid("openid-abc");
        u.setNickname("小明");
        userMapper.insert(u);

        User found = userMapper.selectByOpenid("openid-abc");
        assertNotNull(found);
        assertEquals("小明", found.getNickname());
    }
}
```

- [ ] **Step 5: 运行测试确认通过（Flyway 在容器库上执行 V1）**

Run: `cd server && mvn -q test`
Expected: PASS（全部测试，含 HealthControllerTest 与 UserMapperTest）

- [ ] **Step 6: Commit**

```bash
git add server
git commit -m "feat: database schema migrations, entities and mappers"
```

---

### Task 4: JWT 服务

**Files:**
- Create: `server/src/main/java/com/linklife/auth/jwt/JwtService.java`
- Create: `server/src/main/java/com/linklife/auth/jwt/TokenInfo.java`
- Test: `server/src/test/java/com/linklife/auth/jwt/JwtServiceTest.java`

**Interfaces:**
- Consumes: `application.yml` 中 `link.jwt.secret` / `access-ttl-hours` / `refresh-ttl-days`
- Produces: `String JwtService.generateAccessToken(long userId)`、`String JwtService.generateRefreshToken(long userId)`、`TokenInfo JwtService.parse(String token)`（`record TokenInfo(long userId, String type)`，type 为 `"access"` / `"refresh"`；无效/过期 token 抛 `io.jsonwebtoken.JwtException`）

- [ ] **Step 1: 编写失败测试**

```java
package com.linklife.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-with-at-least-32-bytes!!", 168, 30);

    @Test
    void accessTokenRoundTrip() {
        String token = jwtService.generateAccessToken(42L);
        TokenInfo info = jwtService.parse(token);
        assertEquals(42L, info.userId());
        assertEquals("access", info.type());
    }

    @Test
    void refreshTokenHasRefreshType() {
        String token = jwtService.generateRefreshToken(42L);
        TokenInfo info = jwtService.parse(token);
        assertEquals("refresh", info.type());
    }

    @Test
    void invalidTokenRejected() {
        assertThrows(JwtException.class, () -> jwtService.parse("not-a-token"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && mvn -q test -Dtest=JwtServiceTest`
Expected: 编译失败（`JwtService` 不存在）

- [ ] **Step 3: 实现**

```java
package com.linklife.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${link.jwt.secret}") String secret,
                      @Value("${link.jwt.access-ttl-hours}") long accessTtlHours,
                      @Value("${link.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTtl = Duration.ofHours(accessTtlHours);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    public String generateAccessToken(long userId) {
        return build(userId, "access", accessTtl);
    }

    public String generateRefreshToken(long userId) {
        return build(userId, "refresh", refreshTtl);
    }

    public TokenInfo parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return new TokenInfo(Long.parseLong(claims.getSubject()), claims.get("type", String.class));
    }

    private String build(long userId, String type, Duration ttl) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }
}
```

```java
package com.linklife.auth.jwt;

public record TokenInfo(long userId, String type) {
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && mvn -q test -Dtest=JwtServiceTest`
Expected: PASS（3 个测试）

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "feat: jwt service for access and refresh tokens"
```

---

### Task 5: 微信登录与鉴权 API

**Files:**
- Create: `server/src/main/java/com/linklife/auth/wechat/WeChatProperties.java`
- Create: `server/src/main/java/com/linklife/auth/wechat/WeChatClient.java`
- Create: `server/src/main/java/com/linklife/auth/wechat/WxSession.java`
- Create: `server/src/main/java/com/linklife/auth/AuthService.java`
- Create: `server/src/main/java/com/linklife/auth/AuthController.java`
- Create: `server/src/main/java/com/linklife/auth/dto/AuthTokens.java`
- Create: `server/src/main/java/com/linklife/auth/dto/LoginRequest.java`
- Create: `server/src/main/java/com/linklife/auth/dto/RefreshRequest.java`
- Create: `server/src/main/java/com/linklife/auth/dto/UserVO.java`
- Test: `server/src/test/java/com/linklife/auth/AuthControllerTest.java`

**Interfaces:**
- Consumes: `UserMapper`（Task 3）、`JwtService`（Task 4）、`ErrorCode.WX_LOGIN_FAILED`
- Produces: 
  - `POST /api/auth/wx-login` body `{"code":"..."}` → `{"code":0,"data":{"accessToken":"...","refreshToken":"...","user":{"id":1,"nickname":"...","avatar":null}}}`
  - `POST /api/auth/refresh` body `{"refreshToken":"..."}` → 同上结构
  - `WxSession record WxSession(String openid, String unionid)`
  - `UserVO record UserVO(Long id, String nickname, String avatar)`（后续 `/api/me`、圈子成员列表复用）

- [ ] **Step 1: 编写失败测试**

```java
package com.linklife.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthControllerTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String wxLoginBody() {
        return "{\"code\":\"js-code-1\"}";
    }

    @Test
    void wxLoginCreatesUserAndReturnsTokens() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-new-1", "unionid-1"));

        mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wxLoginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").isNumber());
    }

    @Test
    void refreshReturnsNewTokenPair() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-new-2", "unionid-2"));

        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wxLoginBody()))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.refreshToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void accessTokenCannotRefresh() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-new-3", "unionid-3"));

        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wxLoginBody()))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2002));
    }
}
```

注意：如果 Spring Boot 3.4+ 中 `@MockBean` 已弃用，改用 `@MockitoBean`（`org.springframework.test.context.bean.override.mockito.MockitoBean`）。本项目 Boot 3.3.4 用 `@MockBean`。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && mvn -q test -Dtest=AuthControllerTest`
Expected: 编译失败（`WeChatClient`、`AuthService` 等不存在）

- [ ] **Step 3: 实现**

`WeChatProperties.java`:

```java
package com.linklife.auth.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "link.wx")
public class WeChatProperties {
    private String appid;
    private String secret;
    private String apiBase = "https://api.weixin.qq.com";
}
```

`WxSession.java`:

```java
package com.linklife.auth.wechat;

public record WxSession(String openid, String unionid) {
}
```

`WeChatClient.java`:

```java
package com.linklife.auth.wechat;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class WeChatClient {

    private final WeChatProperties props;
    private final RestClient restClient;

    public WeChatClient(WeChatProperties props) {
        this.props = props;
        this.restClient = RestClient.create();
    }

    public WxSession code2Session(String jsCode) {
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getApiBase())
                .path("/sns/jscode2session")
                .queryParam("appid", props.getAppid())
                .queryParam("secret", props.getSecret())
                .queryParam("js_code", jsCode)
                .queryParam("grant_type", "authorization_code")
                .build().toUri();
        WxSessionResponse resp = restClient.get().uri(uri)
                .retrieve().body(WxSessionResponse.class);
        if (resp == null || resp.errcode() != null && resp.errcode() != 0) {
            log.warn("wx code2session failed: {}", resp);
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
        return new WxSession(resp.openid(), resp.unionid());
    }

    record WxSessionResponse(String openid, String unionid, Integer errcode, String errmsg) {
    }
}
```

`AuthService.java`:

```java
package com.linklife.auth;

import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.dto.UserVO;
import com.linklife.auth.jwt.JwtService;
import com.linklife.auth.jwt.TokenInfo;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final WeChatClient weChatClient;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    @Transactional
    public AuthTokens wxLogin(String jsCode) {
        WxSession session = weChatClient.code2Session(jsCode);
        User user = userMapper.selectByOpenid(session.openid());
        if (user == null) {
            user = new User();
            user.setOpenid(session.openid());
            user.setUnionid(session.unionid());
            userMapper.insert(user);
        }
        return buildTokens(user);
    }

    public AuthTokens refresh(String refreshToken) {
        TokenInfo info = jwtService.parse(refreshToken);
        if (!"refresh".equals(info.type())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        User user = userMapper.selectById(info.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return buildTokens(user);
    }

    public UserVO toVO(User user) {
        return new UserVO(user.getId(), user.getNickname(), user.getAvatar());
    }

    private AuthTokens buildTokens(User user) {
        return new AuthTokens(
                jwtService.generateAccessToken(user.getId()),
                jwtService.generateRefreshToken(user.getId()),
                toVO(user));
    }
}
```

`AuthTokens.java`:

```java
package com.linklife.auth.dto;

public record AuthTokens(String accessToken, String refreshToken, UserVO user) {
}
```

`UserVO.java`:

```java
package com.linklife.auth.dto;

public record UserVO(Long id, String nickname, String avatar) {
}
```

`LoginRequest.java`:

```java
package com.linklife.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String code) {
}
```

`RefreshRequest.java`:

```java
package com.linklife.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {
}
```

`AuthController.java`:

```java
package com.linklife.auth;

import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.dto.LoginRequest;
import com.linklife.auth.dto.RefreshRequest;
import com.linklife.common.web.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/wx-login")
    public Result<AuthTokens> wxLogin(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.wxLogin(request.code()));
    }

    @PostMapping("/refresh")
    public Result<AuthTokens> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && mvn -q test`
Expected: PASS（全部测试）

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "feat: wechat login and token refresh api"
```

---

### Task 6: JWT 鉴权过滤器 + 当前用户上下文 + /api/me

**Files:**
- Create: `server/src/main/java/com/linklife/common/security/UserContext.java`
- Create: `server/src/main/java/com/linklife/common/security/JwtAuthFilter.java`
- Create: `server/src/main/java/com/linklife/user/MeController.java`
- Create: `server/src/main/java/com/linklife/user/dto/UpdateMeRequest.java`
- Test: `server/src/test/java/com/linklife/user/MeControllerTest.java`

**Interfaces:**
- Consumes: `JwtService`（Task 4）、`AuthService.toVO`（Task 5）、`UserMapper`（Task 3）
- Produces: `UserContext.requireUserId()`（返回当前登录 userId，未登录抛 `ErrorCode.INVALID_TOKEN`）；`JwtAuthFilter` 注册为 Servlet 过滤器，公开路径 `/api/health`、`/api/auth/**`；`GET /api/me` → `UserVO`、`PUT /api/me` body `{"nickname":"...","avatar":"..."}` → `UserVO`

- [ ] **Step 1: 编写失败测试**

```java
package com.linklife.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class MeControllerTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String loginAndGetToken() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-me-1", "unionid-me-1"));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        String token = loginAndGetToken();
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void updateMeChangesNickname() throws Exception {
        String token = loginAndGetToken();
        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"家里的主厨\",\"avatar\":\"https://x/a.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("家里的主厨"));
    }
}
```

（测试中补充 import：`static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;`）

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && mvn -q test -Dtest=MeControllerTest`
Expected: FAIL（`/api/me` 404，或未鉴权未 401）

- [ ] **Step 3: 实现**

`UserContext.java`:

```java
package com.linklife.common.security;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;

public final class UserContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        CURRENT.set(userId);
    }

    public static long requireUserId() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
```

`JwtAuthFilter.java`:

```java
package com.linklife.common.security;

import com.linklife.auth.jwt.JwtService;
import com.linklife.auth.jwt.TokenInfo;
import com.linklife.common.web.Result;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS =
            List.of("/api/health", "/api/auth/");

    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/health") || path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response);
            return;
        }
        try {
            TokenInfo info = jwtService.parse(header.substring(7));
            if (!"access".equals(info.type())) {
                reject(response);
                return;
            }
            UserContext.set(info.userId());
            chain.doFilter(request, response);
        } catch (JwtException e) {
            reject(response);
        } finally {
            UserContext.clear();
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(Result.error(2002, "登录状态无效")));
    }
}
```

`MeController.java`:

```java
package com.linklife.user;

import com.linklife.auth.AuthService;
import com.linklife.auth.dto.UserVO;
import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.user.dto.UpdateMeRequest;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserMapper userMapper;
    private final AuthService authService;

    @GetMapping
    public Result<UserVO> me() {
        User user = userMapper.selectById(UserContext.requireUserId());
        return Result.ok(authService.toVO(user));
    }

    @PutMapping
    public Result<UserVO> updateMe(@RequestBody UpdateMeRequest request) {
        User user = userMapper.selectById(UserContext.requireUserId());
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        userMapper.updateById(user);
        return Result.ok(authService.toVO(user));
    }
}
```

`UpdateMeRequest.java`:

```java
package com.linklife.user.dto;

public record UpdateMeRequest(String nickname, String avatar) {
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && mvn -q test`
Expected: PASS（全部测试）

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "feat: jwt auth filter, user context and /api/me endpoints"
```

---

### Task 7: 圈子模块

**Files:**
- Create: `server/src/main/java/com/linklife/circle/CircleService.java`
- Create: `server/src/main/java/com/linklife/circle/CircleController.java`
- Create: `server/src/main/java/com/linklife/circle/dto/CreateCircleRequest.java`
- Create: `server/src/main/java/com/linklife/circle/dto/JoinCircleRequest.java`
- Create: `server/src/main/java/com/linklife/circle/dto/CircleVO.java`
- Create: `server/src/main/java/com/linklife/circle/dto/MemberVO.java`
- Test: `server/src/test/java/com/linklife/circle/CircleControllerTest.java`

**Interfaces:**
- Consumes: `CircleMapper` / `CircleMemberMapper`（Task 3）、`UserContext.requireUserId()`（Task 6）、`ErrorCode.CIRCLE_NOT_FOUND / NOT_CIRCLE_MEMBER / INVITE_CODE_INVALID / ALREADY_MEMBER`、`UserVO`（Task 5）
- Produces:
  - `POST /api/circles` body `{"name":"我们家"}` → `CircleVO`（自动生成 8 位邀请码，创建者为 OWNER）
  - `GET /api/circles` → 我加入的圈子列表 `List<CircleVO>`（含 `inviteCode`，仅 OWNER 可见？——P0 简化：成员都可见，方便互相拉人）
  - `POST /api/circles/join` body `{"inviteCode":"..."}` → `CircleVO`
  - `GET /api/circles/{id}/members` → `List<MemberVO>`（`record MemberVO(Long userId, String nickname, String avatar, String role)`）
  - `record CircleVO(Long id, String name, String inviteCode, Long ownerId)`

- [ ] **Step 1: 编写失败测试**

```java
package com.linklife.circle;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class CircleControllerTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void createCircleThenOwnerIsMember() throws Exception {
        String token = login("circle-owner-1");
        mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"我们家\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("我们家"))
                .andExpect(jsonPath("$.data.inviteCode").isNotEmpty());

        mockMvc.perform(get("/api/circles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("我们家"));
    }

    @Test
    void joinWithInviteCode() throws Exception {
        String ownerToken = login("circle-owner-2");
        MvcResult created = mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"朋友圈\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String inviteCode = JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.inviteCode");

        String memberToken = login("circle-member-2");
        mockMvc.perform(post("/api/circles/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("朋友圈"));

        // 重复加入报错
        mockMvc.perform(post("/api/circles/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));

        // 成员列表两人
        mockMvc.perform(get("/api/circles/1/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void nonMemberCannotSeeMembers() throws Exception {
        String ownerToken = login("circle-owner-3");
        mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"圈子3\"}"))
                .andExpect(status().isOk());

        String outsiderToken = login("circle-outsider-3");
        mockMvc.perform(get("/api/circles/2/members")
                .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }
}
```

注意：members 测试里写死了 circle id 1、2 依赖执行顺序，不可靠。改为从 create 响应读出 id 后拼 URL（第三个测试同样创建后读 `$.data.id`）。实现测试时以动态读取的 id 为准。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && mvn -q test -Dtest=CircleControllerTest`
Expected: 编译失败

- [ ] **Step 3: 实现**

`CreateCircleRequest.java`:

```java
package com.linklife.circle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCircleRequest(@NotBlank @Size(max = 64) String name) {
}
```

`JoinCircleRequest.java`:

```java
package com.linklife.circle.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinCircleRequest(@NotBlank String inviteCode) {
}
```

`CircleVO.java`:

```java
package com.linklife.circle.dto;

public record CircleVO(Long id, String name, String inviteCode, Long ownerId) {
}
```

`MemberVO.java`:

```java
package com.linklife.circle.dto;

public record MemberVO(Long userId, String nickname, String avatar, String role) {
}
```

`CircleService.java`:

```java
package com.linklife.circle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.circle.dto.CircleVO;
import com.linklife.circle.dto.MemberVO;
import com.linklife.circle.entity.Circle;
import com.linklife.circle.entity.CircleMember;
import com.linklife.circle.mapper.CircleMapper;
import com.linklife.circle.mapper.CircleMemberMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CircleService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CircleMapper circleMapper;
    private final CircleMemberMapper circleMemberMapper;
    private final UserMapper userMapper;

    @Transactional
    public CircleVO create(long userId, String name) {
        Circle circle = new Circle();
        circle.setName(name);
        circle.setOwnerId(userId);
        circle.setInviteCode(generateCode());
        circleMapper.insert(circle);

        CircleMember member = new CircleMember();
        member.setCircleId(circle.getId());
        member.setUserId(userId);
        member.setRole("OWNER");
        circleMemberMapper.insert(member);
        return toVO(circle);
    }

    @Transactional
    public CircleVO join(long userId, String inviteCode) {
        Circle circle = circleMapper.selectOne(
                new LambdaQueryWrapper<Circle>().eq(Circle::getInviteCode, inviteCode));
        if (circle == null) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        Long count = circleMemberMapper.selectCount(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circle.getId())
                .eq(CircleMember::getUserId, userId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.ALREADY_MEMBER);
        }
        CircleMember member = new CircleMember();
        member.setCircleId(circle.getId());
        member.setUserId(userId);
        member.setRole("MEMBER");
        circleMemberMapper.insert(member);
        return toVO(circle);
    }

    public List<CircleVO> listMyCircles(long userId) {
        List<Long> circleIds = circleMemberMapper.selectList(
                        new LambdaQueryWrapper<CircleMember>().eq(CircleMember::getUserId, userId))
                .stream().map(CircleMember::getCircleId).toList();
        if (circleIds.isEmpty()) {
            return List.of();
        }
        return circleMapper.selectBatchIds(circleIds).stream().map(this::toVO).toList();
    }

    public List<MemberVO> listMembers(long userId, long circleId) {
        requireMembership(userId, circleId);
        return circleMemberMapper.selectList(
                        new LambdaQueryWrapper<CircleMember>().eq(CircleMember::getCircleId, circleId))
                .stream().map(m -> {
                    User u = userMapper.selectById(m.getUserId());
                    return new MemberVO(m.getUserId(), u.getNickname(), u.getAvatar(), m.getRole());
                }).toList();
    }

    public void requireMembership(long userId, long circleId) {
        if (circleMapper.selectById(circleId) == null) {
            throw new BusinessException(ErrorCode.CIRCLE_NOT_FOUND);
        }
        Long count = circleMemberMapper.selectCount(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId)
                .eq(CircleMember::getUserId, userId));
        if (count == 0) {
            throw new BusinessException(ErrorCode.NOT_CIRCLE_MEMBER);
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private CircleVO toVO(Circle circle) {
        return new CircleVO(circle.getId(), circle.getName(), circle.getInviteCode(), circle.getOwnerId());
    }
}
```

`CircleController.java`:

```java
package com.linklife.circle;

import com.linklife.circle.dto.CircleVO;
import com.linklife.circle.dto.CreateCircleRequest;
import com.linklife.circle.dto.JoinCircleRequest;
import com.linklife.circle.dto.MemberVO;
import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/circles")
@RequiredArgsConstructor
public class CircleController {

    private final CircleService circleService;

    @PostMapping
    public Result<CircleVO> create(@Valid @RequestBody CreateCircleRequest request) {
        return Result.ok(circleService.create(UserContext.requireUserId(), request.name()));
    }

    @PostMapping("/join")
    public Result<CircleVO> join(@Valid @RequestBody JoinCircleRequest request) {
        return Result.ok(circleService.join(UserContext.requireUserId(), request.inviteCode()));
    }

    @GetMapping
    public Result<List<CircleVO>> myCircles() {
        return Result.ok(circleService.listMyCircles(UserContext.requireUserId()));
    }

    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> members(@PathVariable long id) {
        return Result.ok(circleService.listMembers(UserContext.requireUserId(), id));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && mvn -q test`
Expected: PASS（全部测试）

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "feat: circle module with invite code join and member list"
```

---

### Task 8: 绑定码（Web 端账号绑定）

**Files:**
- Create: `server/src/main/java/com/linklife/auth/BindingCodeService.java`
- Modify: `server/src/main/java/com/linklife/auth/AuthController.java`（追加两个端点）
- Test: `server/src/test/java/com/linklife/auth/BindingCodeTest.java`

**Interfaces:**
- Consumes: `BindingCodeMapper.selectByCode`（Task 3）、`UserContext.requireUserId()`（Task 6）、`JwtService`（Task 4）、`ErrorCode.BINDING_CODE_INVALID`
- Produces:
  - `POST /api/auth/binding-code`（需登录，小程序里调）→ `{"code":"123456","expiresAt":"2026-09-16T15:30:00"}`
  - `POST /api/auth/bind` body `{"code":"123456"}`（免登录，Web 端调）→ 与 wx-login 相同的 `AuthTokens` 结构
  - 绑定码 6 位数字、10 分钟有效、一次性使用

- [ ] **Step 1: 编写失败测试**

```java
package com.linklife.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class BindingCodeTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, null));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void bindConsumesBindingCodeAndReturnsTokens() throws Exception {
        String token = login("bind-user-1");
        MvcResult codeResult = mockMvc.perform(post("/api/auth/binding-code")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").isNotEmpty())
                .andReturn();
        String code = JsonPath.read(codeResult.getResponse().getContentAsString(), "$.data.code");

        mockMvc.perform(post("/api/auth/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").isNumber());

        // 同一码第二次使用失败
        mockMvc.perform(post("/api/auth/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1005));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && mvn -q test -Dtest=BindingCodeTest`
Expected: FAIL（端点不存在）

- [ ] **Step 3: 实现**

`BindingCodeService.java`:

```java
package com.linklife.auth;

import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.dto.BindingCodeVO;
import com.linklife.auth.entity.BindingCode;
import com.linklife.auth.jwt.JwtService;
import com.linklife.auth.mapper.BindingCodeMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BindingCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TTL_MINUTES = 10;

    private final BindingCodeMapper bindingCodeMapper;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final AuthService authService;

    @Transactional
    public BindingCodeVO create(long userId) {
        BindingCode bc = new BindingCode();
        bc.setCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
        bc.setUserId(userId);
        bc.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        bindingCodeMapper.insert(bc);
        return new BindingCodeVO(bc.getCode(), bc.getExpiresAt());
    }

    @Transactional
    public AuthTokens bind(String code) {
        BindingCode bc = bindingCodeMapper.selectByCode(code);
        if (bc == null || bc.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BINDING_CODE_INVALID);
        }
        bc.setUsedAt(LocalDateTime.now());
        bindingCodeMapper.updateById(bc);

        User user = userMapper.selectById(bc.getUserId());
        return new AuthTokens(
                jwtService.generateAccessToken(user.getId()),
                jwtService.generateRefreshToken(user.getId()),
                authService.toVO(user));
    }
}
```

`BindingCodeVO.java`（新增 `server/src/main/java/com/linklife/auth/dto/BindingCodeVO.java`）:

```java
package com.linklife.auth.dto;

import java.time.LocalDateTime;

public record BindingCodeVO(String code, LocalDateTime expiresAt) {
}
```

`AuthController.java` 追加（构造器注入 `BindingCodeService`，`@RequiredArgsConstructor` 自动带上）:

```java
    @PostMapping("/binding-code")
    public Result<BindingCodeVO> bindingCode() {
        return Result.ok(bindingCodeService.create(UserContext.requireUserId()));
    }

    @PostMapping("/bind")
    public Result<AuthTokens> bind(@Valid @RequestBody BindRequest request) {
        return Result.ok(bindingCodeService.bind(request.code()));
    }
```

`BindRequest.java`（新增 `server/src/main/java/com/linklife/auth/dto/BindRequest.java`）:

```java
package com.linklife.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record BindRequest(@NotBlank String code) {
}
```

注意：`/api/auth/binding-code` 在 `/api/auth/` 公开前缀下，但需要登录。修改 `JwtAuthFilter.shouldNotFilter`，把 `/api/auth/binding-code` 排除出公开路径：

```java
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/health")) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return !path.equals("/api/auth/binding-code");
        }
        return false;
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && mvn -q test`
Expected: PASS（全部测试）

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "feat: binding code for web account linking"
```

---

### Task 9: AI 网关骨架（Spring AI）

**Files:**
- Modify: `server/pom.xml`（新增 spring-ai deepseek starter）
- Create: `server/src/main/java/com/linklife/ai/gateway/AiGatewayService.java`
- Create: `server/src/main/java/com/linklife/ai/gateway/AiCallLogger.java`
- Modify: `server/src/main/resources/application.yml`（新增 spring.ai 配置段）
- Test: `server/src/test/java/com/linklife/ai/AiGatewayServiceTest.java`

**Interfaces:**
- Consumes: `AiCallLogMapper`（Task 3）
- Produces: `String AiGatewayService.call(String scene, String prompt)`——P0 仅骨架：业务后续统一从该方法/该类扩展流式、多模态；所有 AI 调用写 `ai_call_log`（scene、provider、model、ok、耗时）。测试中不真实调用外部 API（用 mock ChatClient 验证日志逻辑）

- [ ] **Step 1: pom.xml 增加 spring-ai starter（dependencies 节点内追加）**

```xml
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-deepseek</artifactId>
        </dependency>
```

`application.yml` 追加（api-key 留空时服务不启用真实调用，骨架阶段不阻塞启动）:

```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:}
      chat:
        options:
          model: ${DEEPSEEK_MODEL:deepseek-chat}
```

- [ ] **Step 2: 编写失败测试**

```java
package com.linklife.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linklife.IntegrationTestBase;
import com.linklife.ai.gateway.AiCallLogger;
import com.linklife.ai.gateway.AiGatewayService;
import com.linklife.ai.mapper.AiCallLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.mock.mockito.MockBean;

class AiGatewayServiceTest extends IntegrationTestBase {

    @TestConfiguration
    static class FakeChatConfig {
        @Bean
        ChatClient.Builder chatClientBuilder() {
            ChatClient.Builder builder = org.mockito.Mockito.mock(ChatClient.Builder.class,
                    org.mockito.Mockito.withSettings()
                            .defaultAnswer(org.mockito.Mockito.RETURNS_DEEP_STUBS));
            // deep-stubs 链式桩：build().prompt().user(...).call().content() 返回固定内容
            org.mockito.Mockito.when(builder.build()
                            .prompt()
                            .user(org.mockito.ArgumentMatchers.anyString())
                            .call()
                            .content())
                    .thenReturn("AI 回复内容");
            return builder;
        }
    }
    }

    @MockBean
    private AiCallLogger aiCallLogger;

    @Autowired
    private AiGatewayService aiGatewayService;

    @Test
    void callReturnsContentAndWritesLog() {
        String answer = aiGatewayService.call("recipe_generate", "生成一份番茄炒蛋菜谱");
        assertEquals("AI 回复内容", answer);
        verify(aiCallLogger).log(any(), org.mockito.ArgumentMatchers.eq("recipe_generate"),
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq(true), any());
    }
}
```

若 deep-stubs 与 ChatClient 链式 API 不兼容（Spring AI 版本差异），允许把 `AiGatewayService` 改为构造注入 `ChatClient.Builder` 并在测试里手动 new（传入手写 mock builder），以能验证日志行为为准。

- [ ] **Step 3: 运行测试确认失败**

Run: `cd server && mvn -q test -Dtest=AiGatewayServiceTest`
Expected: 编译失败（`AiGatewayService` 不存在）

- [ ] **Step 4: 实现**

`AiCallLogger.java`:

```java
package com.linklife.ai.gateway;

import com.linklife.ai.entity.AiCallLog;
import com.linklife.ai.mapper.AiCallLogMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallLogger {

    private final AiCallLogMapper aiCallLogMapper;

    public void log(Long userId, String scene, String provider, String model,
                    boolean ok, String errorMsg) {
        try {
            AiCallLog entry = new AiCallLog();
            entry.setUserId(userId);
            entry.setScene(scene);
            entry.setProvider(provider);
            entry.setModel(model);
            entry.setOk(ok);
            entry.setErrorMsg(errorMsg);
            entry.setCreatedAt(LocalDateTime.now());
            aiCallLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("failed to write ai_call_log", e);
        }
    }
}
```

`AiGatewayService.java`:

```java
package com.linklife.ai.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiGatewayService {

    private final ChatClient chatClient;
    private final AiCallLogger aiCallLogger;
    private final String provider;
    private final String model;

    public AiGatewayService(ChatClient.Builder chatClientBuilder,
                            AiCallLogger aiCallLogger,
                            @Value("${spring.ai.deepseek.api-key:}") String apiKey,
                            @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String model) {
        this.chatClient = chatClientBuilder.build();
        this.aiCallLogger = aiCallLogger;
        this.provider = "deepseek";
        this.model = model;
    }

    public String call(String scene, String prompt) {
        try {
            String content = chatClient.prompt().user(prompt).call().content();
            aiCallLogger.log(null, scene, provider, model, true, null);
            return content;
        } catch (Exception e) {
            log.error("ai call failed, scene={}", scene, e);
            aiCallLogger.log(null, scene, provider, model, false, truncate(e.getMessage()));
            throw e;
        }
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 512 ? msg.substring(0, 512) : msg;
    }
}
```

注意：测试上下文里 api-key 为空时 Spring AI 的自动配置可能因缺少 key 而启动失败；若发生，在 `src/test/resources` 加 `application-test.properties` 或在 `AiGatewayServiceTest` 的 `@DynamicPropertySource` 中设置 `spring.ai.deepseek.api-key=test-key`。优先采用后者（在 `IntegrationTestBase` 的 `datasourceProps` 中一并注册 `registry.add("spring.ai.deepseek.api-key", () -> "test-key");`）。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd server && mvn -q test`
Expected: PASS（全部测试）

- [ ] **Step 6: Commit**

```bash
git add server
git commit -m "feat: ai gateway skeleton on spring ai with call logging"
```

---

### Task 10: Docker Compose 部署与备份脚本

**Files:**
- Create: `server/Dockerfile`
- Create: `deploy/docker-compose.yml`
- Create: `deploy/nginx.conf`
- Create: `deploy/backup.sh`
- Create: `README.md`
- Modify: `.gitignore`（根目录，新建）

**Interfaces:**
- Consumes: Task 1-9 的完整后端
- Produces: `docker compose up -d` 一键启动 nginx + app + mysql；`/api/health` 经 nginx 可访问；`deploy/backup.sh` 每日备份 mysql 与日志

- [ ] **Step 1: 编写 Dockerfile（多阶段构建）**

`server/Dockerfile`:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_OPTS="-Xmx768m -Xms256m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 2: 编写 docker-compose.yml**

`deploy/docker-compose.yml`:

```yaml
services:
  app:
    build: ../server
    restart: unless-stopped
    environment:
      DB_URL: jdbc:mysql://mysql:3306/linklife?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      DB_USER: linklife
      DB_PASSWORD: ${DB_PASSWORD:-linklife-prod}
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}
      WX_APPID: ${WX_APPID:-}
      WX_SECRET: ${WX_SECRET:-}
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-}
    depends_on:
      mysql:
        condition: service_healthy
    expose:
      - "8080"

  mysql:
    image: mysql:8.0
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: linklife
      MYSQL_USER: linklife
      MYSQL_PASSWORD: ${DB_PASSWORD:-linklife-prod}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root-prod}
    command: --innodb-buffer-pool-size=128M --max-connections=50
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-ulinklife", "-p${DB_PASSWORD:-linklife-prod}"]
      interval: 5s
      timeout: 3s
      retries: 20

  nginx:
    image: nginx:1.27-alpine
    restart: unless-stopped
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - ../web-dist:/usr/share/nginx/html:ro
      - ../data/images:/data/images:ro
    depends_on:
      - app

volumes:
  mysql-data:
```

- [ ] **Step 3: 编写 nginx.conf**

`deploy/nginx.conf`:

```nginx
server {
    listen 80;
    server_name _;

    client_max_body_size 10m;

    # Web 前端静态资源（P1 起填充 ../web-dist）
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # API 反代
    location /api/ {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 用户上传图片静态服务
    location /images/ {
        alias /data/images/;
        expires 30d;
    }
}
```

- [ ] **Step 4: 编写备份脚本**

`deploy/backup.sh`:

```bash
#!/usr/bin/env bash
# 每日备份：mysqldump 到 /opt/link-life/backups，保留 14 天
# crontab 示例：0 3 * * * /opt/link-life/deploy/backup.sh >> /opt/link-life/logs/backup.log 2>&1
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/link-life/backups}"
KEEP_DAYS=14
mkdir -p "$BACKUP_DIR"

STAMP=$(date +%Y%m%d-%H%M%S)
docker compose -f /opt/link-life/deploy/docker-compose.yml exec -T mysql \
    mysqldump -ulinklife -p"${DB_PASSWORD:-linklife-prod}" linklife \
    | gzip > "$BACKUP_DIR/linklife-$STAMP.sql.gz"

find "$BACKUP_DIR" -name "linklife-*.sql.gz" -mtime +$KEEP_DAYS -delete
echo "backup done: linklife-$STAMP.sql.gz"
```

- [ ] **Step 5: 编写 .gitignore 与 README**

`.gitignore`:

```
target/
logs/
*.iml
.idea/
.vscode/
.DS_Store
deploy/.env
web-dist/
data/
node_modules/
```

`README.md`:

```markdown
# Link-Life

家庭/朋友间的点单清单 + AI 菜谱平台。设计文档见 `docs/superpowers/specs/`。

## 本地开发（后端）

```bash
cd server
mvn spring-boot:run   # 需本地 MySQL，或用 docker 起一个
```

## 部署（2c4G 云服务器）

1. 安装 Docker 与 Docker Compose 插件
2. 在 `deploy/` 下创建 `.env`：

```
DB_PASSWORD=你的数据库密码
JWT_SECRET=至少32字节的随机字符串
WX_APPID=小程序appid
WX_SECRET=小程序secret
DEEPSEEK_API_KEY=后续AI功能用
```

3. 启动：

```bash
cd deploy && docker compose up -d
curl http://localhost/api/health
```

4. 每日备份：`crontab -e` 添加 `0 3 * * * /opt/link-life/deploy/backup.sh`

## 进度

见 `docs/superpowers/specs/2026-09-16-link-life-design.md` 第 9 节路线图。
```

- [ ] **Step 6: 验证部署配置**

```bash
cd deploy && JWT_SECRET=test-secret-key-at-least-32-bytes-long!! docker compose config -q
docker build -t link-life-server server/
```

Expected: `compose config` 无报错；`docker build` 成功产出镜像（若本机 Docker 不可用，则记录并跳过，部署时在服务器上验证）。

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "feat: docker compose deployment, nginx config and backup script"
```

---

## 收尾任务

- [ ] 全量验证：`cd server && mvn -q test` 全绿
- [ ] 更新 spec 第 9 节路线图：P0 状态改为 ✅
- [ ] 提交：`git add docs && git commit -m "docs: mark P0 complete in roadmap"`
