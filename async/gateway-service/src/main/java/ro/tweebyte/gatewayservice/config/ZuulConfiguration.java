package ro.tweebyte.gatewayservice.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.cglib.proxy.*;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.web.ZuulController;
import org.springframework.cloud.netflix.zuul.web.ZuulHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@Configuration
public class ZuulConfiguration {

    private static final String ERROR_PATH = "/error";

    @Bean
    public ZuulPostProcessor zuulPostProcessor(@Autowired RouteLocator routeLocator, @Autowired ZuulController zuulController,
                                               @Autowired(required = false) ErrorController errorController) {
        return new ZuulPostProcessor(routeLocator, zuulController, errorController);
    }

    public static final class ZuulPostProcessor implements BeanPostProcessor {

        private final RouteLocator routeLocator;

        private final ZuulController zuulController;

        private final boolean hasErrorController;

        ZuulPostProcessor(RouteLocator routeLocator, ZuulController zuulController, ErrorController errorController) {
            this.routeLocator = routeLocator;
            this.zuulController = zuulController;
            this.hasErrorController = (errorController != null);
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (hasErrorController && (bean instanceof ZuulHandlerMapping)) {
                Enhancer enhancer = new Enhancer();
                enhancer.setSuperclass(ZuulHandlerMapping.class);
                enhancer.setCallbackFilter(LookupHandlerCallbackFilter.INSTANCE);
                enhancer.setCallbacks(new Callback[] { LookupHandlerMethodInterceptor.INSTANCE, NoOp.INSTANCE });
                Constructor<?> ctor = ZuulHandlerMapping.class.getConstructors()[0];
                return enhancer.create(ctor.getParameterTypes(), new Object[] { routeLocator, zuulController });
            }
            return bean;
        }

    }

    private enum LookupHandlerCallbackFilter implements CallbackFilter {

        INSTANCE;

        @Override
        public int accept(Method method) {
            if ("lookupHandler".equals(method.getName())) {
                return 0;
            }
            return 1;
        }

    }

    private enum LookupHandlerMethodInterceptor implements MethodInterceptor {

        INSTANCE;

        @Override
        public Object intercept(Object target, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            if (ERROR_PATH.equals(args[0])) {
                return null;
            }

            return methodProxy.invokeSuper(target, args);
        }

    }

}
