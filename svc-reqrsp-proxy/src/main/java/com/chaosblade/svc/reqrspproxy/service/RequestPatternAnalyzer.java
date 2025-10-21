package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.dto.RecordedEntry;
import com.chaosblade.svc.reqrspproxy.dto.ServiceRequestPattern;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 请求模式分析器 分析录制的请求数据，提取服务请求模式 */
@Service
public class RequestPatternAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(RequestPatternAnalyzer.class);

  /** 分析录制的请求数据，提取服务请求模式 */
  public List<ServiceRequestPattern> analyzeRequestPatterns(
      List<RecordedEntry> recordedEntries, List<String> targetServices) {

    logger.info(
        "Analyzing request patterns for {} recorded entries and {} target services",
        recordedEntries.size(),
        targetServices.size());

    // 按服务名分组请求
    Map<String, List<RequestInfo>> serviceRequestMap =
        groupRequestsByService(recordedEntries, targetServices);

    // 为每个服务提取请求模式
    List<ServiceRequestPattern> patterns = new ArrayList<>();

    for (String serviceName : targetServices) {
      List<RequestInfo> requests = serviceRequestMap.getOrDefault(serviceName, new ArrayList<>());
      ServiceRequestPattern pattern = extractServicePattern(serviceName, requests);
      patterns.add(pattern);
    }

    logger.info("Extracted patterns for {} services", patterns.size());
    return patterns;
  }

  /** 按服务名分组请求 */
  private Map<String, List<RequestInfo>> groupRequestsByService(
      List<RecordedEntry> recordedEntries, List<String> targetServices) {

    Map<String, List<RequestInfo>> serviceRequestMap = new HashMap<>();

    // 初始化每个目标服务的列表
    for (String serviceName : targetServices) {
      serviceRequestMap.put(serviceName, new ArrayList<>());
    }

    for (RecordedEntry entry : recordedEntries) {
      try {
        RequestInfo requestInfo = extractRequestInfo(entry);
        if (requestInfo != null) {
          // 1) 优先使用录制时的 serviceName 归属
          String serviceName = entry.getServiceName();
          if (!StringUtils.hasText(serviceName) || !serviceRequestMap.containsKey(serviceName)) {
            // 2) 退化为基于 host/path 的规则判断
            serviceName = determineTargetService(requestInfo, targetServices);
          }
          if (serviceName != null) {
            serviceRequestMap.get(serviceName).add(requestInfo);
          } else {
            logger.debug(
                "Skip entry without determinable service. url={}, path={}",
                requestInfo.getUrl(),
                requestInfo.getPath());
          }
        }
      } catch (Exception e) {
        logger.warn("Failed to extract request info from entry: {}", e.getMessage());
      }
    }

    return serviceRequestMap;
  }

  /** 从录制条目中提取请求与响应信息 */
  private RequestInfo extractRequestInfo(RecordedEntry entry) {
    try {
      // 直接从 RecordedEntry 的字段中获取信息
      String method = entry.getMethod();
      String path = entry.getPath();
      String url = path;

      // 如果没有完整URL，尝试从headers中获取Host信息构建完整URL
      Map<String, String> reqHeaders = entry.getRequestHeaders();
      if (StringUtils.hasText(path) && reqHeaders != null) {
        String host = reqHeaders.get("host");
        if (host == null) {
          host = reqHeaders.get("Host");
        }
        if (StringUtils.hasText(host)) {
          url = "http://" + host + path;
        } else {
          url = path; // 使用相对路径
        }
      }

      String reqBody = entry.getRequestBody();

      Map<String, String> respHeaders = entry.getResponseHeaders();
      String respBody = entry.getResponseBody();
      Integer respStatus = entry.getStatus();
      Long respTime = null;
      if (respHeaders != null) {
        String t = respHeaders.get("x-envoy-upstream-service-time");
        if (!StringUtils.hasText(t)) t = respHeaders.get("X-Envoy-Upstream-Service-Time");
        if (StringUtils.hasText(t)) {
          try {
            respTime = Long.parseLong(t.trim());
          } catch (NumberFormatException ignore) {
          }
        }
      }

      if (StringUtils.hasText(method) && StringUtils.hasText(url)) {
        return new RequestInfo(
            method.toUpperCase(),
            url,
            path,
            reqHeaders,
            reqBody,
            respHeaders,
            respBody,
            respStatus,
            respTime);
      }

    } catch (Exception e) {
      logger.warn("Failed to extract request info: {}", e.getMessage());
    }

    return null;
  }

  /** 判断请求属于哪个目标服务 */
  private String determineTargetService(RequestInfo requestInfo, List<String> targetServices) {
    String url = requestInfo.getUrl();
    String path = requestInfo.getPath();

    // 优先根据URL中的主机名判断
    if (StringUtils.hasText(url)) {
      try {
        URI uri = new URI(url);
        String host = uri.getHost();
        if (StringUtils.hasText(host)) {
          for (String serviceName : targetServices) {
            if (host.contains(serviceName)) {
              return serviceName;
            }
          }
        }
      } catch (URISyntaxException e) {
        // 忽略URI解析错误，继续使用路径匹配
      }
    }

    // 根据路径中的服务名判断：匹配 /api/v1/{servicename}/ 前缀，避免误判
    if (StringUtils.hasText(path)) {
      for (String serviceName : targetServices) {
        String needle1 = "/api/" + serviceName + "/"; // 兼容 /api/ts-xxx-service/
        String needle2 = "/api/v1/" + serviceName + "/"; // 兼容 /api/v1/ts-xxx-service/
        if (path.startsWith(needle1)
            || path.startsWith(needle2)
            || path.contains("/" + serviceName + "/")) {
          return serviceName;
        }
      }
    }

    // 无法确定：返回 null（不再把请求归属到第一个服务，避免错配）
    return null;
  }

  /** 为单个服务提取请求模式 */
  private ServiceRequestPattern extractServicePattern(
      String serviceName, List<RequestInfo> requests) {
    // 去重并排序请求模式（保持原有逻辑：仅按 method + url；url 使用完整URL，保留查询串）
    Set<ServiceRequestPattern.RequestMode> uniqueModes =
        requests.stream()
            .map(
                req ->
                    new ServiceRequestPattern.RequestMode(
                        req.getMethod(),
                        req.getUrl(),
                        req.getHeaders(),
                        req.getBody(),
                        req.getRespHeaders(),
                        req.getRespBody(),
                        req.getRespStatus(),
                        req.getRespTime()))
            .collect(
                Collectors.toCollection(
                    () ->
                        new TreeSet<>(
                            (a, b) -> {
                              int methodCompare = a.getMethod().compareTo(b.getMethod());
                              return methodCompare != 0
                                  ? methodCompare
                                  : a.getUrl().compareTo(b.getUrl());
                            })));

    List<ServiceRequestPattern.RequestMode> requestModes = new ArrayList<>(uniqueModes);

    logger.debug("Service {} has {} unique request patterns", serviceName, requestModes.size());

    return new ServiceRequestPattern(serviceName, requestModes);
  }

  /** 标准化URL，移除查询参数和fragment */
  private String normalizeUrl(String url) {
    if (!StringUtils.hasText(url)) {
      return url;
    }

    try {
      URI uri = new URI(url);
      // 只保留scheme, host, port, path
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
          .toString();
    } catch (URISyntaxException e) {
      // 如果无法解析为URI，尝试简单的字符串处理
      int queryIndex = url.indexOf('?');
      if (queryIndex > 0) {
        url = url.substring(0, queryIndex);
      }
      int fragmentIndex = url.indexOf('#');
      if (fragmentIndex > 0) {
        url = url.substring(0, fragmentIndex);
      }
      return url;
    }
  }

  /** 请求信息内部类 */
  private static class RequestInfo {
    private String method;
    private String url;
    private String path;
    private Map<String, String> headers;
    private String body;
    private Map<String, String> respHeaders;
    private String respBody;
    private Integer respStatus;
    private Long respTime;

    public RequestInfo(
        String method,
        String url,
        String path,
        Map<String, String> headers,
        String body,
        Map<String, String> respHeaders,
        String respBody,
        Integer respStatus,
        Long respTime) {
      this.method = method;
      this.url = url;
      this.path = path;
      this.headers = headers;
      this.body = body;
      this.respHeaders = respHeaders;
      this.respBody = respBody;
      this.respStatus = respStatus;
      this.respTime = respTime;
    }

    public String getMethod() {
      return method;
    }

    public String getUrl() {
      return url;
    }

    public String getPath() {
      return path;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }

    public String getBody() {
      return body;
    }

    public Map<String, String> getRespHeaders() {
      return respHeaders;
    }

    public String getRespBody() {
      return respBody;
    }

    public Integer getRespStatus() {
      return respStatus;
    }

    public Long getRespTime() {
      return respTime;
    }
  }
}
