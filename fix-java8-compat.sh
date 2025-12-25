#!/bin/bash

# 批量修复Java 8兼容性问题的脚本

echo "开始修复Java 8兼容性问题..."

# 查找所有Java文件
find svc-task-executor svc-task-resource svc-reqrsp-proxy svc-fault-scheduler svc-result-processor common -name "*.java" -type f | while read file; do
    # 备份原文件
    cp "$file" "$file.bak"
    
    # 替换 .toList() 为 .collect(Collectors.toList())
    # 需要确保导入了Collectors
    if grep -q "\.toList()" "$file"; then
        # 添加import如果不存在
        if ! grep -q "import java.util.stream.Collectors;" "$file"; then
            # 在package声明后添加import
            sed -i '/^package /a import java.util.stream.Collectors;' "$file"
        fi
        # 替换toList()
        sed -i 's/\.toList()/\.collect(Collectors.toList())/g' "$file"
    fi
    
    # 删除备份文件
    rm -f "$file.bak"
done

echo "修复完成！"

