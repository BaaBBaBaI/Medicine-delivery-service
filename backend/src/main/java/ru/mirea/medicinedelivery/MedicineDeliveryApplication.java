package ru.mirea.medicinedelivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа серверного приложения «Заказ доставки лекарств» (Medicine Delivery Service).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MedicineDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicineDeliveryApplication.class, args);
    }
}
