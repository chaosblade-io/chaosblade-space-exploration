# ChaosBlade Space Exploration Helm 部署故障排除指南

## 问题：Secret 创建失败

### 症状
- 只有 `chaosblade-space-exploration-llm` Secret 创建成功
- 其他 Secret（database、redis、kubernetes）没有创建

### 解决方案

#### 1. 重新部署 Helm Chart
```bash
# 卸载现有安装
helm uninstall chaosblade-space-exploration -n chaosblade

# 重新安装
helm install chaosblade-space-exploration ./helm/chaosblade-space-exploration -n chaosblade --create-namespace
```

#### 2. 验证 Secret 创建
```bash
# 检查所有 Secret
kubectl get secrets -n chaosblade

# 检查特定 Secret 的详细信息
kubectl describe secret chaosblade-space-exploration-database -n chaosblade
kubectl describe secret chaosblade-space-exploration-redis -n chaosblade
kubectl describe secret chaosblade-space-exploration-kubernetes -n chaosblade
kubectl describe secret chaosblade-space-exploration-llm -n chaosblade
```

#### 3. 检查 Pod 状态
```bash
# 查看所有 Pod 状态
kubectl get pods -n chaosblade

# 查看 Pod 事件
kubectl describe pod <pod-name> -n chaosblade

# 查看 Pod 日志
kubectl logs <pod-name> -n chaosblade
```

#### 4. 检查 Helm 状态
```bash
# 查看 Helm 发布状态
helm status chaosblade-space-exploration -n chaosblade

# 查看 Helm 历史
helm history chaosblade-space-exploration -n chaosblade
```

### 修复内容

1. **统一 Secret 模板**：将所有 Secret 合并到 `secrets.yaml` 文件中
2. **添加 YAML 分隔符**：每个 Secret 之间添加 `---` 分隔符
3. **添加组件标签**：为每个 Secret 添加 `app.kubernetes.io/component` 标签便于识别
4. **修正默认值**：确保所有 Secret 都有有效的默认值

### 预期结果

部署成功后，应该看到以下 Secret：
- `chaosblade-space-exploration-database`
- `chaosblade-space-exploration-redis`
- `chaosblade-space-exploration-kubernetes`
- `chaosblade-space-exploration-llm`

### 如果问题仍然存在

1. **检查命名空间**：确保 Secret 在正确的命名空间中
2. **检查权限**：确保有创建 Secret 的权限
3. **检查资源限制**：确保集群有足够的资源
4. **查看 Helm 日志**：使用 `helm install --debug` 查看详细日志

### 联系支持

如果问题仍然存在，请提供：
- `kubectl get secrets -n chaosblade` 的输出
- `helm status chaosblade-space-exploration -n chaosblade` 的输出
- 任何相关的错误日志

