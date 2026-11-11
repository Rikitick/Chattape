package org.bot.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bot.Handlers;
import org.bot.db.ORM;
import org.bot.model.Message;
import org.bot.model.interfaces.DataBaseInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ConsoleInterfaceTest
{
    private static final String NO_CHATS_AVAILABLE = "Пока нет доступных чатов, но вы можете начать новый с помощью /new";
    private static final String NO_ACTIVE_CHAT = "Пока что нет чатов для вас, но вы можете начать новый с помощью /new";

    private final Handlers handlers = new Handlers();
    private final List<SendMessage> sent = new ArrayList<>();
    private TelegramClient client;
    private InMemoryDataBase dataBase;
    private ConsoleInterface controller;

    @BeforeEach
    void setUp() throws Exception
    {
        client = mock(TelegramClient.class);
        when(client.execute(any(SendMessage.class))).thenAnswer(invocation ->
        {
            sent.add(invocation.getArgument(0));
            return null;
        });
        dataBase = new InMemoryDataBase();
        controller = new ConsoleInterface(dataBase, client);
    }

    // ---------- вспомогательные методы ----------

    private static User user(long id, String username, String firstName, String lastName)
    {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUserName()).thenReturn(username);
        when(user.getFirstName()).thenReturn(firstName);
        when(user.getLastName()).thenReturn(lastName);
        return user;
    }

    private static Update textUpdate(long telegramChatId, User from, String text)
    {
        var message = mock(org.telegram.telegrambots.meta.api.objects.message.Message.class);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(telegramChatId);
        when(message.getFrom()).thenReturn(from);

        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        return update;
    }

    /** Отправляет текст от имени пользователя (личный чат: id чата == id пользователя) и возвращает ответ бота. */
    private String say(long userId, String username, String text)
    {
        controller.consume(textUpdate(userId, user(userId, username, null, null), text));
        return sent.get(sent.size() - 1).getText();
    }

    private String alice(String text)
    {
        return say(1, "alice", text);
    }

    private String bob(String text)
    {
        return say(2, "bob", text);
    }

    // ---------- обновления, которые игнорируются ----------

    @Test
    void updateWithoutMessageIsIgnored()
    {
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);

        controller.consume(update);

        verifyNoInteractions(client);
    }

    @Test
    void messageWithoutTextIsIgnored()
    {
        var message = mock(org.telegram.telegrambots.meta.api.objects.message.Message.class);
        when(message.hasText()).thenReturn(false);
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);

        controller.consume(update);

        verifyNoInteractions(client);
    }

    // ---------- команды ----------

    @Test
    void startShowsGreetingAndReportsNoChatsWhenPoolIsEmpty()
    {
        String reply = alice("/start");

        assertEquals(handlers.StartMessage() + "\n" + NO_CHATS_AVAILABLE, reply);
    }

    @Test
    void helpShowsHelpMessage()
    {
        assertEquals(handlers.HelpMessage(), alice("/help"));
    }

    @Test
    void newShowsNewChatMessageAndCreatesChat()
    {
        assertEquals(handlers.NewChat(), alice("/new"));
        assertEquals(1, dataBase.createdChats());
    }

    @Test
    void skipWithEmptyPoolReportsNoChats()
    {
        assertEquals(handlers.SkipChat() + "\n" + NO_CHATS_AVAILABLE, alice("/skip"));
    }

    @Test
    void unknownCommandIsRejected()
    {
        assertEquals("Неизвестная команда. Используйте /help.", alice("/unknown"));
    }

    // Регрессия: раньше команды сравнивались через ==, и равная по значению,
    // но не интернированная строка не распознавалась как команда
    @Test
    void commandsAreComparedByValueNotByReference()
    {
        assertEquals(handlers.HelpMessage(), alice(new String("/help")));
        assertEquals(handlers.NewChat(), alice(new String("/new")));
        assertEquals(handlers.StartMessage() + "\n" + NO_CHATS_AVAILABLE, alice(new String("/start")));
        assertEquals(handlers.SkipChat() + "\n" + NO_CHATS_AVAILABLE, alice(new String("/skip")));
    }

    @Test
    void surroundingWhitespaceIsTrimmedBeforeMatching()
    {
        assertEquals(handlers.HelpMessage(), alice("  /help \n"));
    }

    // ---------- обычные сообщения ----------

    @Test
    void textWithoutActiveChatAsksToStartNewOne()
    {
        assertEquals(NO_ACTIVE_CHAT, alice("привет"));
        assertTrue(dataBase.messages().isEmpty());
    }

    @Test
    void textAfterNewIsSavedIntoCreatedChat()
    {
        alice("/new");

        String reply = alice("Привет!");
        assertTrue(reply.startsWith("Сообщение сохранено."));
        assertTrue(reply.contains("alice: Привет!"));

        assertEquals(1, dataBase.messages().size());
        Message saved = dataBase.messages().get(0);
        assertEquals(1, saved.getChatId());
        assertEquals(1, saved.getUserId());
        assertEquals("alice", saved.getNameSender());
        assertEquals("Привет!", saved.getText());
    }

    @Test
    void savedTextIsTrimmed()
    {
        alice("/new");
        alice("   текст   ");

        assertEquals("текст", dataBase.messages().get(0).getText());
    }

    @Test
    void secondMessageInSameChatIsRejected()
    {
        alice("/new");
        alice("первое");
        assertEquals(NO_ACTIVE_CHAT, alice("второе"));
        assertEquals(1, dataBase.messages().size());
    }

    @Test
    void chainGoesBetweenUsers()
    {
        alice("/new");
        alice("Привет!");

        String bobStart = bob("/start");
        assertEquals(handlers.StartMessage() + "\nalice: Привет!", bobStart);

        String bobReply = bob("Здравствуй");
        assertTrue(bobReply.startsWith("Сообщение сохранено."));
        assertTrue(bobReply.contains("alice: Привет!"));
        assertTrue(bobReply.contains("bob: Здравствуй"));

        // alice смотрит свой чат через /my + /open
        String aliceOpen = alice("/open 1");
        assertTrue(aliceOpen.contains("alice: Привет!"));
        assertTrue(aliceOpen.contains("bob: Здравствуй"));
    }

    @Test
    void userCannotReceiveOwnChat()
    {
        alice("/new");
        alice("Привет!");

        assertEquals(handlers.StartMessage() + "\n" + NO_CHATS_AVAILABLE, alice("/start"));
    }

    @Test
    void userCannotReceiveChatTheyAlreadyParticipatedIn()
    {
        alice("/new");
        alice("Привет!");

        bob("/start");
        bob("ответ");

        // теперь чат не должен выдаваться ни alice, ни bob через /start
        assertEquals(handlers.StartMessage() + "\n" + NO_CHATS_AVAILABLE, alice("/start"));
        assertEquals(handlers.StartMessage() + "\n" + NO_CHATS_AVAILABLE, bob("/start"));
    }

    @Test
    void skipGivesAnotherUsersChatAndReplyGoesIntoIt()
    {
        alice("/new");
        alice("Привет!");
        bob("/new");

        assertEquals(handlers.SkipChat() + "\nalice: Привет!", bob("/skip"));

        bob("Ответ Боба");
        assertEquals(2, dataBase.messages().size());
        assertEquals(1, dataBase.messages().get(1).getChatId());
        assertEquals(2, dataBase.messages().get(1).getUserId());

        // второе сообщение в тот же чат — уже нельзя
        assertEquals(NO_ACTIVE_CHAT, bob("Ещё одно"));
    }

    @Test
    void usersHaveIndependentActiveChats()
    {
        alice("/new");
        bob("/new");

        alice("от алисы");
        bob("от боба");

        assertEquals(2, dataBase.messages().size());
        assertEquals(1, dataBase.messages().get(0).getChatId());
        assertEquals(2, dataBase.messages().get(1).getChatId());
    }

    // ---------- /my и /open ----------

    @Test
    void myListsOnlyOwnChats()
    {
        alice("/new");
        alice("моё");

        String reply = alice("/my");
        assertTrue(reply.contains("/open 1"));

        assertEquals(handlers.MyChatsEmpty(), bob("/my"));
    }

    @Test
    void openAllowsOnlyOwnChats()
    {
        alice("/new");
        alice("моё");

        assertTrue(alice("/open 1").contains("alice: моё"));
        assertEquals(handlers.NotParticipated(), bob("/open 1"));
    }

    @Test
    void openRejectsBadId()
    {
        assertEquals(handlers.OpenUsage(), alice("/open abc"));
        assertEquals(handlers.ChatNotFound(), alice("/open 999"));
    }

    // ---------- ограничение размера чата ----------

    @Test
    void chatIsFullAfter10Messages()
    {
        // alice создаёт чат и пишет первое сообщение
        alice("/new");
        alice("m1");

        // user2..user10 дописывают до 10 сообщений
        for (int i = 2; i <= 10; i++) {
            say(i, "user" + i, "/start");
            say(i, "user" + i, "m" + i);
        }

        // в чате ровно 10 сообщений
        assertEquals(10, dataBase.getChatSize(1));

        // user11 получает чат (мок не знает про лимит), но запись должна быть отклонена
        say(11, "user11", "/start");
        assertEquals(handlers.ChatFull(), say(11, "user11", "m11"));

        // и в чате по-прежнему 10 сообщений
        assertEquals(10, dataBase.getChatSize(1));
    }

    // ---------- имя отправителя ----------

    @Test
    void usernameIsUsedAsSenderNameWhenPresent()
    {
        controller.consume(textUpdate(5, user(5, "nick", "Иван", "Петров"), "/new"));
        controller.consume(textUpdate(5, user(5, "nick", "Иван", "Петров"), "текст"));

        assertEquals("nick", dataBase.messages().get(0).getNameSender());
    }

    @Test
    void fullNameIsUsedWhenUsernameIsBlank()
    {
        controller.consume(textUpdate(5, user(5, "  ", "Иван", "Петров"), "/new"));
        controller.consume(textUpdate(5, user(5, "  ", "Иван", "Петров"), "текст"));

        assertEquals("Иван Петров", dataBase.messages().get(0).getNameSender());
    }

    @Test
    void firstNameAloneIsUsedWhenThereIsNoLastName()
    {
        controller.consume(textUpdate(5, user(5, null, "Иван", null), "/new"));
        controller.consume(textUpdate(5, user(5, null, "Иван", null), "текст"));

        assertEquals("Иван", dataBase.messages().get(0).getNameSender());
    }

    @Test
    void userIdIsUsedWhenThereAreNoNames()
    {
        controller.consume(textUpdate(5, user(5, null, null, null), "/new"));
        controller.consume(textUpdate(5, user(5, null, null, null), "текст"));

        assertEquals("5", dataBase.messages().get(0).getNameSender());
    }

    // ---------- отправка ответа в Telegram ----------

    @Test
    void replyIsSentToTelegramChatOfIncomingMessage()
    {
        controller.consume(textUpdate(555, user(7, "alice", null, null), "/help"));

        assertEquals(1, sent.size());
        assertEquals("555", sent.get(0).getChatId());
        assertEquals(handlers.HelpMessage(), sent.get(0).getText());
    }

    @Test
    void exactlyOneReplyPerMessage()
    {
        alice("/help");
        alice("/new");
        alice("текст");

        assertEquals(3, sent.size());
    }

    @Test
    void sendFailureIsWrappedIntoIllegalStateException() throws Exception
    {
        TelegramApiException failure = new TelegramApiException("нет связи");
        doThrow(failure).when(client).execute(any(SendMessage.class));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> controller.consume(textUpdate(1, user(1, "alice", null, null), "/help")));

        assertSame(failure, thrown.getCause());
    }

    // ---------- с настоящей базой данных ----------

    @Test
    void worksWithRealSqliteDatabase()
    {
        try (ORM orm = new ORM("jdbc:sqlite::memory:"))
        {
            controller = new ConsoleInterface(orm, client);

            alice("/new");
            alice("Привет!");

            assertEquals(handlers.StartMessage() + "\nalice: Привет!", bob("/start"));

            String bobReply = bob("Здравствуй");
            assertTrue(bobReply.startsWith("Сообщение сохранено."));
            assertTrue(bobReply.contains("alice: Привет!"));
            assertTrue(bobReply.contains("bob: Здравствуй"));

            String aliceOpen = alice("/open 1");
            assertTrue(aliceOpen.contains("alice: Привет!"));
            assertTrue(aliceOpen.contains("bob: Здравствуй"));
        }
    }

    // ---------- простая подмена базы данных ----------

    private static final class InMemoryDataBase implements DataBaseInterface
    {
        private final List<Message> messages = new ArrayList<>();
        private int chats = 0;

        @Override
        public int createChat()
        {
            return ++chats;
        }

        @Override
        public void addMessage(Message message)
        {
            messages.add(new Message(messages.size() + 1, message.getChatId(), message.getUserId(),
                    message.getNameSender(), message.getText()));
        }

        @Override
        public List<Message> getRandomChat(long userId)
        {
            Map<Integer, List<Message>> byChat = new LinkedHashMap<>();
            for (Message m : messages)
            {
                byChat.computeIfAbsent(m.getChatId(), k -> new ArrayList<>()).add(m);
            }

            List<List<Message>> eligible = new ArrayList<>();
            for (List<Message> chat : byChat.values())
            {
                boolean userAlreadyWrote = chat.stream().anyMatch(m -> m.getUserId() == userId);
                if (userAlreadyWrote) continue;

                Message last = chat.get(chat.size() - 1);
                if (last.getUserId() == userId) continue;

                eligible.add(chat);
            }

            if (eligible.isEmpty()) return List.of();
            return eligible.get(0);
        }

        @Override
        public int getChatSize(int chatId)
        {
            int n = 0;
            for (Message m : messages)
            {
                if (m.getChatId() == chatId) n++;
            }
            return n;
        }

        @Override
        public List<Integer> getUserChats(long userId)
        {
            List<Integer> result = new ArrayList<>();
            for (Message m : messages)
            {
                if (m.getUserId() == userId && !result.contains(m.getChatId()))
                {
                    result.add(m.getChatId());
                }
            }
            result.sort(Integer::compareTo);
            return result;
        }

        @Override
        public List<Message> getChatById(int chatId)
        {
            List<Message> result = new ArrayList<>();
            for (Message m : messages)
            {
                if (m.getChatId() == chatId) result.add(m);
            }
            return result;
        }

        List<Message> messages()
        {
            return messages;
        }

        int createdChats()
        {
            return chats;
        }
    }
}