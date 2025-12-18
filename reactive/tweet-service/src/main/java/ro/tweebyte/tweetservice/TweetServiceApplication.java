package ro.tweebyte.tweetservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
@Slf4j
public class TweetServiceApplication {

    public static void main(String[] args) {
        log.info("os.arch={}, availableProcessors={}",
            System.getProperty("os.arch"),
            Runtime.getRuntime().availableProcessors());
        SpringApplication.run(TweetServiceApplication.class, args);
    }

}
