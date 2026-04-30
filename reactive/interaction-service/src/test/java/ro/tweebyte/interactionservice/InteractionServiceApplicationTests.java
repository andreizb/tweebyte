package ro.tweebyte.interactionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InteractionServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void main() {
        // boots the application
        // entry-point so SpringApplication.run wiring is exercised.
        InteractionServiceApplication.main(new String[] {});
    }

}
