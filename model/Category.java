package model;

public record Category(
        long id,
        String name,
        String description,
        TicketPriority defaultPriority,
        boolean active
) {
}