package ro.tweebyte.userservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebConfiguration implements WebFluxConfigurer {

	@Override
	public void configurePathMatching(PathMatchConfigurer configurer) {
		configurer.setUseTrailingSlashMatch(true);
	}

}
