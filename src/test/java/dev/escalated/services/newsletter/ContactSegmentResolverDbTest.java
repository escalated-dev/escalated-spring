package dev.escalated.services.newsletter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.NewsletterList;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.NewsletterListMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({ContactSegmentResolver.class, ContactSegmentResolverDbTest.JacksonConfig.class})
@TestPropertySource(
        properties = {
            "escalated.newsletters.enabled=true",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.flyway.enabled=false",
        })
class ContactSegmentResolverDbTest {

    @Autowired private ContactRepository contacts;
    @Autowired private ContactSegmentResolver resolver;

    @MockitoBean private NewsletterListMemberRepository members;

    @BeforeEach
    void seed() {
        contacts.deleteAll();
        Contact alice = new Contact();
        alice.setEmail("alice@example.com");
        alice.setName("Alice");
        contacts.save(alice);

        Contact bob = new Contact();
        bob.setEmail("bob@example.com");
        bob.setName("Bob");
        contacts.save(bob);

        Contact carol = new Contact();
        carol.setEmail("carol@example.com");
        carol.setName("Carol Acme");
        contacts.save(carol);
    }

    @Test
    void resolve_filtersByEmailInDatabase() {
        NewsletterList list = new NewsletterList();
        list.setKind("dynamic");
        list.setFilterJson("""
                {"rules":[{"field":"email","op":"=","value":"bob@example.com"}]}""");

        assertThat(resolver.resolve(list)).hasSize(1);
        Contact bob = contacts.findByEmail("bob@example.com").orElseThrow();
        assertThat(resolver.resolve(list)).containsExactly(bob.getId());
    }

    @Test
    void countMatches_filtersByNameContainsInDatabase() {
        int count = resolver.countMatches("""
                {"rules":[{"field":"name","op":"contains","value":"Acme"}]}""");
        assertThat(count).isEqualTo(1);
    }

    @TestConfiguration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
