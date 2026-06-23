package dev.escalated.services.newsletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.NewsletterList;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.NewsletterListMemberRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ContactSegmentResolverTest {

    @Mock private ContactRepository contactRepository;
    @Mock private NewsletterListMemberRepository memberRepository;

    private ContactSegmentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ContactSegmentResolver(contactRepository, memberRepository, new ObjectMapper());
    }

    @Test
    void isAllowedField_rejectsUnknownColumn() {
        assertThat(ContactSegmentResolver.isAllowedField("email")).isTrue();
        assertThat(ContactSegmentResolver.isAllowedField("metadata.plan")).isTrue();
        assertThat(ContactSegmentResolver.isAllowedField("password")).isFalse();
        assertThat(ContactSegmentResolver.isAllowedField("'; DROP TABLE contacts;--")).isFalse();
    }

    @Test
    void parseRules_skipsUnknownField() {
        String filter = """
                {"rules":[{"field":"password","op":"=","value":"secret"}]}""";
        assertThat(resolver.parseRules(filter)).isEmpty();
    }

    @Test
    void resolveDynamicList_usesSpecificationNotFindAll() {
        Contact alice = contact(1L, "alice@example.com");
        when(contactRepository.findAll(any(Specification.class))).thenReturn(List.of(alice));

        NewsletterList list = new NewsletterList();
        list.setKind("dynamic");
        list.setFilterJson("""
                {"rules":[{"field":"email","op":"=","value":"alice@example.com"}]}""");

        assertThat(resolver.resolve(list)).containsExactly(1L);
        verify(contactRepository, never()).findAll();
        ArgumentCaptor<Specification<Contact>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(contactRepository).findAll(specCaptor.capture());
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    void countMatches_usesCountNotFindAll() {
        when(contactRepository.count(any(Specification.class))).thenReturn(2L);

        int count = resolver.countMatches("""
                {"rules":[{"field":"name","op":"contains","value":"acme"}]}""");

        assertThat(count).isEqualTo(2);
        verify(contactRepository, never()).findAll();
        verify(contactRepository).count(any(Specification.class));
    }

    private static Contact contact(long id, String email) {
        Contact contact = new Contact();
        contact.setId(id);
        contact.setEmail(email);
        return contact;
    }
}
