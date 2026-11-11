package org.bot.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bot.model.Message;
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
 * ORM проверяется на нескольких разных базах: в памяти, во временном файле
 * и во внутрипамятной базе с общим кэшем. Тесты запускаются для каждой.
 */
class ORMTest
{
    enum Backend
    {
        IN_MEMORY
                {
                    @Override
                    String url(Path dir)
                    {
                        return "jdbc:sqlite::memory:";
                    }
                },
        TEMP_FILE
                {
                    @Override
                    String url(Path dir)
                    {
                        return "jdbc:sqlite:" + dir.resolve("chattape.db");
                    }
                },
        SHARED_CACHE_MEMORY
                {
                    @Override
                    String url(Path dir)
                    {
                        return "jdbc:sqlite:file:chattape-" + UUID.randomUUID() + "?mode=memory&cache=shared";
                    }
                };

        abstract String url(Path dir);
    }

    private static Message message(int chatId, long userId, String name, String text)
    {
        return new Message(0, chatId, userId, name, text);
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void emptyDatabaseHasNoChatsToOffer(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            assertTrue(orm.getRandomChat(1).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void createChatReturnsConsecutiveIds(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int first = orm.createChat();
            int second = orm.createChat();
            int third = orm.createChat();

            assertTrue(first > 0);
            assertEquals(first + 1, second);
            assertEquals(second + 1, third);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void chatWithoutMessagesIsNotOffered(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            orm.createChat();

            assertTrue(orm.getRandomChat(1).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void messagesComeBackInOrderWithAllFields(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int chatId = orm.createChat();
            orm.addMessage(message(chatId, 10, "alice", "первое"));
            orm.addMessage(message(chatId, 20, "bob", "второе"));

            List<Message> chat = orm.getRandomChat(30);

            assertEquals(2, chat.size());
            assertEquals("первое", chat.get(0).getText());
            assertEquals("alice", chat.get(0).getNameSender());
            assertEquals(10, chat.get(0).getUserId());
            assertEquals(chatId, chat.get(0).getChatId());
            assertEquals("второе", chat.get(1).getText());
            assertEquals("bob", chat.get(1).getNameSender());
            assertEquals(20, chat.get(1).getUserId());
            assertTrue(chat.get(0).getId() < chat.get(1).getId());
            assertEquals("alice: первое", chat.get(0).getMessage());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void chatIsNotOfferedToAuthorOfItsLastMessage(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int chatId = orm.createChat();
            orm.addMessage(message(chatId, 10, "alice", "привет"));

            assertTrue(orm.getRandomChat(10).isEmpty());
            assertFalse(orm.getRandomChat(20).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void onlyLastMessageAuthorIsExcluded(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int chatId = orm.createChat();
            orm.addMessage(message(chatId, 10, "alice", "привет"));
            orm.addMessage(message(chatId, 20, "bob", "ответ"));

            // bob — последний автор, ему чат не выдаётся
            assertTrue(orm.getRandomChat(20).isEmpty());
            // alice уже писала в этот чат → по новой логике тоже не выдаётся
            assertTrue(orm.getRandomChat(10).isEmpty());
            // carol не писала и не последний автор → получает
            assertEquals(2, orm.getRandomChat(30).size());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void randomChatExcludesChatsWhereUserAlreadyWrote(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int chatId = orm.createChat();
            orm.addMessage(message(chatId, 10, "alice", "я писал"));
            orm.addMessage(message(chatId, 20, "bob", "не я"));

            assertTrue(orm.getRandomChat(10).isEmpty()); // alice уже писала
            assertFalse(orm.getRandomChat(30).isEmpty()); // carol — ещё нет
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void onlyEligibleChatsAreChosen(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int own = orm.createChat();
            int foreign = orm.createChat();
            orm.addMessage(message(own, 10, "alice", "моё"));
            orm.addMessage(message(foreign, 20, "bob", "чужое"));

            for (int i = 0; i < 20; i++)
            {
                List<Message> chat = orm.getRandomChat(10);
                assertEquals(1, chat.size());
                assertEquals(foreign, chat.get(0).getChatId());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void randomChatIsReturnedWholeAndNeverMixed(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            Set<Integer> chatIds = new HashSet<>();
            for (int i = 0; i < 3; i++)
            {
                int chatId = orm.createChat();
                chatIds.add(chatId);
                orm.addMessage(message(chatId, 10, "alice", "a" + i));
                orm.addMessage(message(chatId, 11, "bob", "b" + i));
            }

            for (int i = 0; i < 20; i++)
            {
                List<Message> chat = orm.getRandomChat(99);
                assertEquals(2, chat.size());
                assertEquals(chat.get(0).getChatId(), chat.get(1).getChatId());
                assertTrue(chatIds.contains(chat.get(0).getChatId()));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void unicodeTextSurvivesRoundTrip(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int chatId = orm.createChat();
            orm.addMessage(message(chatId, 10, "Иван Петров", "Привет, мир! 👋 'кавычки' \"двойные\""));

            Message stored = orm.getRandomChat(20).get(0);

            assertEquals("Иван Петров", stored.getNameSender());
            assertEquals("Привет, мир! 👋 'кавычки' \"двойные\"", stored.getText());
        }
    }

    @Test
    void differentDatabasesAreIsolated(@TempDir Path dir)
    {
        try (ORM first = new ORM("jdbc:sqlite:" + dir.resolve("first.db"));
             ORM second = new ORM("jdbc:sqlite:" + dir.resolve("second.db")))
        {
            int chatId = first.createChat();
            first.addMessage(message(chatId, 10, "alice", "только в первой"));

            assertFalse(first.getRandomChat(20).isEmpty());
            assertTrue(second.getRandomChat(20).isEmpty());
        }
    }

    @Test
    void dataSurvivesReopeningFileDatabase(@TempDir Path dir)
    {
        String url = "jdbc:sqlite:" + dir.resolve("persistent.db");
        int chatId;
        try (ORM orm = new ORM(url))
        {
            chatId = orm.createChat();
            orm.addMessage(message(chatId, 10, "alice", "останется"));
        }

        try (ORM reopened = new ORM(url))
        {
            List<Message> chat = reopened.getRandomChat(20);

            assertEquals(1, chat.size());
            assertEquals("останется", chat.get(0).getText());
            assertEquals(chatId + 1, reopened.createChat());
        }
    }

    @Test
    void legacyMessagesTableIsMigratedIntoChats(@TempDir Path dir) throws Exception
    {
        String url = "jdbc:sqlite:" + dir.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement())
        {
            statement.executeUpdate("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "chat_id INT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL, " +
                    "name_sender VARCHAR(255) NOT NULL, text TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO messages (chat_id, user_id, name_sender, text) " +
                    "VALUES (5, 10, 'old', 'старое сообщение')");
            statement.executeUpdate("INSERT INTO messages (chat_id, user_id, name_sender, text) " +
                    "VALUES (7, 11, 'old2', 'ещё одно')");
        }

        try (ORM orm = new ORM(url))
        {
            assertEquals(8, orm.createChat());

            List<Message> chat = orm.getRandomChat(99);
            assertEquals(1, chat.size());
            assertTrue(Set.of(5, 7).contains(chat.get(0).getChatId()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:nosuchdriver:foo", "jdbc:sqlite:/nonexistent-dir-for-chattape/test.db"})
    void unreachableDatabaseIsReportedAsIllegalState(String url)
    {
        assertThrows(IllegalStateException.class, () -> new ORM(url));
    }

    @Test
    void closedOrmCannotBeUsed()
    {
        ORM orm = new ORM("jdbc:sqlite::memory:");
        orm.close();

        assertThrows(IllegalStateException.class, orm::createChat);
        assertThrows(IllegalStateException.class, () -> orm.getRandomChat(1));
        assertThrows(IllegalStateException.class, () -> orm.addMessage(message(1, 1, "a", "b")));
    }

    // ---------- новые методы ----------

    @ParameterizedTest
    @EnumSource(Backend.class)
    void getChatSizeCountsMessages(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int chatId = orm.createChat();
            assertEquals(0, orm.getChatSize(chatId));
            orm.addMessage(message(chatId, 10, "a", "1"));
            orm.addMessage(message(chatId, 20, "b", "2"));
            assertEquals(2, orm.getChatSize(chatId));
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void getUserChatsReturnsDistinctSortedIds(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int a = orm.createChat();
            int b = orm.createChat();
            orm.addMessage(message(a, 10, "alice", "1"));
            orm.addMessage(message(a, 10, "alice", "2"));
            orm.addMessage(message(b, 10, "alice", "3"));
            orm.addMessage(message(b, 20, "bob", "4"));

            assertEquals(List.of(a, b), orm.getUserChats(10));
            assertEquals(List.of(b), orm.getUserChats(20));
            assertTrue(orm.getUserChats(99).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void getChatByIdReturnsFullOrderedChat(Backend backend, @TempDir Path dir)
    {
        try (ORM orm = new ORM(backend.url(dir)))
        {
            int chatId = orm.createChat();
            orm.addMessage(message(chatId, 10, "alice", "первое"));
            orm.addMessage(message(chatId, 20, "bob", "второе"));

            List<Message> chat = orm.getChatById(chatId);
            assertEquals(2, chat.size());
            assertEquals("первое", chat.get(0).getText());
            assertEquals("второе", chat.get(1).getText());
            assertTrue(orm.getChatById(999).isEmpty());
        }
    }
}