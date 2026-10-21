package org.bot.repository;

import org.bot.model.ChatMessage;
import org.bot.model.ChatThread;

import java.sql.*;
import java.util.*;

public class H2ChatRepository implements ChatRepository {
    private static final String DEFAULT_URL = "jdbc:h2:file:./chattape;AUTO_SERVER=TRUE";
    private final String dbUrl;
    private final Random random = new Random();

    public H2ChatRepository() {
        this(DEFAULT_URL);
    }

    public H2ChatRepository(String url) {
        this.dbUrl = url;
        try {
            Class.forName("org.h2.Driver");
            initializeSchema();
            ensureSeedData();
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("Не удалось подключиться к базе данных чатов", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, "sa", "");
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
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

    private void ensureSeedData() {
        if (getActiveCount() > 0) return;

        ChatThread demo1 = new ChatThread();
        demo1.addMessage("Незнакомец #42", "Привет! Какая у тебя погода?");
        saveThread(demo1);

        ChatThread demo2 = new ChatThread();
        demo2.addMessage("Незнакомец #11", "Какой твой любимый фильм?");
        demo2.addMessage("Незнакомец #89", "Интерстеллар! А твой?");
        saveThread(demo2);
    }

    @Override
    public void saveThread(ChatThread thread) {
        if (thread == null || thread.getMessages().isEmpty()) return;

        String sql = "MERGE INTO chat_messages (id, message_order, sender_alias, message_text) KEY (id, message_order) VALUES (?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            List<ChatMessage> messages = thread.getMessages();
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                statement.setString(1, thread.getId());
                statement.setInt(2, i);
                statement.setString(3, message.senderAlias());
                statement.setString(4, message.text());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось сохранить цепочку чата", e);
        }
    }

    @Override
    public ChatThread getRandomThread() {
        List<ChatThread> all = getAllThreads();
        if (all.isEmpty()) return null;
        return all.get(random.nextInt(all.size()));
    }

    @Override
    public ChatThread findAvailableThreadForUser(String userId) {
        return getAllThreads().stream()
                .filter(t -> t.canUserPost(userId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<ChatThread> findThreadsByParticipant(String userId) {
        List<ChatThread> result = new ArrayList<>();
        for (ChatThread thread : getAllThreads()) {
            boolean participated = thread.getMessages().stream()
                    .anyMatch(m -> m.senderAlias().equalsIgnoreCase(userId));
            if (participated) {
                result.add(thread);
            }
        }
        return result;
    }

    @Override
    public int getActiveCount() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(DISTINCT id) FROM chat_messages");
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) return resultSet.getInt(1);
            return 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось посчитать активные чаты", e);
        }
    }

    private List<ChatThread> getAllThreads() {
        Map<String, List<ChatMessage>> map = new LinkedHashMap<>();
        String sql = "SELECT id, sender_alias, message_text FROM chat_messages ORDER BY id, message_order";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                String sender = rs.getString("sender_alias");
                String text = rs.getString("message_text");
                map.computeIfAbsent(id, k -> new ArrayList<>()).add(new ChatMessage(sender, text));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось прочитать историю чатов", e);
        }

        List<ChatThread> threads = new ArrayList<>();
        map.forEach((id, msgs) -> threads.add(ChatThread.fromMessages(id, msgs)));
        return threads;
    }
}