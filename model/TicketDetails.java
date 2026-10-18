package model;

import java.time.LocalDateTime;

public record TicketDetails(
        long id,
        String title,
        TicketStatus status,
        TicketPriority priority,
        String categoryName,
        String customerName,
        String assigneeName,
        LocalDateTime createdAt
) {
}