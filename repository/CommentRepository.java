package repository;

import model.TicketComment;

import java.util.List;

public interface CommentRepository {
    TicketComment add(TicketComment comment);

    List<TicketComment> findByTicketId(long ticketId);

    List<TicketComment> findExternalByTicketId(long ticketId);
}