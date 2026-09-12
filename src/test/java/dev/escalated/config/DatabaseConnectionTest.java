package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import hostapp.HostApplication;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.TicketRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Escalated's tables on a database of the host's choosing.
 *
 * <p>Every repository in the package bound to the host's
 * {@code EntityManagerFactory}, which forced Escalated's tables into the host's
 * database — unusable in any host that partitions its data. Setting
 * {@code escalated.datasource.url} now gives Escalated a persistence unit of its
 * own; leaving it unset keeps the wiring exactly as it was.
 *
 * <p>Both halves are worth pinning. The shared case is the one every existing
 * host runs, and it must stay the <em>same</em> beans rather than equivalent
 * ones — a second {@code EntityManagerFactory} over the same database would pass
 * an equality check and still be a behaviour change.
 */
class DatabaseConnectionTest {

    @Nested
    @SpringBootTest(classes = HostApplication.class)
    @ActiveProfiles("test")
    // The package's mail service needs a JavaMailSender to construct; the rest
    // of the suite never boots a whole context, so nothing configured one.
    @TestPropertySource(properties = {"spring.mail.host=localhost"})
    class SharingTheHostsDatabase {

        @Autowired private ApplicationContext context;

        @Test
        void escalatedUsesTheHostsEntityManagerFactory() {
            // Identity, not equality. Every repository in the package names
            // this bean, so if it stops being the host's own, every query has
            // quietly moved to another persistence unit.
            assertThat(context.getBean("escalatedEntityManagerFactory"))
                    .isSameAs(context.getBean("entityManagerFactory"));
        }

        @Test
        void escalatedUsesTheHostsTransactionManager() {
            assertThat(context.getBean(EscalatedTransactionManagers.ESCALATED))
                    .isSameAs(context.getBean("transactionManager"));
        }

        @Test
        void theHostStillHasExactlyOneEntityManagerFactoryToInject() {
            // The names above are aliases, not extra beans. A second bean of
            // this type would make @Autowired EntityManagerFactory ambiguous
            // throughout the host application.
            assertThat(context.getBeanNamesForType(EntityManagerFactory.class))
                    .hasSize(1);
        }

        @Test
        void noDedicatedDataSourceIsCreated() {
            assertThat(context.containsBean("escalatedDataSource")).isFalse();
        }
    }

    @Nested
    @SpringBootTest(classes = HostApplication.class)
    @ActiveProfiles("test")
    @TestPropertySource(
            properties = {
                "spring.mail.host=localhost",
                // A host database of this test's own. H2's DB_CLOSE_DELAY=-1
                // keeps an in-memory database alive for the whole JVM, so
                // sharing testdb with the other context would leave that
                // context's escalated_ tables sitting in it.
                "spring.datasource.url=jdbc:h2:mem:hostdb_split;DB_CLOSE_DELAY=-1",
                // A genuinely separate database. Pointing this at the host's own
                // would pass every assertion below and prove nothing.
                "escalated.datasource.url=jdbc:h2:mem:supportdb;DB_CLOSE_DELAY=-1",
                "escalated.datasource.username=sa",
                "escalated.datasource.password=",
                "escalated.datasource.driver-class-name=org.h2.Driver",
                "escalated.datasource.ddl-auto=create-drop",
                // Flyway's migrations are the host's job to run here; the point
                // under test is which connection the tables land on.
                "escalated.datasource.migrate=false",
            })
    class OnItsOwnDatabase {

        @Autowired private ApplicationContext context;
        @Autowired private TicketRepository tickets;

        @Autowired
        @Qualifier("escalatedDataSource")
        private DataSource escalatedDataSource;

        @Autowired
        @Qualifier("dataSource")
        private DataSource hostDataSource;

        @Test
        void escalatedGetsAPersistenceUnitOfItsOwn() {
            assertThat(context.getBean("escalatedEntityManagerFactory"))
                    .isNotSameAs(context.getBean("entityManagerFactory"));

            assertThat(context.getBean(EscalatedTransactionManagers.ESCALATED))
                    .isNotSameAs(context.getBean("transactionManager"));
        }

        @Test
        void theTwoDataSourcesReallyAreDifferentDatabases() throws Exception {
            assertThat(escalatedDataSource).isNotSameAs(hostDataSource);
            assertThat(urlOf(escalatedDataSource)).isNotEqualTo(urlOf(hostDataSource));
        }

        @Test
        void aTicketIsWrittenToEscalatedsDatabaseAndNotTheHosts() throws Exception {
            Ticket ticket = new Ticket();
            ticket.setSubject("Cannot log in");
            ticket.setBody("It says my password is wrong");
            ticket.setPriority(TicketPriority.MEDIUM);
            ticket.setTicketNumber("ESC-1");
            ticket.setRequesterName("Ada");
            ticket.setRequesterEmail("ada@example.com");

            tickets.save(ticket);

            assertThat(countTickets(escalatedDataSource)).isEqualTo(1);

            // And nothing landed in the host's. This is the whole claim: the
            // repository resolved Escalated's persistence unit, not the one the
            // host application uses for its own data.
            assertThat(countTickets(hostDataSource)).isZero();
        }

        private String urlOf(DataSource dataSource) throws Exception {
            try (Connection connection = dataSource.getConnection()) {
                return connection.getMetaData().getURL();
            }
        }

        /**
         * Rows in {@code escalated_tickets}, or zero when the table is not
         * there at all.
         *
         * <p>The table's presence is not the thing under test. This suite's own
         * {@code dev.escalated.TestApplication} is a {@code @SpringBootApplication}
         * sitting inside Escalated's package, so Escalated's component scan
         * finds it and widens the host's entity scan to match — an artefact of
         * testing a library from inside itself that no real host reproduces.
         * Where the rows go is unaffected by any of that.
         */
        private int countTickets(DataSource dataSource) throws Exception {
            try (Connection connection = dataSource.getConnection()) {
                try (ResultSet tables = connection.getMetaData()
                        .getTables(null, null, "ESCALATED_TICKETS", null)) {
                    if (!tables.next()) {
                        return 0;
                    }
                }

                try (Statement statement = connection.createStatement();
                        ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM escalated_tickets")) {
                    rows.next();

                    return rows.getInt(1);
                }
            }
        }
    }
}
