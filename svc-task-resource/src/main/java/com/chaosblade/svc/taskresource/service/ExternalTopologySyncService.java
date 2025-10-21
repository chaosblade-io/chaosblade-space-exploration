/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.taskresource.service;

import com.chaosblade.svc.taskresource.config.ExternalTopologyProperties;
import com.chaosblade.svc.taskresource.entity.*;
import com.chaosblade.svc.taskresource.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * External topology sync service
 */
@Service
public class ExternalTopologySyncService {
    private static final Logger log = LoggerFactory.getLogger(ExternalTopologySyncService.class);

    @Autowired private ExternalTopologyProperties props;
    @Autowired private SystemRepository systemRepository;
    @Autowired private ApiRepository apiRepository;
    @Autowired private ApiTopologyRepository topoRepository;
    @Autowired private ApiTopologyNodeRepository nodeRepository;
    @Autowired private ApiTopologyEdgeRepository edgeRepository;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private ApiTopologyPersistenceService topologyPersistenceService;

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(props.getTimeoutMs());
        f.setReadTimeout(props.getTimeoutMs());
        return new RestTemplate(f);
    }

    /**
     * Sync external systems and related APIs & topologies before listing
     * - Only insert when missing (avoid duplicates)
     */
    public void syncBeforeList() {
        String base = props.getBaseUrl();
        log.info("我执行了哦: {}", base);
        if (base == null || base.isBlank()) {
            log.warn("external.topology.base-url not configured, skip external sync");
            return;
        }
        try {
            List<ExternalSystemItem> externalSystems = fetchSystems();
            if (externalSystems == null || externalSystems.isEmpty()) {
                log.info("No external systems found to sync");
                return;
            }
            log.info("Found {} external systems to sync", externalSystems.size());
            // Collect newly created systems for further sync (apis, topology)
            List<NewSystemPair> newlyCreated = new ArrayList<>();

            for (ExternalSystemItem ext : externalSystems) {
                if (ext.systemKey == null || ext.systemKey.isBlank()) continue;
                boolean exists = systemRepository.existsBySystemKey(ext.systemKey);
                log.info("ext.systemKey: {}", ext.systemKey);
                log.info("ext.name: {}", ext.name);
                log.info("ext.description: {}", ext.description);
                log.info("ext.owner: {}", ext.owner);
                log.info("ext.defaultEnvironment: {}", ext.defaultEnvironment);
                if (exists) {
                    continue; // skip existing systems
                }
                
                // create new system
                com.chaosblade.svc.taskresource.entity.System sys = new com.chaosblade.svc.taskresource.entity.System();
                sys.setSystemKey(ext.systemKey);
                sys.setName(ext.name != null ? ext.name : ext.systemKey);
                sys.setDescription(ext.description);
                sys.setOwner(ext.owner);
                sys.setDefaultEnvironment(ext.defaultEnvironment != null ? ext.defaultEnvironment : "prod");
                
                com.chaosblade.svc.taskresource.entity.System saved = saveSystem(sys);
                
                newlyCreated.add(new NewSystemPair(saved.getId(), ext.id));
                log.info("Synced new system: localId={}, systemKey={}, remoteId={}", saved.getId(), saved.getSystemKey(), ext.id);
            }

            // For each newly created system, fetch apis and then topology for new apis
            for (NewSystemPair p : newlyCreated) {
                try {
                    List<ExternalApiItem> apis = fetchApis(p.remoteSystemId);
                    if (apis == null) continue;
                    for (ExternalApiItem extApi : apis) {
                        // dedupe by (systemId, operationId)
                        if (extApi.operationId == null || extApi.operationId.isBlank()) continue;
                        if (apiRepository.existsBySystemIdAndOperationId(p.localSystemId, extApi.operationId)) {
                            continue;
                        }
                        Api localApi = new Api();
                        localApi.setSystemId(p.localSystemId);
                        localApi.setOperationId(extApi.operationId);
                        localApi.setMethod(nullSafe(extApi.method));
                        localApi.setPath(nullSafe(extApi.path));
                        localApi.setSummary(extApi.summary);
                        if (extApi.tags != null) {
                            try { localApi.setTags(objectMapper.writeValueAsString(extApi.tags)); } catch (JsonProcessingException ignore) {}
                        }
                        localApi.setVersion(extApi.version);
                        localApi.setBaseUrl(extApi.baseUrl);
                        Api savedApi = saveApi(localApi);
                        log.info("Synced new API: id={}, operationId={} for systemId={}", savedApi.getId(), savedApi.getOperationId(), p.localSystemId);

                        // fetch and sync topology for this API
                        try {
                            ExternalTopologyPayload topoPayload = fetchTopology(p.remoteSystemId);
                            if (topoPayload != null) {
                                topologyPersistenceService.syncTopologyBundle(
                                        p.localSystemId, savedApi.getId(),
                                        topoPayload.topology, topoPayload.nodes, topoPayload.edges);
                            }
                        } catch (Exception e) {
                            log.warn("Sync topology failed for system {} api {}: {}", p.localSystemId, savedApi.getId(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Sync APIs failed for system localId={}, remoteId={}: {}", p.localSystemId, p.remoteSystemId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("External sync encountered error: {}", e.getMessage(), e);
        }
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    // region HTTP fetchers
    private List<ExternalSystemItem> fetchSystems() {
        String url = props.getBaseUrl() + "/v1/topology/namespaces";
        RestTemplate rt = buildRestTemplate();
        try {
            JsonNode root = rt.getForObject(URI.create(url), JsonNode.class);
            if (root == null || !root.path("success").asBoolean(false)) return Collections.emptyList();
            JsonNode items = root.path("data").path("items");
            if (items == null || !items.isArray()) return Collections.emptyList();
            List<ExternalSystemItem> list = new ArrayList<>();
            for (JsonNode n : items) {
                ExternalSystemItem it = new ExternalSystemItem();
                it.id = n.path("id").asLong(0);
                it.systemKey = text(n, "systemKey");
                it.k8sNamespace = text(n, "k8sNamespace");
                it.name = text(n, "name");
                it.description = text(n, "description");
                it.owner = text(n, "owner");
                it.defaultEnvironment = text(n, "defaultEnvironment");
                list.add(it);
            }
            return list;
        } catch (Exception e) {
            log.warn("Fetch systems failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ExternalApiItem> fetchApis(Long remoteSystemId) {
        if (remoteSystemId == null) return Collections.emptyList();
        String url = props.getBaseUrl() + "/v1/topology/" + remoteSystemId + "/apis";
        RestTemplate rt = buildRestTemplate();
        try {
            JsonNode root = rt.getForObject(URI.create(url), JsonNode.class);
            if (root == null || !root.path("success").asBoolean(false)) return Collections.emptyList();
            JsonNode items = root.path("data").path("items");
            if (items == null || !items.isArray()) return Collections.emptyList();
            List<ExternalApiItem> list = new ArrayList<>();
            for (JsonNode n : items) {
                ExternalApiItem it = new ExternalApiItem();
                it.id = n.path("id").asLong(0);
                it.systemId = n.path("systemId").asLong(0);
                it.operationId = text(n, "operationId");
                it.method = text(n, "method");
                it.path = text(n, "path");
                it.summary = text(n, "summary");
                it.version = text(n, "version");
                it.baseUrl = text(n, "baseUrl");
                // tags can be array or string
                JsonNode tagsNode = n.get("tags");
                if (tagsNode != null) {
                    if (tagsNode.isArray()) {
                        it.tags = new ArrayList<>();
                        for (JsonNode t : tagsNode) it.tags.add(t.asText());
                    } else if (tagsNode.isTextual()) {
                        it.tags = Arrays.stream(tagsNode.asText().split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    }
                }
                list.add(it);
            }
            return list;
        } catch (Exception e) {
            log.warn("Fetch apis failed for systemId {}: {}", remoteSystemId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private ExternalTopologyPayload fetchTopology(Long remoteSystemId) {
        String url = props.getBaseUrl() + "/v1/topology/" + remoteSystemId + "/services";
        RestTemplate rt = buildRestTemplate();
        try {
            JsonNode root = rt.getForObject(URI.create(url), JsonNode.class);
            if (root == null || !root.path("success").asBoolean(false)) return null;
            JsonNode data = root.path("data");
            if (data == null || data.isMissingNode()) return null;
            ExternalTopologyPayload payload = new ExternalTopologyPayload();
            payload.topology = data.get("topology");
            payload.nodes = data.get("nodes");
            payload.edges = data.get("edges");
            return payload;
        } catch (Exception e) {
            log.warn("Fetch topology failed for systemId {}: {}", remoteSystemId, e.getMessage());
            return null;
        }
    }
    // endregion

    // region persistence helpers
    @Transactional
    protected com.chaosblade.svc.taskresource.entity.System saveSystem(com.chaosblade.svc.taskresource.entity.System sys) {
        return systemRepository.save(sys);
    }

    @Transactional
    protected Api saveApi(Api api) { return apiRepository.save(api); }

    @Transactional
    public void syncTopologyBundle(Long localSystemId, Long localApiId, ExternalTopologyPayload payload) {
        if (payload == null) return;
        // Topology header -> insert if not exists
        if (topoRepository.existsBySystemIdAndApiId(localSystemId, localApiId)) {
            log.debug("Topology already exists for systemId={}, apiId={}, skip", localSystemId, localApiId);
            return;
        }
        ApiTopology topo = new ApiTopology();
        topo.setSystemId(localSystemId);
        topo.setApiId(localApiId);
        topo.setDiscoveredAt(LocalDateTime.now());
        try {
            if (payload.topology != null && payload.topology.has("sourceVersion")) {
                topo.setSourceVersion(payload.topology.get("sourceVersion").asText(null));
            }
            if (payload.topology != null && payload.topology.has("notes")) {
                topo.setNotes(payload.topology.get("notes").asText(null));
            }
        } catch (Exception ignore) {}
        ApiTopology savedTopo = topoRepository.save(topo);

        // nodes: build remoteId -> localId map
        Map<Long, Long> remoteNodeIdToLocal = new HashMap<>();
        Map<String, Long> nodeKeyToLocal = new HashMap<>();
        if (payload.nodes != null && payload.nodes.isArray()) {
            for (JsonNode n : payload.nodes) {
                ApiTopologyNode node = new ApiTopologyNode();
                node.setTopologyId(savedTopo.getId());
                node.setNodeKey(text(n, "nodeKey"));
                node.setName(text(n, "name"));
                if (n.has("layer")) node.setLayer(n.get("layer").asInt(1));
                node.setProtocol(parseProtocol(text(n, "protocol")));
                // store metadata raw
                try { node.setMetadata(objectToJson(n.get("metadata"))); } catch (Exception ignore) {}
                ApiTopologyNode savedNode = nodeRepository.save(node);
                long remoteId = n.path("id").asLong(0);
                if (remoteId > 0) remoteNodeIdToLocal.put(remoteId, savedNode.getId());
                if (node.getNodeKey() != null) nodeKeyToLocal.put(node.getNodeKey(), savedNode.getId());
            }
        }

        // edges
        if (payload.edges != null && payload.edges.isArray()) {
            for (JsonNode e : payload.edges) {
                Long fromLocal = mapEdgeEndpoint(e, "fromNodeId", "fromNodeKey", remoteNodeIdToLocal, nodeKeyToLocal);
                Long toLocal = mapEdgeEndpoint(e, "toNodeId", "toNodeKey", remoteNodeIdToLocal, nodeKeyToLocal);
                if (fromLocal == null || toLocal == null) {
                    log.debug("Skip edge, cannot resolve endpoints: {}", e);
                    continue;
                }
                ApiTopologyEdge edge = new ApiTopologyEdge();
                edge.setTopologyId(savedTopo.getId());
                edge.setFromNodeId(fromLocal);
                edge.setToNodeId(toLocal);
                try { edge.setMetadata(objectToJson(e.get("metadata"))); } catch (Exception ignore) {}
                edgeRepository.save(edge);
            }
        }
        log.info("Synced topology: topologyId={}, nodes={}, edges={}", savedTopo.getId(),
                nodeRepository.countByTopologyId(savedTopo.getId()), edgeRepository.countByTopologyId(savedTopo.getId()));
    }
    // endregion

    private String text(JsonNode n, String field) { return n != null && n.has(field) && !n.get(field).isNull() ? n.get(field).asText() : null; }
    private String objectToJson(JsonNode n) throws JsonProcessingException { return n == null || n.isNull() ? null : objectMapper.writeValueAsString(n); }

    private Protocol parseProtocol(String p) {
        if (p == null) return Protocol.HTTP;
        try {
            if ("grpc".equalsIgnoreCase(p)) return Protocol.gRPC;
            if ("db".equalsIgnoreCase(p) || "database".equalsIgnoreCase(p)) return Protocol.DB;
            if ("mq".equalsIgnoreCase(p) || "kafka".equalsIgnoreCase(p) || "rabbitmq".equalsIgnoreCase(p)) return Protocol.MQ;
            if ("http".equalsIgnoreCase(p)) return Protocol.HTTP;
            return Protocol.valueOf(p);
        } catch (Exception e) {
            return Protocol.OTHER;
        }
    }

    private Long mapEdgeEndpoint(JsonNode e, String idField, String keyField,
                                 Map<Long, Long> idMap, Map<String, Long> keyMap) {
        Long local = null;
        long rid = e.path(idField).asLong(0);
        if (rid > 0 && idMap != null) local = idMap.get(rid);
        if (local == null && e.has(keyField) && !e.get(keyField).isNull()) {
            local = keyMap.get(e.get(keyField).asText());
        }
        return local;
    }

    // DTOs used internally
    static class ExternalSystemItem {
        Long id;
        String systemKey;
        String k8sNamespace;
        String name;
        String description;
        String owner;
        String defaultEnvironment;
    }

    static class ExternalApiItem {
        Long id;
        Long systemId;
        String operationId;
        String method;
        String path;
        String summary;
        String version;
        String baseUrl;
        List<String> tags;
    }

    static class ExternalTopologyPayload {
        JsonNode topology;
        JsonNode nodes;
        JsonNode edges;
    }

    static class NewSystemPair {
        final Long localSystemId;
        final Long remoteSystemId;
        NewSystemPair(Long localSystemId, Long remoteSystemId) {
            this.localSystemId = localSystemId;
            this.remoteSystemId = remoteSystemId;
        }
    }
}

