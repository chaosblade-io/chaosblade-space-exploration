package com.chaosblade.svc.reqrspproxy.repository;

import com.chaosblade.svc.reqrspproxy.entity.ProxyInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProxyInstanceRepository extends JpaRepository<ProxyInstance, Long> {

    Optional<ProxyInstance> findByRecordingId(String recordingId);

    List<ProxyInstance> findByNamespaceAndTargetService(String namespace, String targetService);

    List<ProxyInstance> findByStatus(ProxyInstance.Status status);
}
