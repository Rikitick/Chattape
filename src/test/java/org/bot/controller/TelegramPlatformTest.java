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

import org.bot.model.MessageEntity;
import org.bot.presenter.ChatPresenter;
import org.bot.presenter.MessageTexts;
import org.bot.repository.ChatRepository;
import org.bot.repository.InMemoryChatRepository;
import org.bot.view.TelegramPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Тесты для TelegramPlatform и ChatPresenter с новой MVP-архитектурой.
 * Заменяет старый ConsoleInterfaceTest.
 */
class TelegramPlatformTest {

    private static final String NO_CHATS_AVAILABLE = "Пока нет доступных чатов, но вы можете начать новый с помощью /new";
    private static final String NO_ACTIVE_CHAT = "Пока что нет чатов для вас, но вы можете начать новый с помощью /new";

    private final MessageTexts texts = new MessageTexts();
    private final List<SendMessage> sent = new ArrayList<>();
    private TelegramClient client;
    private InMemoryChatRepository repository;
    private ChatPresenter presenter;
    private TelegramPlatform platform;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(TelegramClient.class);
        when(client.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        });
        repository = new InMemoryChatRepository();
        presenter = new ChatPresenter(repository, texts);
        platform = new TelegramPlatformForTest(presenter, "test-token", client);
    }

    // ---------- обновления, которые игнорируются ----------

    private static User user(long id, String username, String firstName, String lastName) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUserName()).thenReturn(username);
        when(user.getFirstName()).thenReturn(firstName);
        when(user.getLastName()).thenReturn(lastName);
        return user;
    }

    private static Update textUpdate(long telegramChatId, User from, String text) {
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

    private String say(long userId, String username, String text) {
        platform.consume(textUpdate(userId, user(userId, username, null, null), text));
        return sent.get(sent.size() - 1).getText();
    }

    private String alice(String text) {
        return say(1, "alice", text);
    }

    private String bob(String text) {
        return say(2, "bob", text);
    }

    // ---------- команды ----------

    @Test
    void updateWithoutMessageIsIgnored() {
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);

        platform.consume(update);

        verifyNoInteractions(client);
    }

    @Test
    void messageWithoutTextIsIgnored() {
        var message = mock(org.telegram.telegrambots.meta.api.objects.message.Message.class);
        when(message.hasText()).thenReturn(false);
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);

        platform.consume(update);

        verifyNoInteractions(client);
    }

    // ---------- команды ----------

    @Test
    void startShowsGreetingAndReportsNoChatsWhenPoolIsEmpty() {
        String reply = alice("/start");
        assertEquals(texts.startMessage() + "\n" + NO_CHATS_AVAILABLE, reply);
    }

    @Test
    void helpShowsHelpMessage() {
        assertEquals(texts.helpMessage(), alice("/help"));
    }

    @Test
    void newShowsNewChatMessageAndCreatesChat() {
        assertEquals(texts.newChat(), alice("/new"));
        assertEquals(1, repository.getCreatedChatsCount());
    }

    @Test
    void skipWithEmptyPoolReportsNoChats() {
        assertEquals(texts.skipChat() + "\n" + NO_CHATS_AVAILABLE, alice("/skip"));
    }

    @Test
    void unknownCommandIsRejected() {
        assertEquals(texts.unknownCommand(), alice("/unknown"));
    }

    @Test
    void commandsAreComparedByValueNotByReference() {
        assertEquals(texts.helpMessage(), alice(new String("/help")));
        assertEquals(texts.newChat(), alice(new String("/new")));
        assertEquals(texts.startMessage() + "\n" + NO_CHATS_AVAILABLE, alice(new String("/start")));
        assertEquals(texts.skipChat() + "\n" + NO_CHATS_AVAILABLE, alice(new String("/skip")));
    }

    @Test
    void surroundingWhitespaceIsTrimmedBeforeMatching() {
        assertEquals(texts.helpMessage(), alice("  /help \n"));
    }

    // ---------- вспомогательные методы ----------

    @Test
    void textWithoutActiveChatAsksToStartNewOne() {
        assertEquals(NO_ACTIVE_CHAT, alice("привет"));
        assertTrue(repository.getAllMessages().isEmpty());
    }

    @Test
    void textAfterNewIsSavedIntoCreatedChat() {
        alice("/new");

        String reply = alice("Привет!");
        assertTrue(reply.startsWith(texts.messageSaved()));
        assertTrue(reply.contains("alice: Привет!"));

        assertEquals(1, repository.getAllMessages().size());
        MessageEntity saved = repository.getAllMessages().get(0);
        assertEquals(1, saved.getChatId());
        assertEquals(1, saved.getUserId());
        assertEquals("alice", saved.getNameSender());
        assertEquals("Привет!", saved.getText());
    }

    @Test
    void savedTextIsTrimmed() {
        alice("/new");
        alice("   текст   ");

        assertEquals("текст", repository.getAllMessages().get(0).getText());
    }

    @Test
    void secondMessageInSameChatIsRejected() {
        alice("/new");
        alice("первое");
        assertEquals(NO_ACTIVE_CHAT, alice("второе"));
        assertEquals(1, repository.getAllMessages().size());
    }

    @Test
    void chainGoesBetweenUsers() {
        alice("/new");
        alice("Привет!");

        String bobStart = bob("/start");
        assertEquals(texts.startMessage() + "\nalice: Привет!", bobStart);

        String bobReply = bob("Здравствуй");
        assertTrue(bobReply.startsWith(texts.messageSaved()));
        assertTrue(bobReply.contains("alice: Привет!"));
        assertTrue(bobReply.contains("bob: Здравствуй"));

        String aliceOpen = alice("/open 1");
        assertTrue(aliceOpen.contains("alice: Привет!"));
        assertTrue(aliceOpen.contains("bob: Здравствуй"));
    }

    @Test
    void userCannotReceiveOwnChat() {
        alice("/new");
        alice("Привет!");

        assertEquals(texts.startMessage() + "\n" + NO_CHATS_AVAILABLE, alice("/start"));
    }

    @Test
    void userCannotReceiveChatTheyAlreadyParticipatedIn() {
        alice("/new");
        alice("Привет!");

        bob("/start");
        bob("ответ");

        assertEquals(texts.startMessage() + "\n" + NO_CHATS_AVAILABLE, alice("/start"));
        assertEquals(texts.startMessage() + "\n" + NO_CHATS_AVAILABLE, bob("/start"));
    }

    @Test
    void skipGivesAnotherUsersChatAndReplyGoesIntoIt() {
        alice("/new");
        alice("Привет!");
        bob("/new");

        assertEquals(texts.skipChat() + "\nalice: Привет!", bob("/skip"));

        bob("Ответ Боба");
        assertEquals(2, repository.getAllMessages().size());
        assertEquals(1, repository.getAllMessages().get(1).getChatId());
        assertEquals(2, repository.getAllMessages().get(1).getUserId());

        assertEquals(NO_ACTIVE_CHAT, bob("Ещё одно"));
    }

    @Test
    void usersHaveIndependentActiveChats() {
        alice("/new");
        bob("/new");

        alice("от алисы");
        bob("от боба");

        assertEquals(2, repository.getAllMessages().size());
        assertEquals(1, repository.getAllMessages().get(0).getChatId());
        assertEquals(2, repository.getAllMessages().get(1).getChatId());
    }

    // ---------- обычные сообщения ----------

    @Test
    void myListsOnlyOwnChats() {
        alice("/new");
        alice("моё");

        String reply = alice("/my");
        assertTrue(reply.contains("/open 1"));

        assertEquals(texts.myChatsEmpty(), bob("/my"));
    }

    @Test
    void openAllowsOnlyOwnChats() {
        alice("/new");
        alice("моё");

        assertTrue(alice("/open 1").contains("alice: моё"));
        assertEquals(texts.notParticipated(), bob("/open 1"));
    }

    @Test
    void openRejectsBadId() {
        assertEquals(texts.openUsage(), alice("/open abc"));
        assertEquals(texts.chatNotFound(), alice("/open 999"));
    }

    // ---------- /my и /open ----------

    @Test
    void chatIsFullAfter10Messages() {
        alice("/new");
        alice("m1");

        for (int i = 2; i <= 10; i++) {
            say(i, "user" + i, "/start");
            say(i, "user" + i, "m" + i);
        }

        assertEquals(10, repository.getChatSize(1));

        say(11, "user11", "/start");
        assertEquals(texts.chatFull(), say(11, "user11", "m11"));

        assertEquals(10, repository.getChatSize(1));
    }

    // ---------- ограничение размера чата ----------

    @Test
    void usernameIsUsedAsSenderNameWhenPresent() {
        platform.consume(textUpdate(5, user(5, "nick", "Иван", "Петров"), "/new"));
        platform.consume(textUpdate(5, user(5, "nick", "Иван", "Петров"), "текст"));

        assertEquals("nick", repository.getAllMessages().get(0).getNameSender());
    }

    @Test
    void fullNameIsUsedWhenUsernameIsBlank() {
        platform.consume(textUpdate(5, user(5, "  ", "Иван", "Петров"), "/new"));
        platform.consume(textUpdate(5, user(5, "  ", "Иван", "Петров"), "текст"));

        assertEquals("Иван Петров", repository.getAllMessages().get(0).getNameSender());
    }

    @Test
    void firstNameAloneIsUsedWhenThereIsNoLastName() {
        platform.consume(textUpdate(5, user(5, null, "Иван", null), "/new"));
        platform.consume(textUpdate(5, user(5, null, "Иван", null), "текст"));

        assertEquals("Иван", repository.getAllMessages().get(0).getNameSender());
    }

    @Test
    void userIdIsUsedWhenThereAreNoNames() {
        platform.consume(textUpdate(5, user(5, null, null, null), "/new"));
        platform.consume(textUpdate(5, user(5, null, null, null), "текст"));

        assertEquals("5", repository.getAllMessages().get(0).getNameSender());
    }

    // ---------- имя отправителя ----------

    @Test
    void replyIsSentToTelegramChatOfIncomingMessage() {
        platform.consume(textUpdate(555, user(7, "alice", null, null), "/help"));

        assertEquals(1, sent.size());
        assertEquals("555", sent.get(0).getChatId());
        assertEquals(texts.helpMessage(), sent.get(0).getText());
    }

    @Test
    void exactlyOneReplyPerMessage() {
        alice("/help");
        alice("/new");
        alice("текст");

        assertEquals(3, sent.size());
    }

    @Test
    void sendFailureIsWrappedIntoIllegalStateException() throws Exception {
        TelegramApiException failure = new TelegramApiException("нет связи");
        doThrow(failure).when(client).execute(any(SendMessage.class));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> platform.consume(textUpdate(1, user(1, "alice", null, null), "/help")));

        assertSame(failure, thrown.getCause());
    }

    // ---------- отправка ответа в Telegram ----------

    // ---------- TelegramPlatformForTest — тестовый дублёр с внедрённым моком TelegramClient ----------

    private static final class TelegramPlatformForTest extends TelegramPlatform {

        private final TelegramClient telegramClient;

        TelegramPlatformForTest(ChatPresenter presenter, String botToken, TelegramClient telegramClient) {
            super(presenter, botToken);
            this.telegramClient = telegramClient;
        }

        @Override
        public void consume(Update update) {
            if (!update.hasMessage() || !update.getMessage().hasText()) {
                return;
            }

            var incoming = update.getMessage();
            String messageText = incoming.getText().trim();
            long telegramChatId = incoming.getChatId();
            long userId = incoming.getFrom().getId();
            String response;

            if (messageText.equals("/start")) {
                response = getPresenter().handleStart(userId);
            } else if (messageText.equals("/help")) {
                response = getPresenter().handleHelp();
            } else if (messageText.equals("/new")) {
                response = getPresenter().handleNew(userId);
            } else if (messageText.equals("/skip")) {
                response = getPresenter().handleSkip(userId);
            } else if (messageText.equals("/my")) {
                response = getPresenter().handleMy(userId);
            } else if (messageText.startsWith("/open ")) {
                response = getPresenter().handleOpen(userId, messageText.substring("/open ".length()).trim());
            } else if (messageText.startsWith("/")) {
                response = getPresenter().handleUnknownCommand();
            } else {
                response = getPresenter().handleMessage(userId, getSenderName(incoming.getFrom()), messageText);
            }

            SendMessage reply = SendMessage.builder()
                    .chatId(telegramChatId)
                    .text(response)
                    .build();

            try {
                telegramClient.execute(reply);
            } catch (TelegramApiException e) {
                throw new IllegalStateException("Не удалось отправить ответ пользователю", e);
            }
        }
    }
}