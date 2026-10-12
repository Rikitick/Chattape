package org.bot.model;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChatRepository {
    private static final String DEFAULT_URL = "jdbc:h2:file:./chattape;AUTO_SERVER=TRUE";
    private final Connection connection;
    private final Random random = new Random();

    public ChatRepository()
    {
        this(DEFAULT_URL);
    }

    public ChatRepository(String url)
    {
        try
        {
            Class.forName("org.h2.Driver");
            this.connection = DriverManager.getConnection(url, "sa", "");
            initializeSchema();
            ensureSeedData();
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

    private void ensureSeedData()
    {
        if (getActiveCount() > 0)
        {
            return;
        }

        ChatThread demo1 = new ChatThread();
        demo1.addMessage("Незнакомец #42", "Привет! Какая у тебя погода?");
        saveThread(demo1);

        ChatThread demo2 = new ChatThread();
        demo2.addMessage("Незнакомец #11", "Какой твой любимый фильм?");
        demo2.addMessage("Незнакомец #89", "Интерстеллар! А твой?");
        saveThread(demo2);
    }

    public void saveThread(ChatThread thread)
    {
        if (thread == null || thread.getMessages().isEmpty())
        {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chat_messages (id, message_order, sender_alias, message_text) VALUES (?, ?, ?, ?)")) {
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

    public ChatThread getRandomThread()
    {
        List<String> threadIds = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT id FROM chat_messages ORDER BY id");
             ResultSet resultSet = statement.executeQuery()) {
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

        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM chat_messages WHERE id = ?"))
        {
            statement.setString(1, chosenThreadId);
            statement.executeUpdate();
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось удалить выбранный чат из очереди", e);
        }

        return ChatThread.fromMessages(chosenThreadId, messages);
    }

    public int getActiveCount()
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(DISTINCT id) FROM chat_messages");
             ResultSet resultSet = statement.executeQuery())
             {
            if (resultSet.next())
            {
                return resultSet.getInt(1);
            }
            return 0;
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Не удалось посчитать активные чаты", e);
        }
    }
}