package ru.mirea.medicinedelivery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 1. Smoke-тест загрузки полного контекста Spring Boot на тестовой базе H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContextSmokeTest {

    @Test
    void contextLoads() {
    }
}
