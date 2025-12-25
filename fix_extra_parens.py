#!/usr/bin/env python3
import os
import re

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 修复 .collect(Collectors.toList())); -> .collect(Collectors.toList());
    content = re.sub(r'\.collect\(Collectors\.toList\(\)\)\);', '.collect(Collectors.toList());', content)
    content = re.sub(r'\.collect\(java\.util\.stream\.Collectors\.toList\(\)\)\);', '.collect(java.util.stream.Collectors.toList());', content)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# 遍历所有Java文件
for root, dirs, files in os.walk('svc-task-executor'):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            fix_file(filepath)
            print(f"Fixed: {filepath}")

print("Done!")

