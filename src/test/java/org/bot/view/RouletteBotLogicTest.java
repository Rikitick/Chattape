package org.bot.view;

import org.bot.model.ChatRepository;
import org.bot.model.ChatThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouletteBotLogicTest
{
    private RouletteBotLogic botLogic;
    private ChatRepository repository;

    @BeforeEach
    void setUp()
    {
        repository = new ChatRepository();
        botLogic = new RouletteBotLogic(repository);
    }

    @Test
    @DisplayName("Команда \\help возвращает справочную информацию")
    void testHelpCommand()
    {
        String response = botLogic.handleInput("\\help");
        assertTrue(response.contains("Справка"));
    }

    @Test
    @DisplayName("Команда \\new сбрасывает контекст и создает новую цепочку")
    void testNewChainCommand()
    {
        String response = botLogic.handleInput("\\new");
        assertTrue(response.contains("Создан новый чат"));
    }

    @Test
    @DisplayName("Отправка ответа сохраняет цепочку и возвращает следующий чат")
    void testSendMessageAndGetNext()
    {
        botLogic.loadNextThread();
        int initialCount = repository.getActiveCount();

        String response = botLogic.handleInput("Привет, это мой ответ!");

        assertTrue(response.contains("Ваше сообщение отправлено"));
        assertEquals(initialCount, repository.getActiveCount());
    }

    @Test
    @DisplayName("Чаты сохраняются между экземплярами репозитория")
    void testRepositoryPersistsThreadsAcrossInstances()
    {
        ChatThread thread = new ChatThread();
        thread.addMessage("tester", "hello");
        repository.saveThread(thread);

        int savedCount = repository.getActiveCount();
        ChatRepository anotherRepository = new ChatRepository();

        assertEquals(savedCount, anotherRepository.getActiveCount());
    }
}