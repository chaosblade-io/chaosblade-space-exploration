#!/bin/bash

echo "🧪 测试活跃录制 API"
echo "=================="

# 配置
BASE_URL="http://localhost:8105"
API_PREFIX="/api/direct-tap"

echo "📋 测试环境:"
echo "  Base URL: $BASE_URL"
echo "  API Prefix: $API_PREFIX"
echo ""

# 测试函数
test_api() {
    local endpoint="$1"
    local description="$2"
    
    echo "🔍 测试: $description"
    echo "   URL: $BASE_URL$endpoint"
    
    response=$(curl -s -w "\n%{http_code}" "$BASE_URL$endpoint" 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n -1)
    
    if [ "$http_code" = "200" ]; then
        echo "   ✅ 状态码: $http_code"
        echo "   📄 响应:"
        echo "$body" | jq . 2>/dev/null || echo "$body"
    else
        echo "   ❌ 状态码: $http_code"
        echo "   📄 响应: $body"
    fi
    echo ""
}

# 检查服务是否运行
echo "🔍 检查服务状态..."
if curl -s "$BASE_URL/actuator/health" >/dev/null 2>&1; then
    echo "✅ 服务正在运行"
else
    echo "❌ 服务未运行，请先启动 svc-reqrsp-proxy 服务"
    echo "   启动命令: cd svc-reqrsp-proxy && mvn spring-boot:run"
    exit 1
fi
echo ""

# 测试各个接口
test_api "$API_PREFIX/recordings/active" "获取活跃录制 ID 列表"

test_api "$API_PREFIX/recordings/active/details" "获取活跃录制详细信息"

test_api "$API_PREFIX/train-ticket/ts-travel2-service/info" "获取 ts-travel2-service 的 tap 信息"

test_api "$API_PREFIX/train-ticket/ts-travel2-service/entries?limit=5" "获取 ts-travel2-service 的前5条 tap 数据"

echo "🎉 测试完成!"
