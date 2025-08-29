package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.ApiQueryRequest;
import com.chaosblade.svc.topo.model.ApiQueryResponse;
import com.chaosblade.svc.topo.model.entity.Entity;
import com.chaosblade.svc.topo.model.entity.EntityType;
import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * API查询服务
 * 从currentTopology图数据中提取API信息
 */
@Service
public class ApiQueryService {

    private static final Logger logger = LoggerFactory.getLogger(ApiQueryService.class);

    /**
     * 根据查询请求从拓扑图中提取API列表
     *
     * @param topology 当前拓扑图
     * @param request  查询请求
     * @return API查询响应
     */
    public ApiQueryResponse queryApisFromTopology(TopologyGraph topology, ApiQueryRequest request) {
        logger.info("开始查询API列表，命名空间: {}, 服务数: {}",
            request.getNamespace(),
            request.getAppSelector() != null && request.getAppSelector().getServices() != null ? 
                request.getAppSelector().getServices().size() : 0);

        try {
            // 获取所有RPC类型的节点（代表API）
            List<Node> rpcNodes = topology.getNodesByType(EntityType.RPC);

            // 过滤节点
            List<ApiQueryResponse.ApiItem> apiItems = rpcNodes.stream()
                .filter(node -> filterByNamespace(node, request.getNamespace()))
                .filter(node -> filterByServices(node, request.getAppSelector()))
                .filter(node -> filterByTimeRange(node, request.getTimeRange()))
                .map(this::convertNodeToApiItem)
                .sorted((item1, item2) -> sortApiItems(item1, item2, request.getSort()))
                .collect(Collectors.toList());


            logger.info("查询完成，共找到 {} 个API", apiItems.size());
            return new ApiQueryResponse(apiItems);
        } catch (Exception e) {
            logger.error("查询API列表时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("查询API列表失败", e);
        }
    }

    /**
     * 根据命名空间过滤节点
     */
    private boolean filterByNamespace(Node node, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return true;
        }

        Entity entity = node.getEntity();
        if (entity == null) {
            return false;
        }

        // 检查appId中是否包含该命名空间
        // appId格式通常是 "{serviceName}@{namespace}"
        String appId = entity.getAppId();
        if (appId != null) {
            int atIndex = appId.indexOf('@');
            String nodeNamespace = atIndex >= 0 ? appId.substring(atIndex + 1) : appId;
            return namespace.equals(nodeNamespace);
        }

        return false;
    }

    /**
     * 根据服务列表过滤节点
     */
    private boolean filterByServices(Node node, ApiQueryRequest.AppSelector appSelector) {
        if (appSelector == null || appSelector.getServices() == null || appSelector.getServices().isEmpty()) {
            return true;
        }

        Entity entity = node.getEntity();
        if (entity == null) {
            return false;
        }

        // 从实体的appId中提取服务名进行匹配
        String appId = entity.getAppId();
        if (appId == null) {
            return false;
        }

        // appId格式通常是 "{serviceName}@{namespace}"
        String serviceName = extractServiceNameFromAppId(appId);
        return appSelector.getServices().contains(serviceName);
    }

    /**
     * 从appId中提取服务名
     */
    private String extractServiceNameFromAppId(String appId) {
        if (appId == null) {
            return null;
        }

        // 格式: {serviceName}@{namespace}
        int atIndex = appId.indexOf('@');
        String servicePart = atIndex >= 0 ? appId.substring(0, atIndex) : appId;

        return servicePart;
    }

    /**
     * 根据时间范围过滤节点
     */
    private boolean filterByTimeRange(Node node, ApiQueryRequest.TimeRange timeRange) {
        if (timeRange == null || (timeRange.getStart() == null && timeRange.getEnd() == null)) {
            return true;
        }

        Entity entity = node.getEntity();
        if (entity == null) {
            return false;
        }

        Long firstSeen = entity.getFirstSeen();
        Long lastSeen = entity.getLastSeen();

        // 如果没有时间信息，默认包含
        if (firstSeen == null && lastSeen == null) {
            return true;
        }

        // 检查时间范围
        if (timeRange.getStart() != null) {
            if (lastSeen != null && lastSeen < timeRange.getStart()) {
                return false; // 节点最后出现时间早于查询开始时间
            }
        }

        if (timeRange.getEnd() != null) {
            if (firstSeen != null && firstSeen > timeRange.getEnd()) {
                return false; // 节点首次出现时间晚于查询结束时间
            }
        }

        return true;
    }

    /**
     * 将节点转换为API项
     */
    private ApiQueryResponse.ApiItem convertNodeToApiItem(Node node) {
        ApiQueryResponse.ApiItem apiItem = new ApiQueryResponse.ApiItem();

        Entity entity = node.getEntity();
        if (entity != null) {
            // API ID
            apiItem.setApiId(entity.getEntityId());

            // 显示名称
            apiItem.setDisplayName(entity.getDisplayName());

            // 命名空间
            String namespace = null;
            String appId = entity.getAppId();
            if (appId != null) {
                // 从appId中提取命名空间
                int atIndex = appId.indexOf('@');
                String servicePart = atIndex >= 0 ? appId.substring(atIndex + 1) : appId;
                int dotIndex = servicePart.lastIndexOf('.');
                if (dotIndex > 0) {
                    namespace = servicePart.substring(dotIndex + 1);
                }
            }
            apiItem.setNamespace(namespace);

            // 提供者服务
            if (appId != null) {
                String serviceName = extractServiceNameFromAppId(appId);
                String serviceNamespace = namespace;
                String serviceId = appId; // 使用完整的appId作为serviceId

                ApiQueryResponse.ProviderService providerService = new ApiQueryResponse.ProviderService();
                providerService.setServiceId(serviceId);
                providerService.setName(serviceName);
                providerService.setNamespace(serviceNamespace);
                apiItem.setProviderService(providerService);
            }

            // 方法和URL信息从显示名称中提取
            String displayName = entity.getDisplayName();
            if (displayName != null) {
                parseMethodAndUrl(displayName, apiItem);
            }

            // 时间信息
            apiItem.setFirstSeen(entity.getFirstSeen());
            apiItem.setLastSeen(entity.getLastSeen());

            // 标签
            Map<String, String> labels = new HashMap<>();
            String protocol = (String) entity.getAttributes().get("protocol");
            if (protocol != null) {
                labels.put("protocol", protocol);
            }
            apiItem.setLabels(labels);
        }

        // 初始化参数和请求体
        apiItem.setParams(new ApiQueryResponse.Params());
        apiItem.setBody(new ApiQueryResponse.Body());

        return apiItem;
    }

    /**
     * 从显示名称中解析方法和URL信息
     */
    private void parseMethodAndUrl(String displayName, ApiQueryResponse.ApiItem apiItem) {
        // 格式示例: "接口【GET http://details:9080/details/0】"
        if (displayName != null && displayName.contains("【") && displayName.contains("】")) {
            // 提取括号中的内容
            int start = displayName.indexOf("【");
            int end = displayName.indexOf("】");
            if (start >= 0 && end > start) {
                String content = displayName.substring(start + 1, end);
                // 分离方法和URL
                String[] parts = content.split(" ", 2);
                if (parts.length >= 2) {
                    apiItem.setMethod(parts[0]);

                    ApiQueryResponse.UrlInfo urlInfo = new ApiQueryResponse.UrlInfo();
                    urlInfo.setTemplate(parts[1]);
                    urlInfo.setExample(parts[1]);

                    // 解析URL组件
                    try {
                        java.net.URL url = new java.net.URL(parts[1]);
                        urlInfo.setScheme(url.getProtocol());
                        urlInfo.setHost(url.getHost());
                        urlInfo.setPort(url.getPort() == -1 ? null : url.getPort());
                        urlInfo.setFullExample(parts[1]);
                    } catch (Exception e) {
                        // 解析失败时使用默认值
                        urlInfo.setScheme("http");
                        urlInfo.setHost("localhost");
                        urlInfo.setPort(8080);
                        urlInfo.setFullExample("http://localhost:8080" + (parts.length > 1 ? parts[1] : ""));
                    }

                    apiItem.setUrl(urlInfo);
                    apiItem.setDisplayName(content);
                }
            }
        } else if (displayName != null && displayName.contains(" ")) {
            // 格式示例: "GET /productpage"
            String[] parts = displayName.split(" ", 2);
            if (parts.length == 2) {
                apiItem.setMethod(parts[0]);

                ApiQueryResponse.UrlInfo urlInfo = new ApiQueryResponse.UrlInfo();
                urlInfo.setTemplate(parts[1]);
                urlInfo.setExample(parts[1]);
                // 默认值
                urlInfo.setScheme("http");
                urlInfo.setHost("localhost");
                urlInfo.setPort(8080);
                urlInfo.setFullExample("http://localhost:8080" + parts[1]);
                apiItem.setUrl(urlInfo);

                apiItem.setDisplayName(displayName);
            }
        }
    }

    /**
     * 对API项进行排序
     */
    private int sortApiItems(ApiQueryResponse.ApiItem item1, ApiQueryResponse.ApiItem item2, ApiQueryRequest.Sort sort) {
        if (sort == null) {
            // 默认按名称排序
            String name1 = item1.getDisplayName() != null ? item1.getDisplayName() : "";
            String name2 = item2.getDisplayName() != null ? item2.getDisplayName() : "";
            return name1.compareTo(name2);
        }

        int result = 0;

        switch (sort.getSortBy()) {
            case NAME:
                String name1 = item1.getDisplayName() != null ? item1.getDisplayName() : "";
                String name2 = item2.getDisplayName() != null ? item2.getDisplayName() : "";
                result = name1.compareToIgnoreCase(name2);
                break;
            case FIRST_SEEN:
                Long first1 = item1.getFirstSeen() != null ? item1.getFirstSeen() : 0L;
                Long first2 = item2.getFirstSeen() != null ? item2.getFirstSeen() : 0L;
                result = first1.compareTo(first2);
                break;
            case LAST_SEEN:
                Long last1 = item1.getLastSeen() != null ? item1.getLastSeen() : 0L;
                Long last2 = item2.getLastSeen() != null ? item2.getLastSeen() : 0L;
                result = last1.compareTo(last2);
                break;
            default:
                String default1 = item1.getDisplayName() != null ? item1.getDisplayName() : "";
                String default2 = item2.getDisplayName() != null ? item2.getDisplayName() : "";
                result = default1.compareToIgnoreCase(default2);
        }

        // 如果是降序，反转结果
        if (sort.getSortOrder() == ApiQueryRequest.Sort.SortOrder.DESC) {
            result = -result;
        }

        return result;
    }
}
