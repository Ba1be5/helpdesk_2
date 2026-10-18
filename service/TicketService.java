package service;

import model.Category;
import model.Ticket;
import model.TicketComment;
import model.TicketDetails;
import model.TicketPriority;
import model.TicketStatus;
import model.TicketStatusHistory;
import model.User;
import model.UserRole;
import repository.CategoryRepository;
import repository.CommentRepository;
import repository.TicketRepository;
import repository.UserRepository;

import java.util.List;

public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CommentRepository commentRepository;

    public TicketService(TicketRepository ticketRepository,
                         UserRepository userRepository,
                         CategoryRepository categoryRepository,
                         CommentRepository commentRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.commentRepository = commentRepository;
    }

    public Ticket createTicket(String title,
                               String description,
                               TicketPriority priority,
                               long customerId,
                               Long categoryId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Пустая тема заявки");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Пустое описание заявки");
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Клиент не найден: " + customerId));
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new IllegalArgumentException("Заявку может создать только клиент");
        }

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Категория не найдена: " + categoryId));
            if (!category.active()) {
                throw new IllegalStateException(
                        "Категория неактивна: " + category.name());
            }
        }

        Ticket ticket = new Ticket();
        ticket.setTitle(title.trim());
        ticket.setDescription(description.trim());
        ticket.setStatus(TicketStatus.NEW);
        ticket.setPriority(priority);
        ticket.setCustomerId(customerId);
        ticket.setCategoryId(categoryId);
        return ticketRepository.save(ticket);
    }

    public void assignTicket(long ticketId, long agentId, long changedById) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не найден: " + agentId));
        if (agent.getRole() != UserRole.SUPPORT_AGENT) {
            throw new IllegalArgumentException(
                    "Назначить можно только сотрудника поддержки (SUPPORT_AGENT)");
        }
        ticketRepository.assign(ticketId, agentId, changedById);
    }

    public void changeStatus(long ticketId, TicketStatus newStatus, long changedById) {
        userRepository.findById(changedById)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не найден: " + changedById));
        ticketRepository.changeStatus(ticketId, newStatus, changedById);
    }

    public TicketComment addComment(long ticketId, long authorId,
                                    String text, boolean internal) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Пустой комментарий");
        }
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Заявка не найдена: " + ticketId));
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Автор не найден: " + authorId));
        if (internal && author.getRole() == UserRole.CUSTOMER) {
            throw new IllegalStateException(
                    "Клиент не может оставлять внутренние заметки");
        }
        return commentRepository.add(new TicketComment(
                null, ticketId, authorId, null, null, text.trim(), internal, null));
    }

    public Ticket getTicket(long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Заявка не найдена: " + id));
    }

    public List<Ticket> findByStatusAndPriority(TicketStatus status, TicketPriority priority) {
        return ticketRepository.findByStatusAndPriority(status, priority);
    }

    public List<TicketDetails> getAllDetails() {
        return ticketRepository.findAllDetails();
    }

    public List<TicketStatusHistory> getHistory(long ticketId) {
        return ticketRepository.findHistory(ticketId);
    }

    public List<TicketComment> getComments(long ticketId, boolean includeInternal) {
        return includeInternal
                ? commentRepository.findByTicketId(ticketId)
                : commentRepository.findExternalByTicketId(ticketId);
    }
}