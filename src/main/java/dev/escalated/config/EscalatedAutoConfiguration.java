package dev.escalated.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@ConditionalOnProperty(prefix = "escalated", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EscalatedProperties.class)
@ComponentScan(basePackages = "dev.escalated")
@EntityScan(basePackages = "dev.escalated.models")
@EnableJpaRepositories(basePackages = "dev.escalated.repositories")
@EnableScheduling
@Import(MessageSourceConfig.class)
public class EscalatedAutoConfiguration {
}
