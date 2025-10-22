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

package com.chaosblade.svc.taskexecutor.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "api_topology_nodes")
public class ApiTopologyNode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topology_id", nullable = false)
    private Long topologyId;

    @Column(name = "node_key", nullable = false, length = 128)
    private String nodeKey;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "layer", nullable = false)
    private Integer layer = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false)
    private Protocol protocol = Protocol.HTTP;

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    public Long getId() { return id; }
    public Long getTopologyId() { return topologyId; }
    public String getNodeKey() { return nodeKey; }
    public String getName() { return name; }
    public Protocol getProtocol() { return protocol; }
}

