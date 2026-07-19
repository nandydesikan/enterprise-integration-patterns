package io.github.nandydesikan.eip.returns;

import io.github.nandydesikan.eip.returns.infrastructure.configuration.MediationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MediationProperties.class)
public class ServiceMediationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceMediationApplication.class, args);
    }
}
