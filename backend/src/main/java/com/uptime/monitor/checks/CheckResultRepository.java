package com.uptime.monitor.checks;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    List<CheckResult> findByMonitorIdOrderByCheckedAtDesc(long monitorId, Pageable page);
}
