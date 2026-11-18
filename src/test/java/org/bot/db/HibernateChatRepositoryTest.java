package org.bot.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bot.model.MessageEntity;
import org.bot.repository.HibernateChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Тесты для HibernateChatRepository — слой ORM на Hibernate/JPA.
 * Запускаются на нескольких бэкендах SQLite: in-memory, временный файл, shared-cache memory.
 * Заменяет старый ORMTest.
 */
class HibernateChatRepositoryTest {

    enum Backend {
        IN_MEMORY {
            @Override
            String url(Path dir) {
                return "jdbc:sqlite::memory:";
            }
        },
        TEMP_FILE {
            @Override
            String url(Path dir) {
                return "jdbc:sqlite:" + dir.resolve("chattape.db");
            }
        },
        SHARED_CACHE_MEMORY {
            @Override
            String url(Path dir) {
                return "jdbc:sqlite:file:chattape-" + UUID.randomUUID() + "?mode=memory&cache=shared";
            }
        };

        abstract String url(Path dir);
    }

    private static MessageEntity message(int chatId, long userId, String name, String text) {
        return new MessageEntity(chatId, userId, name, text);
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void emptyDatabaseHasNoChatsToOffer(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            assertTrue(repo.getRandomChat(1).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void createChatReturnsConsecutiveIds(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int first = repo.createChat();
            int second = repo.createChat();
            int third = repo.createChat();

            assertTrue(first > 0);
            assertEquals(first + 1, second);
            assertEquals(second + 1, third);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void chatWithoutMessagesIsNotOffered(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            repo.createChat();
            assertTrue(repo.getRandomChat(1).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void messagesComeBackInOrderWithAllFields(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int chatId = repo.createChat();
            repo.addMessage(message(chatId, 10, "alice", "первое"));
            repo.addMessage(message(chatId, 20, "bob", "второе"));

            List<MessageEntity> chat = repo.getRandomChat(30);

            assertEquals(2, chat.size());
            assertEquals("первое", chat.get(0).getText());
            assertEquals("alice", chat.get(0).getNameSender());
            assertEquals(10, chat.get(0).getUserId());
            assertEquals(chatId, chat.get(0).getChatId());
            assertEquals("второе", chat.get(1).getText());
            assertEquals("bob", chat.get(1).getNameSender());
            assertEquals(20, chat.get(1).getUserId());
            assertTrue(chat.get(0).getId() < chat.get(1).getId());
            assertEquals("alice: первое", chat.get(0).getFormattedMessage());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void chatIsNotOfferedToAuthorOfItsLastMessage(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int chatId = repo.createChat();
            repo.addMessage(message(chatId, 10, "alice", "привет"));

            assertTrue(repo.getRandomChat(10).isEmpty());
            assertFalse(repo.getRandomChat(20).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void onlyLastMessageAuthorIsExcluded(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int chatId = repo.createChat();
            repo.addMessage(message(chatId, 10, "alice", "привет"));
            repo.addMessage(message(chatId, 20, "bob", "ответ"));

            assertTrue(repo.getRandomChat(20).isEmpty());
            assertTrue(repo.getRandomChat(10).isEmpty());
            assertEquals(2, repo.getRandomChat(30).size());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void randomChatExcludesChatsWhereUserAlreadyWrote(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int chatId = repo.createChat();
            repo.addMessage(message(chatId, 10, "alice", "я писал"));
            repo.addMessage(message(chatId, 20, "bob", "не я"));

            assertTrue(repo.getRandomChat(10).isEmpty());
            assertFalse(repo.getRandomChat(30).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void onlyEligibleChatsAreChosen(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int own = repo.createChat();
            int foreign = repo.createChat();
            repo.addMessage(message(own, 10, "alice", "моё"));
            repo.addMessage(message(foreign, 20, "bob", "чужое"));

            for (int i = 0; i < 20; i++) {
                List<MessageEntity> chat = repo.getRandomChat(10);
                assertEquals(1, chat.size());
                assertEquals(foreign, chat.get(0).getChatId());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void randomChatIsReturnedWholeAndNeverMixed(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            Set<Integer> chatIds = new HashSet<>();
            for (int i = 0; i < 3; i++) {
                int chatId = repo.createChat();
                chatIds.add(chatId);
                repo.addMessage(message(chatId, 10, "alice", "a" + i));
                repo.addMessage(message(chatId, 11, "bob", "b" + i));
            }

            for (int i = 0; i < 20; i++) {
                List<MessageEntity> chat = repo.getRandomChat(99);
                assertEquals(2, chat.size());
                assertEquals(chat.get(0).getChatId(), chat.get(1).getChatId());
                assertTrue(chatIds.contains(chat.get(0).getChatId()));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void unicodeTextSurvivesRoundTrip(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int chatId = repo.createChat();
            repo.addMessage(message(chatId, 10, "Иван Петров", "Привет, мир! 👋 'кавычки' \"двойные\""));

            MessageEntity stored = repo.getRandomChat(20).get(0);

            assertEquals("Иван Петров", stored.getNameSender());
            assertEquals("Привет, мир! 👋 'кавычки' \"двойные\"", stored.getText());
        }
    }

    @Test
    void differentDatabasesAreIsolated(@TempDir Path dir) {
        try (HibernateChatRepository first = new HibernateChatRepository("jdbc:sqlite:" + dir.resolve("first.db"));
             HibernateChatRepository second = new HibernateChatRepository("jdbc:sqlite:" + dir.resolve("second.db"))) {
            int chatId = first.createChat();
            first.addMessage(message(chatId, 10, "alice", "только в первой"));

            assertFalse(first.getRandomChat(20).isEmpty());
            assertTrue(second.getRandomChat(20).isEmpty());
        }
    }

    @Test
    void dataSurvivesReopeningFileDatabase(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("persistent.db");
        int chatId;
        try (HibernateChatRepository repo = new HibernateChatRepository(url)) {
            chatId = repo.createChat();
            repo.addMessage(message(chatId, 10, "alice", "останется"));
        }

        try (HibernateChatRepository reopened = new HibernateChatRepository(url)) {
            List<MessageEntity> chat = reopened.getRandomChat(20);

            assertEquals(1, chat.size());
            assertEquals("останется", chat.get(0).getText());
            assertEquals(chatId + 1, reopened.createChat());
        }
    }

    @Test
    void legacyMessagesTableIsMigratedIntoChats(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "chat_id INT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL, " +
                    "name_sender VARCHAR(255) NOT NULL, text TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO messages (chat_id, user_id, name_sender, text) " +
                    "VALUES (5, 10, 'old', 'старое сообщение')");
            statement.executeUpdate("INSERT INTO messages (chat_id, user_id, name_sender, text) " +
                    "VALUES (7, 11, 'old2', 'ещё одно')");
        }

        try (HibernateChatRepository repo = new HibernateChatRepository(url)) {
            assertEquals(8, repo.createChat());

            List<MessageEntity> chat = repo.getRandomChat(99);
            assertEquals(1, chat.size());
            assertTrue(Set.of(5, 7).contains(chat.get(0).getChatId()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:nosuchdriver:foo", "jdbc:sqlite:/nonexistent-dir-for-chattape/test.db"})
    void unreachableDatabaseIsReportedAsException(String url) {
        assertThrows(Exception.class, () -> new HibernateChatRepository(url));
    }

    @Test
    void closedRepoCannotBeUsed() {
        HibernateChatRepository repo = new HibernateChatRepository("jdbc:sqlite::memory:");
        repo.close();

        assertThrows(Exception.class, repo::createChat);
        assertThrows(Exception.class, () -> repo.getRandomChat(1));
        assertThrows(Exception.class, () -> repo.addMessage(message(1, 1, "a", "b")));
    }

    // ---------- новые методы ----------

    @ParameterizedTest
    @EnumSource(Backend.class)
    void getChatSizeCountsMessages(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int chatId = repo.createChat();
            assertEquals(0, repo.getChatSize(chatId));
            repo.addMessage(message(chatId, 10, "a", "1"));
            repo.addMessage(message(chatId, 20, "b", "2"));
            assertEquals(2, repo.getChatSize(chatId));
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void getUserChatsReturnsDistinctSortedIds(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int a = repo.createChat();
            int b = repo.createChat();
            repo.addMessage(message(a, 10, "alice", "1"));
            repo.addMessage(message(a, 10, "alice", "2"));
            repo.addMessage(message(b, 10, "alice", "3"));
            repo.addMessage(message(b, 20, "bob", "4"));

            assertEquals(List.of(a, b), repo.getUserChats(10));
            assertEquals(List.of(b), repo.getUserChats(20));
            assertTrue(repo.getUserChats(99).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void getChatByIdReturnsFullOrderedChat(Backend backend, @TempDir Path dir) {
        try (HibernateChatRepository repo = new HibernateChatRepository(backend.url(dir))) {
            int chatId = repo.createChat();
            repo.addMessage(message(chatId, 10, "alice", "первое"));
            repo.addMessage(message(chatId, 20, "bob", "второе"));

            List<MessageEntity> chat = repo.getChatById(chatId);
            assertEquals(2, chat.size());
            assertEquals("первое", chat.get(0).getText());
            assertEquals("второе", chat.get(1).getText());
            assertTrue(repo.getChatById(999).isEmpty());
        }
    }
}