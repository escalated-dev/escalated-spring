package dev.escalated.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TicketVisibilityFilter extends OncePerRequestFilter {
    private final TicketAccess access;

    public TicketVisibilityFilter(TicketAccess access) {
        this.access = access;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (TicketAccess.Scope ignored =
                access.open(SecurityContextHolder.getContext().getAuthentication())) {
            chain.doFilter(request, response);
        }
    }
}
