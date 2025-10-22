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

import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.entity.HttpReqDef;
import com.chaosblade.svc.taskresource.repository.HttpReqDefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Transactional
public class HttpReqDefService {

    private static final Logger logger = LoggerFactory.getLogger(HttpReqDefService.class);

    @Autowired
    private HttpReqDefRepository repository;

    private static final Pattern URL_TEMPLATE_PATTERN = Pattern.compile("^/.*");

    @Transactional(readOnly = true)
    public PageResponse<HttpReqDef> pageQuery(String name, HttpReqDef.HttpMethod method, Long apiId,
                                              int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<HttpReqDef> p = repository.findByConditions(name, method, apiId, pageable);
        return PageResponse.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public HttpReqDef getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("HTTP_REQ_DEF_NOT_FOUND", "请求定义不存在: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<HttpReqDef> getByCode(String code) {
        return repository.findByCode(code);
    }

    public HttpReqDef create(HttpReqDef def) {
        validate(def, true);
        if (repository.existsByCode(def.getCode())) {
            throw new BusinessException("HTTP_REQ_DEF_CODE_EXISTS",
                    "请求定义编码已存在: " + def.getCode());
        }
        HttpReqDef saved = repository.save(def);
        logger.info("HttpReqDef created id={}, code={}", saved.getId(), saved.getCode());
        return saved;
    }

    public HttpReqDef update(Long id, HttpReqDef def) {
        HttpReqDef existing = getById(id);
        // code 不允许修改（如需允许，可在此校验唯一性）
        def.setId(existing.getId());
        def.setCode(existing.getCode());
        validate(def, false);
        HttpReqDef saved = repository.save(def);
        logger.info("HttpReqDef updated id={}, code={}", saved.getId(), saved.getCode());
        return saved;
    }

    public void delete(Long id) {
        HttpReqDef existing = getById(id);
        repository.delete(existing);
        logger.info("HttpReqDef deleted id={}, code={}", existing.getId(), existing.getCode());
    }

    private void validate(HttpReqDef def, boolean creating) {
        if (def.getUrlTemplate() == null || def.getUrlTemplate().isBlank()) {
            throw new BusinessException("URL_TEMPLATE_INVALID", "URL 模板不能为空");
        }
        // 简单格式校验：以 / 开头，可包含 {var}
        if (!URL_TEMPLATE_PATTERN.matcher(def.getUrlTemplate()).matches()) {
            throw new BusinessException("URL_TEMPLATE_INVALID",
                    "URL 模板需以 / 开头，如 /api/{name}/list");
        }
        // 枚举/内容一致性校验由实体上的 @AssertTrue 保证，这里可加 JSON 合法性校验（略，保持与项目其它 JSON 字段一致策略）
    }
}

