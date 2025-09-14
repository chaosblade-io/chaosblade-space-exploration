const fs = require('fs');
const path = require('path');

// 读取原始 trace1.json 文件
function processTraceData() {
    try {
        const inputFile = path.join(__dirname, 'trace1.json');
        const outputFile = path.join(__dirname, 'trace1-processed.json');
        
        console.log('Reading trace1.json...');
        const rawData = fs.readFileSync(inputFile, 'utf8');
        const spans = JSON.parse(rawData);
        
        console.log(`Original spans count: ${spans.length}`);
        
        // 处理每个 span，只保留关键字段
        const processedSpans = spans.map(span => {
            const processedSpan = {
                service: span.service,
                trace_id: span.trace_id,
                id: span.id,
                parent_id: span.parent_id,
                name: span.name,
                timestamp: span.timestamp,
                duration: span.duration,
                status: span.status
            };
            
            // 处理 attributes，只保留关键字段
            if (span.attributes) {
                const filteredAttributes = {};
                
                // 保留的关键字段列表
                const keyFields = [
                    'http.request.method',
                    'http.response.status_code', 
                    'http.route',
                    'server.address',
                    'server.port',
                    'service.name',
                    'url.path',
                    'url.scheme',
                    'url.full'
                ];
                
                keyFields.forEach(field => {
                    if (span.attributes[field] !== undefined) {
                        filteredAttributes[field] = span.attributes[field];
                    }
                });
                
                processedSpan.attributes = filteredAttributes;
            }
            
            return processedSpan;
        });
        
        // 写入处理后的文件
        fs.writeFileSync(outputFile, JSON.stringify(processedSpans, null, 2), 'utf8');
        
        console.log(`Processed spans count: ${processedSpans.length}`);
        console.log(`Output file: ${outputFile}`);
        
        // 计算文件大小减少
        const originalSize = fs.statSync(inputFile).size;
        const processedSize = fs.statSync(outputFile).size;
        const reduction = ((originalSize - processedSize) / originalSize * 100).toFixed(1);
        
        console.log(`File size reduction: ${reduction}% (${originalSize} -> ${processedSize} bytes)`);
        
        return processedSpans;
        
    } catch (error) {
        console.error('Error processing trace data:', error);
        throw error;
    }
}

// 如果直接运行此脚本
if (require.main === module) {
    processTraceData();
}

module.exports = { processTraceData };
