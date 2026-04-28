package ro.tweebyte.userservice.config;

import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class ReactivePoolConfiguration {

    @Bean(name = "readScheduler")
    public Scheduler readScheduler() {
        return Schedulers.newBoundedElastic(
            1000,
            100_000,
            "blocking-read",
            60,
            true
        );
    }

    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
        NettyReactiveWebServerFactory f = new NettyReactiveWebServerFactory();
        f.addServerCustomizers(server ->
                server
                    .option(ChannelOption.SO_BACKLOG, 5000)

                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new WriteBufferWaterMark(64 * 1024, 128 * 1024))

                    .childOption(ChannelOption.TCP_NODELAY, true)
        );
        return f;
    }

}
