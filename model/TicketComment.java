package model;

import java.time.LocalDateTime;

public record TicketComment(
        Long id,
        long ticketId,
        long authorId,
        String authorName,
        UserRole authorRole,
        String text,
        boolean internal,
        LocalDateTime createdAt
) {
}