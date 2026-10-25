package org.bot.controller;

import org.bot.model.ChatRepository;
import org.bot.model.ChatThread;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RouletteBotLogic
{
    private static final long CONSOLE_USER_ID = 0L;

    private final ChatRepository repository;
    private final ConcurrentMap<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public RouletteBotLogic(ChatRepository repository)
    {
        this.repository = repository;
    }

    public String getGreeting()
    {
        return """
               === Анонимная Чат-Рулетка (Chain Chat) ===
               Вы получаете случайное сообщение от незнакомца.
               Напишите ответ, и вся история чата отправится СЛЕДУЮЩЕМУ случайному человеку!

               Команды:
               \\help - Инструкции
               \\new  - Начать новую цепочку сообщений
               \\skip - Пропустить текущий чат и получить другой
               """;
    }

    public String getHelp()
    {
        return """
               --- Справка ---
               - Введите любой текст, чтобы ответить на текущее сообщение и отправить чат дальше.
               - \\new  - Создать совершенно новый чат и написать первое сообщение.
               - \\skip - Сбросить текущую цепочку и взять новую из пула.
               - \\exit - Выйти из программы.
               """;
    }

    public String loadNextThread()
    {
        return loadNextThread(CONSOLE_USER_ID);
    }

    public String loadNextThread(long userId)
    {
        UserSession session = getSession(userId);
        synchronized (session)
        {
            session.currentThread = repository.getRandomThread();
            if (session.currentThread == null)
            {
                session.currentThread = new ChatThread();
                return "Анонимных чатов пока нет. Напишите сообщение, чтобы начать новую цепочку!";
            }
            return "Вам пришел анонимный чат!\n" + session.currentThread.getFormattedHistory() + "\nВведите ваш ответ:";
        }
    }

    public String handleInput(String input)
    {
        return handleInput(CONSOLE_USER_ID, input);
    }

    public String handleInput(long userId, String input)
    {
        UserSession session = getSession(userId);
        synchronized (session)
        {
            String trimmed = input.trim();

            if (trimmed.equalsIgnoreCase("\\help"))
            {
                return getHelp();
            }

            if (trimmed.equalsIgnoreCase("\\new"))
            {
                session.currentThread = new ChatThread();
                return "Создан новый чат. Напишите первое сообщение:";
            }

            if (trimmed.equalsIgnoreCase("\\skip"))
            {
                if (session.currentThread != null && !session.currentThread.getMessages().isEmpty())
                {
                    repository.saveThread(session.currentThread);
                }
                return loadNextThread(userId);
            }

            if (session.currentThread == null)
            {
                session.currentThread = new ChatThread();
            }

            session.currentThread.addMessage(String.valueOf(userId), trimmed);

            repository.saveThread(session.currentThread);
            session.currentThread = null;

            return "Ваше сообщение отправлено в эстафету случайному человеку!\n\n" + loadNextThread(userId);
        }
    }

    private UserSession getSession(long userId)
    {
        return sessions.computeIfAbsent(userId, ignored -> new UserSession());
    }

    private static final class UserSession
    {
        private ChatThread currentThread;
    }
}