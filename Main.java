import model.Category;
import model.Ticket;
import model.TicketComment;
import model.TicketDetails;
import model.TicketPriority;
import model.TicketStatus;
import model.TicketStatusHistory;
import model.User;
import repository.CategoryRepository;
import repository.CommentRepository;
import repository.TicketRepository;
import repository.UserRepository;
import repository.jdbc.JdbcCategoryRepository;
import repository.jdbc.JdbcCommentRepository;
import repository.jdbc.JdbcTicketRepository;
import repository.jdbc.JdbcUserRepository;
import service.TicketService;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        TicketRepository ticketRepo = new JdbcTicketRepository();
        UserRepository userRepo = new JdbcUserRepository();
        CategoryRepository categoryRepo = new JdbcCategoryRepository();
        CommentRepository commentRepo = new JdbcCommentRepository();
        TicketService service =
                new TicketService(ticketRepo, userRepo, categoryRepo, commentRepo);

        try {
            List<Ticket> existing = ticketRepo.findAll();
            System.out.println("=== Заявок в БД на момент запуска: " + existing.size() + " ===");
            for (Ticket t : existing) {
                System.out.println("  " + t);
            }

            System.out.println("\n[1] Подключение к PostgreSQL выполняется в репозиториях");

            User customer = userRepo.findByEmail("anna@example.org")
                    .orElseThrow(() -> new IllegalStateException("Клиент не найден"));
            System.out.println("[2] Клиент: " + customer);

            User agent = userRepo.findByEmail("ivan.support@example.org")
                    .orElseThrow(() -> new IllegalStateException("Сотрудник не найден"));
            User admin = userRepo.findByEmail("admin@example.org")
                    .orElseThrow(() -> new IllegalStateException("Админ не найден"));

            System.out.println("[3] Категории:");
            List<Category> categories = categoryRepo.findAll();
            for (Category c : categories) {
                System.out.println("  " + c.id() + ". " + c.name()
                        + " (приоритет по умолчанию: " + c.defaultPriority() + ")");
            }

            Ticket ticket = service.createTicket(
                    "Не работает VPN",
                    "После обновления клиента VPN не подключается к корпоративной сети",
                    TicketPriority.HIGH,
                    customer.getId(),
                    categories.get(0).id()
            );

            long ticketId = ticket.getId();
            System.out.println("[4-5] Создана заявка, id из PostgreSQL = " + ticketId);

            Ticket loaded = ticketRepo.findById(ticketId).orElseThrow();
            System.out.println("[6] findById: " + loaded);

            service.assignTicket(ticketId, agent.getId(), admin.getId());
            System.out.println("[7] Назначен: " + agent.getName()
                    + ", статус теперь " + service.getTicket(ticketId).getStatus());

            service.addComment(ticketId, customer.getId(),
                    "VPN перестал работать после вчерашнего обновления", false);
            System.out.println("[8] Добавлен комментарий клиента");

            service.changeStatus(ticketId, TicketStatus.RESOLVED, agent.getId());
            System.out.println("[9] Статус: IN_PROGRESS -> RESOLVED");

            service.addComment(ticketId, agent.getId(),
                    "Переустановлен клиент VPN, профиль пересоздан", true);
            System.out.println("[10] Добавлена внутренняя заметка сотрудника");

            try {
                service.changeStatus(ticketId, TicketStatus.NEW, agent.getId());
            } catch (IllegalStateException e) {
                System.out.println("[проверка] Отклонено: " + e.getMessage());
            }

            service.changeStatus(ticketId, TicketStatus.CLOSED, agent.getId());
            System.out.println("[11] Статус: RESOLVED -> CLOSED");

            System.out.println("[12] История статусов заявки " + ticketId + ":");
            for (TicketStatusHistory h : service.getHistory(ticketId)) {
                System.out.println("  " + h.changedAt() + "  "
                        + h.oldStatus() + " -> " + h.newStatus()
                        + "  (" + h.changedByName() + ")");
            }

            System.out.println("[13] Заявки (JOIN):");
            for (TicketDetails d : service.getAllDetails()) {
                System.out.printf("  #%d | %s | %s | %s | клиент: %s | исполнитель: %s | категория: %s%n",
                        d.id(), d.title(), d.status(), d.priority(),
                        d.customerName(),
                        d.assigneeName() == null ? "не назначен" : d.assigneeName(),
                        d.categoryName() == null ? "-" : d.categoryName());
            }

            System.out.println("Комментарии глазами клиента (только внешние):");
            for (TicketComment c : service.getComments(ticketId, false)) {
                System.out.println("  " + c.authorName() + ": " + c.text());
            }
            System.out.println("Комментарии глазами сотрудника (все):");
            for (TicketComment c : service.getComments(ticketId, true)) {
                System.out.println("  " + c.authorName()
                        + (c.internal() ? " [внутренний]" : "") + ": " + c.text());
            }

            System.out.println("\n[14] Запусти программу ещё раз: заявка " + ticketId
                    + " будет в списке в начале вывода.");

        } catch (RuntimeException e) {
            System.out.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}