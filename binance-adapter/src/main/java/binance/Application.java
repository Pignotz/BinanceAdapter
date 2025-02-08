package binance;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "binance") // Adjust package based on your project structure

public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.exit(0);
    }
}
