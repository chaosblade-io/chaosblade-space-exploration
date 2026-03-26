package com.chaosblade.svc.reqrspproxy.repository;

import com.chaosblade.svc.reqrspproxy.entity.ProxySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProxySnapshotRepository extends JpaRepository<ProxySnapshot, Long> {

    List<ProxySnapshot> findByRecordingId(String recordingId);

    List<ProxySnapshot> findByRecordingIdAndSignatureHash(String recordingId, String signatureHash);
}
