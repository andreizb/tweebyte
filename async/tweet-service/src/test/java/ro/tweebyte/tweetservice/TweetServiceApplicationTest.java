package ro.tweebyte.tweetservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TweetServiceApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    public void main() {
        TweetServiceApplication.main(new String[] {});
    }

}