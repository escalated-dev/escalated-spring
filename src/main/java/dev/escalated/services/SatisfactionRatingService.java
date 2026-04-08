package dev.escalated.services;

import dev.escalated.models.SatisfactionRating;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.SatisfactionRatingRepository;
import dev.escalated.repositories.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SatisfactionRatingService {

    private final SatisfactionRatingRepository ratingRepository;
    private final TicketRepository ticketRepository;

    public SatisfactionRatingService(SatisfactionRatingRepository ratingRepository,
                                     TicketRepository ticketRepository) {
        this.ratingRepository = ratingRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public SatisfactionRating createRatingRequest(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));

        SatisfactionRating rating = new SatisfactionRating();
        rating.setTicket(ticket);
        rating.setRaterEmail(ticket.getRequesterEmail());
        rating.setRaterName(ticket.getRequesterName());
        rating.setAccessToken(UUID.randomUUID().toString());
        return ratingRepository.save(rating);
    }

    @Transactional
    public SatisfactionRating submitRating(String accessToken, int score, String comment) {
        SatisfactionRating rating = ratingRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> new EntityNotFoundException("Rating not found for token"));

        rating.setRating(score);
        rating.setComment(comment);
        return ratingRepository.save(rating);
    }

    @Transactional(readOnly = true)
    public List<SatisfactionRating> findByTicket(Long ticketId) {
        return ratingRepository.findByTicketId(ticketId);
    }

    @Transactional(readOnly = true)
    public Double getAverageRating() {
        return ratingRepository.getAverageRating();
    }
}
