package com.chaosblade.svc.taskresource.service;

import com.chaosblade.svc.taskresource.entity.ApiTopology;
import com.chaosblade.svc.taskresource.entity.ApiTopologyEdge;
import com.chaosblade.svc.taskresource.entity.ApiTopologyNode;
import com.chaosblade.svc.taskresource.entity.Protocol;
import com.chaosblade.svc.taskresource.repository.ApiTopologyEdgeRepository;
import com.chaosblade.svc.taskresource.repository.ApiTopologyNodeRepository;
import com.chaosblade.svc.taskresource.repository.ApiTopologyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class ApiTopologyPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(ApiTopologyPersistenceService.class);

    @Autowired private ApiTopologyRepository topoRepository;
    @Autowired private ApiTopologyNodeRepository nodeRepository;
    @Autowired private ApiTopologyEdgeRepository edgeRepository;
    @Autowired private ObjectMapper objectMapper;

    @Transactional
    public void syncTopologyBundle(Long localSystemId, Long localApiId, JsonNode topologyNode, JsonNode nodes, JsonNode edges) {
        if (topoRepository.existsBySystemIdAndApiId(localSystemId, localApiId)) {
            log.debug("Topology already exists for systemId={}, apiId={}, skip", localSystemId, localApiId);
            return;
        }
        ApiTopology topo = new ApiTopology();
        topo.setSystemId(localSystemId);
        topo.setApiId(localApiId);
        topo.setDiscoveredAt(LocalDateTime.now());
        try {
            if (topologyNode != null && topologyNode.has("sourceVersion")) {
                topo.setSourceVersion(topologyNode.get("sourceVersion").asText(null));
            }
            if (topologyNode != null && topologyNode.has("notes")) {
                topo.setNotes(topologyNode.get("notes").asText(null));
            }
        } catch (Exception ignore) {}
        ApiTopology savedTopo = topoRepository.save(topo);

        Map<Long, Long> remoteNodeIdToLocal = new HashMap<>();
        Map<String, Long> nodeKeyToLocal = new HashMap<>();
        if (nodes != null && nodes.isArray()) {
            for (JsonNode n : nodes) {
                ApiTopologyNode node = new ApiTopologyNode();
                node.setTopologyId(savedTopo.getId());
                node.setNodeKey(text(n, "nodeKey"));
                node.setName(text(n, "name"));
                if (n.has("layer")) node.setLayer(n.get("layer").asInt(1));
                node.setProtocol(parseProtocol(text(n, "protocol")));
                try { node.setMetadata(objectToJson(n.get("metadata"))); } catch (Exception ignore) {}
                ApiTopologyNode savedNode = nodeRepository.save(node);
                long remoteId = n.path("id").asLong(0);
                if (remoteId > 0) remoteNodeIdToLocal.put(remoteId, savedNode.getId());
                if (node.getNodeKey() != null) nodeKeyToLocal.put(node.getNodeKey(), savedNode.getId());
            }
        }

        if (edges != null && edges.isArray()) {
            for (JsonNode e : edges) {
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
}

