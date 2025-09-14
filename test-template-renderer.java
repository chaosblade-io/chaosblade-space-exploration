import java.util.*;
import java.io.*;

// 简单测试模板渲染逻辑
public class TestTemplateRenderer {
    
    static class RecordingRule {
        private String path;
        private String method;
        
        public RecordingRule(String path, String method) {
            this.path = path;
            this.method = method;
        }
        
        public String getPath() { return path; }
        public String getMethod() { return method; }
    }
    
    public static void main(String[] args) {
        System.out.println("🧪 测试模板渲染逻辑");
        System.out.println("===================");
        
        // 测试单规则情况
        testSingleRule();
        
        // 测试多规则情况
        testMultipleRules();
    }
    
    static void testSingleRule() {
        System.out.println("\n📋 测试单规则情况:");
        List<RecordingRule> rules = Arrays.asList(
            new RecordingRule("/api/v1/travel2service/trips/left", "POST")
        );
        
        boolean useSingleRule = rules.size() == 1;
        boolean useMultipleRules = rules.size() > 1;
        
        System.out.println("规则数量: " + rules.size());
        System.out.println("useSingleRule: " + useSingleRule);
        System.out.println("useMultipleRules: " + useMultipleRules);
        
        if (useSingleRule) {
            System.out.println("✅ 将使用 http_request_headers_match 格式");
            System.out.println("生成的配置片段:");
            System.out.println("  match_config:");
            System.out.println("    http_request_headers_match:");
            System.out.println("      headers:");
            for (RecordingRule rule : rules) {
                System.out.println("      - name: \":path\"");
                System.out.println("        string_match:");
                System.out.println("          exact: \"" + rule.getPath() + "\"");
                System.out.println("      - name: \":method\"");
                System.out.println("        string_match:");
                System.out.println("          exact: \"" + rule.getMethod() + "\"");
            }
        }
    }
    
    static void testMultipleRules() {
        System.out.println("\n📋 测试多规则情况:");
        List<RecordingRule> rules = Arrays.asList(
            new RecordingRule("/api/v1/travel2service/trips/left", "POST"),
            new RecordingRule("/api/v1/travel2service/trips/right", "GET")
        );
        
        boolean useSingleRule = rules.size() == 1;
        boolean useMultipleRules = rules.size() > 1;
        
        System.out.println("规则数量: " + rules.size());
        System.out.println("useSingleRule: " + useSingleRule);
        System.out.println("useMultipleRules: " + useMultipleRules);
        
        if (useMultipleRules) {
            System.out.println("✅ 将使用 or_match 格式");
            System.out.println("生成的配置片段:");
            System.out.println("  match_config:");
            System.out.println("    or_match:");
            System.out.println("      rules:");
            for (RecordingRule rule : rules) {
                System.out.println("      - http_request_headers_match:");
                System.out.println("          headers:");
                System.out.println("          - name: \":path\"");
                System.out.println("            string_match:");
                System.out.println("              exact: \"" + rule.getPath() + "\"");
                System.out.println("          - name: \":method\"");
                System.out.println("            string_match:");
                System.out.println("              exact: \"" + rule.getMethod() + "\"");
            }
        }
    }
}
