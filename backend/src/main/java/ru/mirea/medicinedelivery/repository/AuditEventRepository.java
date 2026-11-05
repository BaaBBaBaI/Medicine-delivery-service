package ru.mirea.medicinedelivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.mirea.medicinedelivery.domain.AuditEvent;

import java.util.UUID;

/** Репозиторий неизменяемых записей журнала аудита. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
}
