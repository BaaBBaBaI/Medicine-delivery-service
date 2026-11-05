package ru.mirea.medicinedelivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.medicinedelivery.domain.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Репозиторий справочника клиентов аптечной сети. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Customer> findAllByOrderByCodeAsc();
    List<Customer> findByActiveTrueOrderByCodeAsc();
}
