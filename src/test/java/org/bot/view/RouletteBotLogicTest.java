package org.bot.view;

import org.bot.controller.RouletteBotLogic;
import org.bot.db.SqliteChatRepository;
import org.bot.model.ChatRepository;
import org.bot.model.ChatThread;
import org.bot.repository.ChatRepository;
import org.bot.repository.H2ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouletteBotLogicTest
{
    private static final String TEST_DATABASE_URL = "jdbc:sqlite:./chattape-test.db";

    private RouletteBotLogic botLogic;
    private ChatRepository repository;
    private final String testUserId = "testUser123";

    @BeforeEach
    void setUp() throws SQLException
    {
        repository = new SqliteChatRepository(TEST_DATABASE_URL);
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             Statement statement = connection.createStatement())
        {
            statement.executeUpdate("DELETE FROM chat_messages");
        }
        botLogic = new RouletteBotLogic(repository);
    }

    @AfterEach
    void tearDown() throws SQLException
    {
        repository.close();
    }

    @Test
    @DisplayName("Команда \\help возвращает справочную информацию")
    void testHelpCommand()
    {
        String response = botLogic.handleInput("\\help", testUserId);
        assertTrue(response.contains("Справка"));
    }

    @Test
    @DisplayName("Команда \\new сбрасывает контекст и создает новую цепочку")
    void testNewChainCommand()
    {
        String response = botLogic.handleInput("\\new", testUserId);
        assertTrue(response.contains("Создан новый чат"));
    }

    @Test
    @DisplayName("Отправка ответа сохраняет цепочку и возвращает следующий чат")
    void testSendMessageAndGetNext()
    {
        botLogic.loadNextThread(testUserId);
        int initialCount = repository.getActiveCount();

        String response = botLogic.handleInput("Привет, это мой ответ!", testUserId);

        assertTrue(response.contains("Ваше сообщение отправлено"));
        assertEquals(initialCount + 1, repository.getActiveCount());
    }

    @Test
    @DisplayName("Чаты сохраняются между экземплярами репозитория")
    void testRepositoryPersistsThreadsAcrossInstances() throws SQLException
    {
        ChatThread thread = new ChatThread();
        thread.addMessage("tester", "hello");
        repository.saveThread(thread);

        int savedCount = repository.getActiveCount();
        try (ChatRepository anotherRepository = new SqliteChatRepository(
            TEST_DATABASE_URL))
        {
            assertEquals(savedCount, anotherRepository.getActiveCount());
        }
    }

    @Test
    @DisplayName("Пользовательские цепочки не смешиваются между сессиями")
    void testUserSessionsKeepTheirOwnThreads() throws SQLException
    {
        botLogic.handleInput(101L, "\\new");
        botLogic.handleInput(202L, "\\new");
        botLogic.handleInput(101L, "Сообщение первого пользователя");
        botLogic.handleInput(202L, "Сообщение второго пользователя");

        Set<String> savedMessages = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(
                 TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT message_text FROM chat_messages");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                savedMessages.add(resultSet.getString("message_text"));
            }
        }

        assertEquals(Set.of("Сообщение первого пользователя", "Сообщение второго пользователя"), savedMessages);
    }
}