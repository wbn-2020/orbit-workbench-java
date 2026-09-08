package com.orbitworkbench.shared.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Use Tomcat's NIO2 endpoint on Windows environments where the default NIO
 * selector cannot initialize its loopback wakeup pipe.
 */
@Configuration(proxyBeanMethods = false)
public class TomcatProtocolConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatProtocolCustomizer() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
