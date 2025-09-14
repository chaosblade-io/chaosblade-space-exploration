# 项目容器化部署（Docker）

为以下 4 个服务提供一键构建与部署：
- svc-fault-scheduler (8103)
- svc-reqrsp-proxy  (8105)
- svc-task-executor (8102)
- svc-task-resource (8101)

## 前置条件
- Docker 20.10+
- Docker Compose v2（或 docker-compose v1）
- 建议：CPU≥2C，内存≥4GB

## 一键构建与部署
```bash
chmod +x build-and-deploy.sh
./build-and-deploy.sh
```
启动后：
- MySQL: localhost:3306（root/root）
- Redis: localhost:6379
- Web:
  - http://localhost:8101  svc-task-resource
  - http://localhost:8102  svc-task-executor
  - http://localhost:8103  svc-fault-scheduler
  - http://localhost:8105  svc-reqrsp-proxy

## 组件与配置
- MySQL：
  - 数据库：spaceexploration（由 MYSQL_DATABASE 创建）
  - 初始化：挂载 ./scripts/spaceexploration.sql 自动建表
- Redis：端口 6379

### 服务环境变量（docker-compose 中已示例配置）
- svc-task-resource
  - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/spaceexploration?...
  - DB_USERNAME=root, DB_PASSWORD=root
  - ENDPOINTS_EXECUTOR_BASE_URL=http://svc-task-executor:8102
- svc-task-executor
  - SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD
  - PROXY_BASE_URL=http://svc-reqrsp-proxy:8105
  - KUBERNETES_API_URL=https://kubernetes.default.svc:443
  - KUBERNETES_TOKEN=（请通过 .env 或 secrets 注入）
  - KUBERNETES_VERIFY_SSL=false
- svc-fault-scheduler
  - SPRING_DATA_REDIS_HOST=redis, SPRING_DATA_REDIS_PORT=6379
  - KUBERNETES_*
- svc-reqrsp-proxy
  - SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD
  - SPRING_DATA_REDIS_HOST=redis, SPRING_DATA_REDIS_PORT=6379
  - KUBERNETES_*

> 说明：Spring Boot 支持环境变量与配置项的“松散绑定”，如 `kubernetes.api-url` 可用环境变量 `KUBERNETES_API_URL` 覆盖。

## 运行与验证
```bash
# 健康探活
curl http://localhost:8101/hello
curl http://localhost:8102/hello
curl http://localhost:8103/hello
curl http://localhost:8105/hello

# 查看容器状态
if docker compose version >/dev/null 2>&1; then docker compose ps; else docker-compose ps; fi
```

## 故障排除
- 启动失败：确认 MySQL 已创建数据库 "spaceexploration"（compose 已自动执行 scripts/spaceexploration.sql）
- 外部依赖：覆盖各服务 application.yml 中的外部地址（尤其 K8s token/URL）为本地/目标环境；
- 查看日志：
  - docker compose logs -f svc-task-resource
  - docker compose logs -f svc-task-executor
  - docker compose logs -f svc-fault-scheduler
  - docker compose logs -f svc-reqrsp-proxy
- 基础设施连通性（若需排查 DB/Redis）：
  - 测试 MySQL: docker exec -it <mysql> mysql -uroot -proot -e 'show databases;'
  - 测试 Redis: docker exec -it <redis> redis-cli ping

## 安全与建议
- 示例 compose 未将 K8s API 访问凭据硬编码，请使用 .env 或 Docker secrets 注入；
- 如需更改 LLM 等外部调用配置，请改用环境变量覆盖（避免在镜像中写死）。

