package repository.jdbc;

import config.DatabaseConfig;
import model.Ticket;
import model.TicketDetails;
import model.TicketPriority;
import model.TicketStatus;
import model.TicketStatusHistory;
import repository.TicketRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcTicketRepository implements TicketRepository {

    @Override
    public Ticket save(Ticket ticket) {
        String sql = """
                INSERT INTO tickets(
                    title, description, status, priority,
                    customer_id, assignee_id, category_id
                )
                VALUES (?, ?, ?::ticket_status, ?::ticket_priority, ?, ?, ?)
                RETURNING id, created_at, updated_at
                """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, ticket.getTitle());
            ps.setString(2, ticket.getDescription());
            ps.setString(3, ticket.getStatus().name());
            ps.setString(4, ticket.getPriority().name());
            ps.setLong(5, ticket.getCustomerId());
            setNullableLong(ps, 6, ticket.getAssigneeId());
            setNullableLong(ps, 7, ticket.getCategoryId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ticket.setId(rs.getLong("id"));
                    ticket.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    ticket.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
            return ticket;
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось сохранить заявку", e);
        }
    }

    @Override
    public Optional<Ticket> findById(long id) {
        String sql = "SELECT * FROM tickets WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTicket(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка поиска заявки", e);
        }
    }

    @Override
    public List<Ticket> findAll() {
        String sql = "SELECT * FROM tickets ORDER BY id";
        List<Ticket> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapTicket(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка чтения заявок", e);
        }
    }

    @Override
    public List<Ticket> findByStatus(TicketStatus status) {
        String sql = """
                SELECT * FROM tickets
                WHERE status = ?::ticket_status
                ORDER BY created_at DESC
                """;
        List<Ticket> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapTicket(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка поиска заявок по статусу", e);
        }
    }

    @Override
    public List<Ticket> findByStatusAndPriority(TicketStatus status, TicketPriority priority) {
        String sql = """
                SELECT * FROM tickets
                WHERE status = ?::ticket_status
                  AND priority = ?::ticket_priority
                ORDER BY created_at DESC
                """;
        List<Ticket> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, priority.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapTicket(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка фильтрации заявок", e);
        }
    }

    @Override
    public void update(Ticket ticket) {
        String sql = """
                UPDATE tickets
                SET title = ?,
                    description = ?,
                    priority = ?::ticket_priority,
                    category_id = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, ticket.getTitle());
            ps.setString(2, ticket.getDescription());
            ps.setString(3, ticket.getPriority().name());
            setNullableLong(ps, 4, ticket.getCategoryId());
            ps.setLong(5, ticket.getId());

            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Заявка не найдена: " + ticket.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось обновить заявку", e);
        }
    }

    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM tickets WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось удалить заявку", e);
        }
    }

    @Override
    public void changeStatus(long ticketId, TicketStatus newStatus, long changedById) {
        String updateSql = """
                UPDATE tickets
                SET status = ?::ticket_status,
                    updated_at = CURRENT_TIMESTAMP,
                    closed_at = CASE
                        WHEN ?::ticket_status = 'CLOSED' THEN CURRENT_TIMESTAMP
                        ELSE closed_at
                    END
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                TicketStatus oldStatus = lockStatus(connection, ticketId);

                if (!oldStatus.canTransitionTo(newStatus)) {
                    throw new IllegalStateException(
                            "Недопустимый переход: " + oldStatus + " -> " + newStatus);
                }

                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, newStatus.name());
                    ps.setString(2, newStatus.name());
                    ps.setLong(3, ticketId);
                    ps.executeUpdate();
                }

                insertHistory(connection, ticketId, oldStatus, newStatus, changedById);

                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка изменения статуса", e);
        }
    }

    @Override
    public void assign(long ticketId, long agentId, long changedById) {
        String updateSql = """
                UPDATE tickets
                SET assignee_id = ?,
                    status = 'IN_PROGRESS',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                TicketStatus oldStatus = lockStatus(connection, ticketId);

                if (oldStatus != TicketStatus.NEW && oldStatus != TicketStatus.IN_PROGRESS) {
                    throw new IllegalStateException(
                            "Нельзя назначить исполнителя заявке в статусе " + oldStatus);
                }

                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setLong(1, agentId);
                    ps.setLong(2, ticketId);
                    ps.executeUpdate();
                }

                if (oldStatus == TicketStatus.NEW) {
                    insertHistory(connection, ticketId, TicketStatus.NEW,
                            TicketStatus.IN_PROGRESS, changedById);
                }

                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка назначения заявки", e);
        }
    }

    @Override
    public List<TicketDetails> findAllDetails() {
        String sql = """
                SELECT t.id,
                       t.title,
                       t.status,
                       t.priority,
                       c.name AS category_name,
                       customer.name AS customer_name,
                       assignee.name AS assignee_name,
                       t.created_at
                FROM tickets t
                JOIN users customer ON customer.id = t.customer_id
                LEFT JOIN users assignee ON assignee.id = t.assignee_id
                LEFT JOIN categories c ON c.id = t.category_id
                ORDER BY t.id
                """;
        List<TicketDetails> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(new TicketDetails(
                        rs.getLong("id"),
                        rs.getString("title"),
                        TicketStatus.valueOf(rs.getString("status")),
                        TicketPriority.valueOf(rs.getString("priority")),
                        rs.getString("category_name"),
                        rs.getString("customer_name"),
                        rs.getString("assignee_name"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка JOIN-запроса заявок", e);
        }
    }

    @Override
    public List<TicketStatusHistory> findHistory(long ticketId) {
        String sql = """
                SELECT h.id,
                       h.ticket_id,
                       h.old_status,
                       h.new_status,
                       u.name AS changed_by_name,
                       h.changed_at
                FROM ticket_status_history h
                LEFT JOIN users u ON u.id = h.changed_by_id
                WHERE h.ticket_id = ?
                ORDER BY h.changed_at, h.id
                """;
        List<TicketStatusHistory> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String old = rs.getString("old_status");
                    result.add(new TicketStatusHistory(
                            rs.getLong("id"),
                            rs.getLong("ticket_id"),
                            old == null ? null : TicketStatus.valueOf(old),
                            TicketStatus.valueOf(rs.getString("new_status")),
                            rs.getString("changed_by_name"),
                            rs.getTimestamp("changed_at").toLocalDateTime()
                    ));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка чтения истории статусов", e);
        }
    }

    private TicketStatus lockStatus(Connection connection, long ticketId) throws SQLException {
        String sql = "SELECT status FROM tickets WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Заявка не найдена: " + ticketId);
                }
                return TicketStatus.valueOf(rs.getString("status"));
            }
        }
    }

    private void insertHistory(Connection connection, long ticketId,
                               TicketStatus oldStatus, TicketStatus newStatus,
                               long changedById) throws SQLException {
        String sql = """
                INSERT INTO ticket_status_history(
                    ticket_id, old_status, new_status, changed_by_id
                )
                VALUES (?, ?::ticket_status, ?::ticket_status, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, ticketId);
            ps.setString(2, oldStatus.name());
            ps.setString(3, newStatus.name());
            ps.setLong(4, changedById);
            ps.executeUpdate();
        }
    }

    private Ticket mapTicket(ResultSet rs) throws SQLException {
        Ticket ticket = new Ticket();
        ticket.setId(rs.getLong("id"));
        ticket.setTitle(rs.getString("title"));
        ticket.setDescription(rs.getString("description"));
        ticket.setStatus(TicketStatus.valueOf(rs.getString("status")));
        ticket.setPriority(TicketPriority.valueOf(rs.getString("priority")));
        ticket.setCustomerId(rs.getLong("customer_id"));

        long assigneeId = rs.getLong("assignee_id");
        ticket.setAssigneeId(rs.wasNull() ? null : assigneeId);

        long categoryId = rs.getLong("category_id");
        ticket.setCategoryId(rs.wasNull() ? null : categoryId);

        ticket.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        ticket.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

        Timestamp closed = rs.getTimestamp("closed_at");
        ticket.setClosedAt(closed == null ? null : closed.toLocalDateTime());
        return ticket;
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }
}