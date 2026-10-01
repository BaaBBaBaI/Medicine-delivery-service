package ru.mirea.project;

import ru.mirea.project.repository.JdbcMedicineOrderRepository;
import ru.mirea.project.repository.JdbcUserRepository;
import ru.mirea.project.repository.MedicineOrderRepository;
import ru.mirea.project.repository.UserRepository;
import ru.mirea.project.service.MedicineOrderService;
import ru.mirea.project.service.MedicineOrderServiceImpl;
import ru.mirea.project.service.UserService;
import ru.mirea.project.service.UserServiceImpl;
import ru.mirea.project.ui.ConsoleMenu;
import ru.mirea.project.util.DatabaseManager;

public class Main {

    public static void main(String[] args) {
        try {
            DatabaseManager databaseManager = DatabaseManager.getInstance();

            if (!databaseManager.testConnection()) {
                System.out.println("   Внимание: Не удалось подключиться к базе данных PostgreSQL.");
                System.out.println("   Проверьте настройки подключения в application.properties и убедитесь, что СУБД запущена.");
                System.out.println("   Вы можете выполнить инициализацию таблиц и тестовых данных через пункт 9 меню программы.\n");
            }

            UserRepository userRepository = new JdbcUserRepository(databaseManager);
            MedicineOrderRepository orderRepository = new JdbcMedicineOrderRepository(databaseManager);

            UserService userService = new UserServiceImpl(userRepository);
            MedicineOrderService orderService = new MedicineOrderServiceImpl(orderRepository, userRepository);

            ConsoleMenu consoleMenu = new ConsoleMenu(userService, orderService);
            consoleMenu.start();

        } catch (Exception e) {
            System.err.println("Критическая ошибка при запуске приложения: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
