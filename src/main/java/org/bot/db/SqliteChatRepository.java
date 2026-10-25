package org.bot.db;

import org.bot.model.ChatMessage;
import org.bot.model.ChatRepository;
import org.bot.model.ChatThread;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SqliteChatRepository implements ChatRepository
{
    private static final String DEFAULT_URL = "jdbc:sqlite:./chattape.db";
    private final Connection connection;
    private final Random random = new Random();

    public SqliteChatRepository()
    {
        this(DEFAULT_URL);
    }

    public SqliteChatRepository(String url)
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
                    "CREATE TABLE IF NOT EXISTS chat_messages (" +
                            "id VARCHAR(255) NOT NULL, " +
                            "message_order INT NOT NULL, " +
                            "sender_alias VARCHAR(255) NOT NULL, " +
                            "message_text TEXT NOT NULL, " +
                            "PRIMARY KEY (id, message_order))"
            );
        }
    }

    @Override
    public synchronized void saveThread(ChatThread thread)
    {
        if (thread == null || thread.getMessages().isEmpty())
        {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chat_messages (id, message_order, sender_alias, message_text) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(id, message_order) DO UPDATE SET " +
                        "sender_alias = excluded.sender_alias, message_text = excluded.message_text"))
        {
            List<ChatMessage> messages = thread.getMessages();
            for (int i = 0; i < messages.size(); i++)
            {
                ChatMessage message = messages.get(i);
                statement.setString(1, thread.getId());
                statement.setInt(2, i);
                statement.setString(3, message.senderAlias());
                statement.setString(4, message.text());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось сохранить цепочку чата", e);
        }
    }

    @Override
    public synchronized ChatThread getRandomThread()
    {
        List<String> threadIds = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement("SELECT DISTINCT id FROM chat_messages ORDER BY id");
             ResultSet resultSet = statement.executeQuery())
        {
            while (resultSet.next())
            {
                threadIds.add(resultSet.getString("id"));
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось загрузить случайный чат", e);
        }

        if (threadIds.isEmpty())
        {
            return null;
        }

        String chosenThreadId = threadIds.get(random.nextInt(threadIds.size()));
        List<ChatMessage> messages = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sender_alias, message_text FROM chat_messages WHERE id = ? ORDER BY message_order"))
        {
            statement.setString(1, chosenThreadId);
            try (ResultSet resultSet = statement.executeQuery())
            {
                while (resultSet.next())
                {
                    messages.add(new ChatMessage(
                            resultSet.getString("sender_alias"),
                            resultSet.getString("message_text")
                    ));
                }
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось прочитать историю чата", e);
        }

        return ChatThread.fromMessages(chosenThreadId, messages);
    }

    @Override
    public synchronized int getActiveCount()
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(DISTINCT id) FROM chat_messages");
             ResultSet resultSet = statement.executeQuery())
        {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось посчитать активные чаты", e);
        }
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
            throw new IllegalStateException("Не удалось закрыть подключение к базе данных чатов", e);
        }
    }
}