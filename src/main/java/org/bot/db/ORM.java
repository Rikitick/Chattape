package org.bot.db;

import org.bot.model.Message;
import org.bot.model.interfaces.DataBaseInterface;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ORM implements DataBaseInterface, AutoCloseable
{
    private static final String DEFAULT_URL = "jdbc:sqlite:./chattape.db";
    private final Connection connection;

    public ORM()
    {
        this(DEFAULT_URL);
    }

    public ORM(String url)
    {
        try
        {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection(url);
            initializeSchema();
        }
        catch (ClassNotFoundException | SQLException e)
        {
            throw new IllegalStateException("Не удалось подключиться к базе данных чатов", e);
        }
    }

    private void initializeSchema() throws SQLException
    {
        try (Statement statement = connection.createStatement())
        {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS messages (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "chat_id INT NOT NULL DEFAULT 0, " +
                            "user_id BIGINT NOT NULL, " +
                            "name_sender VARCHAR(255) NOT NULL, " +
                            "text TEXT NOT NULL)"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS chats (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT)"
            );
            statement.executeUpdate(
                    "INSERT OR IGNORE INTO chats (id) " +
                            "SELECT DISTINCT chat_id FROM messages WHERE chat_id > 0"
            );
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id)");
            // индекс по user_id пригодится для getUserChats / getRandomChat
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_user_id ON messages(user_id)");
        }
    }

    @Override
    public synchronized int createChat()
    {
        try (Statement statement = connection.createStatement())
        {
            statement.executeUpdate("INSERT INTO chats DEFAULT VALUES");
            try (ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()"))
            {
                if (resultSet.next())
                {
                    return resultSet.getInt(1);
                }
            }
            throw new SQLException("Не удалось получить идентификатор нового чата");
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось создать чат", e);
        }
    }

    @Override
    public List<Message> getRandomChat(long user_id)
    {
        // Добавлено: latest.chat_id NOT IN (чаты, где user_id уже писал)
        String sql = "SELECT rowid AS id, chat_id, user_id, name_sender, text " +
                "FROM messages WHERE chat_id = (" +
                "SELECT latest.chat_id FROM messages AS latest " +
                "WHERE latest.rowid = (SELECT MAX(newer.rowid) FROM messages AS newer " +
                "WHERE newer.chat_id = latest.chat_id) " +
                "AND latest.user_id <> ? " +
                "AND latest.chat_id NOT IN (SELECT chat_id FROM messages WHERE user_id = ?) " +
                "ORDER BY RANDOM() LIMIT 1) ORDER BY rowid ASC";
        List<Message> messages = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setLong(1, user_id);
            statement.setLong(2, user_id);
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next()) {
                    messages.add(new Message(
                            rs.getInt("id"),
                            rs.getInt("chat_id"),
                            rs.getLong("user_id"),
                            rs.getString("name_sender"),
                            rs.getString("text")
                    ));
                }
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось получить случайный чат", e);
        }

        return messages;
    }

    @Override
    public synchronized void addMessage(Message message)
    {
        String sql = "INSERT INTO messages (chat_id, user_id, name_sender, text) " +
                "VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql))
        {
            pstmt.setInt(1, message.getChatId());
            pstmt.setLong(2, message.getUserId());
            pstmt.setString(3, message.getNameSender());
            pstmt.setString(4, message.getText());

            pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось сохранить сообщение в базе данных", e);
        }
    }

    @Override
    public int getChatSize(int chatId)
    {
        String sql = "SELECT COUNT(*) FROM messages WHERE chat_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setInt(1, chatId);
            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось получить размер чата", e);
        }
    }

    @Override
    public List<Integer> getUserChats(long userId)
    {
        String sql = "SELECT DISTINCT chat_id FROM messages WHERE user_id = ? ORDER BY chat_id";
        List<Integer> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next()) result.add(rs.getInt(1));
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось получить чаты пользователя", e);
        }
        return result;
    }

    @Override
    public List<Message> getChatById(int chatId)
    {
        String sql = "SELECT rowid AS id, chat_id, user_id, name_sender, text " +
                "FROM messages WHERE chat_id = ? ORDER BY rowid ASC";
        List<Message> messages = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setInt(1, chatId);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    messages.add(new Message(
                            rs.getInt("id"),
                            rs.getInt("chat_id"),
                            rs.getLong("user_id"),
                            rs.getString("name_sender"),
                            rs.getString("text")
                    ));
                }
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось получить чат по id", e);
        }
        return messages;
    }

    @Override
    public synchronized void close()
    {
        try
        {
            connection.close();
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось закрыть соединение с базой данных", e);
        }
    }
}