#!/usr/bin/env python3
"""
手动 Tap 数据收集器
从 Kubernetes Pod 中收集 Envoy tap 文件并解析为 RecordedEntry 格式
"""

import json
import subprocess
import sys
import os
from datetime import datetime
from typing import List, Dict, Any

def run_kubectl_command(cmd: List[str]) -> str:
    """执行 kubectl 命令"""
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"命令执行失败: {' '.join(cmd)}")
        print(f"错误: {e.stderr}")
        return ""

def get_pods_by_service(namespace: str, service_name: str) -> List[str]:
    """获取服务对应的 Pod 列表"""
    cmd = [
        "kubectl", "get", "pods", "-n", namespace,
        "-l", f"app={service_name}",
        "-o", "jsonpath={.items[*].metadata.name}"
    ]
    output = run_kubectl_command(cmd)
    return output.split() if output else []

def list_tap_files(namespace: str, pod_name: str) -> List[str]:
    """列出 Pod 中的 tap 文件"""
    cmd = [
        "kubectl", "exec", pod_name, "-c", "envoy", "-n", namespace,
        "--", "find", "/var/log/envoy/taps", "-name", "*.json", "-type", "f"
    ]
    output = run_kubectl_command(cmd)
    return output.split('\n') if output else []

def read_tap_file(namespace: str, pod_name: str, file_path: str) -> Dict[str, Any]:
    """读取 tap 文件内容"""
    cmd = [
        "kubectl", "exec", pod_name, "-c", "envoy", "-n", namespace,
        "--", "cat", file_path
    ]
    output = run_kubectl_command(cmd)
    try:
        return json.loads(output)
    except json.JSONDecodeError as e:
        print(f"解析 JSON 失败: {file_path}, 错误: {e}")
        return {}

def parse_tap_to_recorded_entry(tap_data: Dict[str, Any], pod_name: str, file_path: str) -> Dict[str, Any]:
    """将 tap 数据转换为 RecordedEntry 格式"""
    if "http_buffered_trace" not in tap_data:
        return {}
    
    trace = tap_data["http_buffered_trace"]
    request = trace.get("request", {})
    response = trace.get("response", {})
    
    # 提取请求信息
    request_headers = {}
    for header in request.get("headers", []):
        request_headers[header["key"]] = header["value"]
    
    # 提取响应信息
    response_headers = {}
    for header in response.get("headers", []):
        response_headers[header["key"]] = header["value"]
    
    # 构建 RecordedEntry
    entry = {
        "id": os.path.basename(file_path).replace(".json", ""),
        "timestamp": datetime.now().isoformat(),
        "podName": pod_name,
        "request": {
            "method": request_headers.get(":method", ""),
            "path": request_headers.get(":path", ""),
            "headers": request_headers,
            "body": request.get("body", {}).get("as_string", "")
        },
        "response": {
            "status": int(response_headers.get(":status", "0")),
            "headers": response_headers,
            "body": response.get("body", {}).get("as_string", "")
        }
    }
    
    return entry

def collect_tap_data(namespace: str, service_name: str) -> List[Dict[str, Any]]:
    """收集指定服务的所有 tap 数据"""
    print(f"🔍 收集服务 {service_name} 在命名空间 {namespace} 中的 tap 数据...")
    
    pods = get_pods_by_service(namespace, service_name)
    if not pods:
        print(f"❌ 未找到服务 {service_name} 的 Pod")
        return []
    
    print(f"📋 找到 {len(pods)} 个 Pod: {', '.join(pods)}")
    
    all_entries = []
    
    for pod_name in pods:
        print(f"\n🔍 处理 Pod: {pod_name}")
        
        tap_files = list_tap_files(namespace, pod_name)
        if not tap_files:
            print(f"  ⚠️  未找到 tap 文件")
            continue
        
        print(f"  📁 找到 {len(tap_files)} 个 tap 文件")
        
        for file_path in tap_files:
            if not file_path.strip():
                continue
                
            print(f"    📄 处理文件: {file_path}")
            
            tap_data = read_tap_file(namespace, pod_name, file_path)
            if not tap_data:
                continue
            
            entry = parse_tap_to_recorded_entry(tap_data, pod_name, file_path)
            if entry:
                all_entries.append(entry)
                print(f"    ✅ 解析成功: {entry['request']['method']} {entry['request']['path']}")
    
    return all_entries

def main():
    if len(sys.argv) != 3:
        print("用法: python3 manual-tap-collector.py <namespace> <service-name>")
        print("示例: python3 manual-tap-collector.py train-ticket ts-travel2-service")
        sys.exit(1)
    
    namespace = sys.argv[1]
    service_name = sys.argv[2]
    
    print(f"🚀 手动 Tap 数据收集器")
    print(f"命名空间: {namespace}")
    print(f"服务名称: {service_name}")
    print("=" * 50)
    
    entries = collect_tap_data(namespace, service_name)
    
    if entries:
        output_file = f"collected_entries_{service_name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(entries, f, indent=2, ensure_ascii=False)
        
        print(f"\n🎉 收集完成!")
        print(f"📊 总共收集到 {len(entries)} 条记录")
        print(f"💾 数据已保存到: {output_file}")
        
        # 显示前几条记录的摘要
        print(f"\n📋 记录摘要:")
        for i, entry in enumerate(entries[:5]):
            print(f"  {i+1}. {entry['request']['method']} {entry['request']['path']} -> {entry['response']['status']}")
        
        if len(entries) > 5:
            print(f"  ... 还有 {len(entries) - 5} 条记录")
    else:
        print("\n❌ 未收集到任何数据")

if __name__ == "__main__":
    main()
