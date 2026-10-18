package repository;

import model.Ticket;
import model.TicketDetails;
import model.TicketPriority;
import model.TicketStatus;
import model.TicketStatusHistory;

import java.util.List;
import java.util.Optional;

public interface TicketRepository {
    Ticket save(Ticket ticket);

    Optional<Ticket> findById(long id);

    List<Ticket> findAll();

    List<Ticket> findByStatus(TicketStatus status);

    List<Ticket> findByStatusAndPriority(TicketStatus status, TicketPriority priority);

    void update(Ticket ticket);

    boolean deleteById(long id);

    void changeStatus(long ticketId, TicketStatus newStatus, long changedById);

    void assign(long ticketId, long agentId, long changedById);

    List<TicketDetails> findAllDetails();

    List<TicketStatusHistory> findHistory(long ticketId);
}