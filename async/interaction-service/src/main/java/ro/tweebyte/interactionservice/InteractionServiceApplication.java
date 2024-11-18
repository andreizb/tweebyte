package ro.tweebyte.interactionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InteractionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InteractionServiceApplication.class, args);
    }

}
