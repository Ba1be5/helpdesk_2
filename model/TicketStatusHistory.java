package model;

import java.time.LocalDateTime;

public record TicketStatusHistory(
        long id,
        long ticketId,
        TicketStatus oldStatus,
        TicketStatus newStatus,
        String changedByName,
        LocalDateTime changedAt
) {
}