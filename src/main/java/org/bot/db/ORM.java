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

public class ORM implements DataBaseInterface
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
        String sql = "SELECT rowid AS id, chat_id, user_id, name_sender, text " +
                     "FROM messages WHERE chat_id = (" +
                     "SELECT latest.chat_id FROM messages AS latest " +
                     "WHERE latest.rowid = (SELECT MAX(newer.rowid) FROM messages AS newer " +
                     "WHERE newer.chat_id = latest.chat_id) " +
                     "AND latest.user_id <> ? " +
                     "ORDER BY RANDOM() LIMIT 1) ORDER BY rowid ASC";
        List<Message> messages = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setLong(1, user_id);
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
}