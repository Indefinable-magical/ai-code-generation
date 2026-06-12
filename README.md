# AI Code Generation

AI 驱动的代码生成平台，支持通过自然语言描述自动生成 HTML、Vue 项目等多种类型的代码。

## 项目架构

```
ai-code-generation/
├── src/                          # Java 后端（Spring Boot 3）
│   └── main/
│       ├── java/com/yu/aicodeGeneration/
│       │   ├── ai/               # AI 模型集成与代码生成服务
│       │   ├── controller/       # RESTful API 接口
│       │   ├── core/             # 核心业务逻辑
│       │   │   ├── builder/      # 项目构建器（如 Vue 项目）
│       │   │   ├── handler/      # SSE 流式响应处理
│       │   │   ├── parser/       # 代码解析器
│       │   │   └── saver/        # 代码文件保存
│       │   ├── model/            # 数据模型（实体、DTO、VO、枚举）
│       │   ├── service/          # 业务服务层
│       │   ├── mapper/           # 数据访问层（MyBatis-Flex）
│       │   ├── config/           # 配置类（Redis、COS、AI 模型等）
│       │   ├── tools/            # AI 工具（文件读写、代码执行等）
│       │   ├── ratelimter/       # 限流组件（基于 Redisson）
│       │   ├── monitor/          # 监控指标收集
│       │   └── guardrail/        # AI 输入/输出安全护栏
│       └── resources/
│           ├── prompt/           # AI 提示词模板
│           └── mapper/           # MyBatis XML 映射文件
├── ai-code-generation-frontend/  # 前端（Vue 3 + TypeScript）
│   ├── src/
│   │   ├── pages/               # 页面组件
│   │   │   ├── app/             # 应用聊天与编辑页面
│   │   │   ├── admin/           # 后台管理页面
│   │   │   └── user/            # 用户登录注册
│   │   ├── components/          # 通用组件
│   │   ├── api/                 # API 接口定义
│   │   └── stores/              # Pinia 状态管理
│   └── package.json
├── sql/                          # 数据库建表脚本
├── grafana/                      # Grafana 监控面板配置
└── prometheus.yml                # Prometheus 监控配置
```

## 技术栈

### 后端
- **Java 21** + **Spring Boot 3**
- **MyBatis-Flex**（ORM 框架）
- **Redis**（会话管理、缓存）
- **MySQL**（持久化存储）
- **LangChain4j**（AI 模型集成）
- **SSE**（Server-Sent Events，流式响应）
- **Redisson**（分布式限流）
- **Prometheus + Grafana**（监控）

### 前端
- **Vue 3** + **TypeScript**
- **Vite**（构建工具）
- **Ant Design Vue 4**（UI 组件库）
- **Pinia**（状态管理）
- **Vue Router**（路由）

### AI 模型
- **DeepSeek Chat**（代码生成）
- **DeepSeek Reasoner**（复杂推理任务）
- **Qwen Turbo**（智能路由分类）
- **DashScope**（图片生成）

## 快速开始

### 前置要求
- JDK 21+
- Node.js 18+
- MySQL 8.0+
- Redis

### 1. 数据库初始化
```sql
-- 执行 SQL 脚本创建数据库和表
source sql/create_table.sql;
```

### 2. 后端启动

```bash
# 配置本地环境
cp src/main/resources/application.yml src/main/resources/application-local.yml
# 编辑 application-local.yml 填入真实的 API Key 和数据库配置

# 启动后端
./mvnw spring-boot:run
```

### 3. 前端启动

```bash
cd ai-code-generation-frontend
npm install
npm run dev
```

### 4. 访问
- 前端页面：`http://localhost:5173`
- 后端 API：`http://localhost:8123/api`

## 主要功能

- **AI 对话生成代码**：通过自然语言描述生成 HTML、Vue 项目
- **多文件项目生成**：一键生成完整的 Vue 项目结构
- **SSE 流式输出**：实时展示 AI 生成过程
- **项目下载**：生成的项目可打包下载
- **聊天历史管理**：保存和管理对话记录
- **图片搜索与生成**：搜索配图或 AI 生成图片
- **管理员后台**：用户管理、应用管理、聊天记录管理

## 配置说明

所有敏感配置（API Key、数据库密码等）请放在 `application-local.yml` 中，该文件已加入 `.gitignore`，不会提交到代码仓库。

主要配置项：

| 配置 | 说明 |
|------|------|
| `langchain4j.open-ai.chat-model.api-key` | DeepSeek API Key |
| `langchain4j.open-ai.reasoning-streaming-chat-model.api-key` | DeepSeek Reasoner API Key |
| `langchain4j.open-ai.routing-chat-model.api-key` | DashScope API Key |
| `cos.client.secretId/secretKey` | 腾讯云 COS 密钥 |
| `pexels.api-key` | Pexels 图片搜索 API Key |
| `dashscope.api-key` | 阿里云 DashScope API Key |