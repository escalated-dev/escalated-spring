package consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.repositories.TicketRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** A host outside dev.escalated, compiled and started from the published JAR. */
@SpringBootApplication
public class Smoke {
    public static void main(String[] args) throws Exception {
        try (var context = new SpringApplicationBuilder(Smoke.class)
                .run(
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:package_consumer;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        // H2 development uses Hibernate; package migrations target PostgreSQL/MySQL.
                        "--escalated.datasource.url=",
                        "--escalated.datasource.migrate=false",
                        "--spring.mail.host=127.0.0.1",
                        "--spring.mail.port=9",
                        "--spring.main.banner-mode=off")) {
            if (!context.getBean(EscalatedProperties.class).isEnabled()) {
                throw new IllegalStateException("The package's default auto-configuration did not run");
            }
            if (context.getBean(TicketRepository.class).count() != 0) {
                throw new IllegalStateException("The package consumer must use a fresh database");
            }
            var mapper = context.getBean(ObjectMapper.class);
            if (!mapper.writeValueAsString(java.time.Instant.parse("2026-10-18T00:00:00Z"))
                    .contains("2026-10-18")) {
                throw new IllegalStateException("Jackson 2 must support the package's Java time values");
            }
            var artifact = EscalatedProperties.class.getProtectionDomain().getCodeSource().getLocation();
            if (!artifact.getPath().endsWith(".jar")) {
                throw new IllegalStateException("The consumer must load the installed JAR: " + artifact);
            }
            System.out.println("PACKAGED_SPRING_OK: enabled auto-configuration, repositories and fresh H2 schema; " + artifact);
        }
    }
}
