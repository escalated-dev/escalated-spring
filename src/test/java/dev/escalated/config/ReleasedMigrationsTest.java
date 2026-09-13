package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * A released migration never changes.
 *
 * <p>Flyway records each script's checksum when it runs and refuses to start
 * when the script on the classpath no longer matches. Editing a released
 * migration therefore breaks every install that already ran it, however small
 * the edit. A schema change is a new migration.
 *
 * <p>These are the MySQL scripts exactly as 0.1.0 shipped them in
 * {@code db/migration}, hashed byte for byte. Moving them to
 * {@code db/escalated/mysql} kept their bytes, and with them their checksums.
 */
class ReleasedMigrationsTest {

    private static final Map<String, String> MYSQL_AS_RELEASED = Map.of(
            "V1__create_escalated_tables.sql", "6857fa51e0bb785dbf3e2813b4ddd59209612c0c1a93897055468bbc9c40d116",
            "V2__create_escalated_contacts.sql", "8c53aae32bb489252261cae157e3e596d37e13f13f79fef428ff5f007d006c75",
            "V3__add_user_role_flags.sql", "fd7c65c4248fb5c7b5c13725166d2e8a2687716dfe426d369c0fc12bf4b6ac4a",
            "V4__create_escalated_agent_skills.sql", "f98dcec1c205f1316263078a75400fbe51fc0daf553c404934da8736e0ab3fef",
            "V5__create_escalated_skill_routing_tags.sql", "5151a2add80a9e0e2149465e1209d18a6bad9f7f39f188967ca4d9b0f337e538",
            "V6__create_escalated_skill_routing_departments.sql",
            "50e28c43ab2c9b1fe5314b756ec5d90d86ae1e34eb25c69f487084892bdd64b8",
            "V7__create_escalated_ticket_subjects.sql", "568d86cc9e01a54bb7647fc9594979e277556cd42fa57a93a0756b2cdce1fcee",
            "V8__create_escalated_newsletters.sql", "069ce77f0861929e2320f46a64901cd166aa7d714430f1f2901789c9b3dad448",
            "V9__newsletter_next_attempt_and_permissions.sql",
            "c010cae8ba5d3978386eb50775d8c4efa266993ee6560eaa06af233b192a2f77",
            "V10__create_escalated_ticket_followers.sql", "e82b2ed8203bde0603d6054b2877f2c41606bc3d831f523247d81edc5680f183");

    @Test
    void theMySqlMigrationsAreTheOnesZeroPointOneShipped() throws Exception {
        for (Map.Entry<String, String> released : MYSQL_AS_RELEASED.entrySet()) {
            assertThat(sha256("db/escalated/mysql/" + released.getKey()))
                    .as("%s must never change once released; add a new migration instead", released.getKey())
                    .isEqualTo(released.getValue());
        }
    }

    @Test
    void theHostsMigrationLocationIsLeftEmpty() {
        // Anything here is read by the host's own Flyway, into the host's own
        // history, where its version numbers collide with the host's.
        assertThat(new ClassPathResource("db/migration/V1__create_escalated_tables.sql").exists()).isFalse();
    }

    @Test
    void everyMigrationHasAnEditionForEachEngine() {
        for (String script : MYSQL_AS_RELEASED.keySet()) {
            assertThat(new ClassPathResource("db/escalated/postgresql/" + script).exists())
                    .as("PostgreSQL edition of %s", script)
                    .isTrue();
        }
        assertThat(new ClassPathResource("db/escalated/mysql/V11__create_workflow_and_chat_tables.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/escalated/postgresql/V11__create_workflow_and_chat_tables.sql").exists()).isTrue();
    }

    private static String sha256(String resource) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            digest.update(in.readAllBytes());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
