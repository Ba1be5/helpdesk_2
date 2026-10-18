package repository.jdbc;

import config.DatabaseConfig;
import model.TicketComment;
import model.UserRole;
import repository.CommentRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcCommentRepository implements CommentRepository {

    @Override
    public TicketComment add(TicketComment comment) {
        String sql = """
                INSERT INTO ticket_comments(ticket_id, author_id, text, internal)
                VALUES (?, ?, ?, ?)
                RETURNING id, created_at
                """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, comment.ticketId());
            ps.setLong(2, comment.authorId());
            ps.setString(3, comment.text());
            ps.setBoolean(4, comment.internal());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new TicketComment(
                        rs.getLong("id"),
                        comment.ticketId(),
                        comment.authorId(),
                        null,
                        null,
                        comment.text(),
                        comment.internal(),
                        rs.getTimestamp("created_at").toLocalDateTime()
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось добавить комментарий", e);
        }
    }

    @Override
    public List<TicketComment> findByTicketId(long ticketId) {
        return find(ticketId, false);
    }

    @Override
    public List<TicketComment> findExternalByTicketId(long ticketId) {
        return find(ticketId, true);
    }

    private List<TicketComment> find(long ticketId, boolean onlyExternal) {
        String sql = """
                SELECT c.id,
                       c.ticket_id,
                       c.author_id,
                       c.text,
                       c.internal,
                       c.created_at,
                       u.name AS author_name,
                       u.role AS author_role
                FROM ticket_comments c
                JOIN users u ON u.id = c.author_id
                WHERE c.ticket_id = ?
                """
                + (onlyExternal ? " AND c.internal = FALSE " : " ")
                + "ORDER BY c.created_at, c.id";

        List<TicketComment> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TicketComment(
                            rs.getLong("id"),
                            rs.getLong("ticket_id"),
                            rs.getLong("author_id"),
                            rs.getString("author_name"),
                            UserRole.valueOf(rs.getString("author_role")),
                            rs.getString("text"),
                            rs.getBoolean("internal"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка чтения комментариев", e);
        }
    }
}