package dev.escalated.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Macro;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.MacroRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroServiceTest {

    @Mock
    private MacroRepository macroRepository;
    @Mock
    private TicketService ticketService;

    private MacroService macroService;

    @BeforeEach
    void setUp() {
        macroService = new MacroService(macroRepository, ticketService, new ObjectMapper());
    }

    @Test
    void create_shouldSaveMacro() {
        when(macroRepository.save(any(Macro.class))).thenAnswer(inv -> {
            Macro m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        Macro result = macroService.create("Close and Thank",
                "Closes ticket and adds thank you note",
                "[{\"type\":\"set_status\",\"value\":\"CLOSED\"}]",
                true, 1L);

        assertNotNull(result);
        assertEquals("Close and Thank", result.getName());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        when(macroRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> macroService.findById(999L));
    }

    @Test
    void applyMacro_shouldExecuteActions() {
        Macro macro = new Macro();
        macro.setId(1L);
        macro.setActions("[{\"type\":\"set_status\",\"value\":\"CLOSED\"}]");

        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus(TicketStatus.OPEN);

        when(macroRepository.findById(1L)).thenReturn(Optional.of(macro));
        when(ticketService.findById(1L)).thenReturn(ticket);
        when(ticketService.changeStatus(eq(1L), eq(TicketStatus.CLOSED), any())).thenReturn(ticket);

        macroService.applyMacro(1L, 1L, "agent@test.com");

        verify(ticketService).changeStatus(eq(1L), eq(TicketStatus.CLOSED), any());
    }
}
