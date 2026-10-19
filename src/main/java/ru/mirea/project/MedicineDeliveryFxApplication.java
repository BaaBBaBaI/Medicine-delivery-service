package ru.mirea.project;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.mirea.project.controller.MainController;
import ru.mirea.project.repository.JdbcMedicineOrderRepository;
import ru.mirea.project.repository.JdbcUserRepository;
import ru.mirea.project.repository.MedicineOrderRepository;
import ru.mirea.project.repository.UserRepository;
import ru.mirea.project.service.MedicineOrderService;
import ru.mirea.project.service.MedicineOrderServiceImpl;
import ru.mirea.project.service.UserService;
import ru.mirea.project.service.UserServiceImpl;
import ru.mirea.project.util.DatabaseManager;

import java.net.URL;

/**
 * Главный класс настольного JavaFX-приложения «Заказ доставки лекарств» (КР №2).
 */
public class MedicineDeliveryFxApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        UserRepository userRepository = new JdbcUserRepository(databaseManager);
        MedicineOrderRepository orderRepository = new JdbcMedicineOrderRepository(databaseManager);

        UserService userService = new UserServiceImpl(userRepository);
        MedicineOrderService orderService = new MedicineOrderServiceImpl(orderRepository, userRepository);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ru/mirea/project/view/main-view.fxml"));
        Parent root = loader.load();

        MainController mainController = loader.getController();
        mainController.initServices(orderService, userService, databaseManager);

        Scene scene = new Scene(root, 1340, 820);
        URL cssUrl = getClass().getResource("/ru/mirea/project/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setTitle("ИС «Заказ доставки лекарств» — Панель фармацевта-диспетчера (РТУ МИРЭА, КР №2)");
        primaryStage.setMinWidth(1080);
        primaryStage.setMinHeight(680);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
