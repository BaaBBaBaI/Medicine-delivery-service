package ru.mirea.project.repository;

import java.util.List;
import java.util.Optional;

/**
 * Обобщенный интерфейс репозитория CRUD (демонстрация применения интерфейсов и полиморфизма).
 *
 * @param <T>  тип сущности
 * @param <ID> тип идентификатора
 */
public interface CrudRepository<T, ID> {
    T save(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    boolean update(T entity);
    boolean deleteById(ID id);
    long count();
}
