package org.bot.presenter;

import org.bot.model.MessageEntity;
import org.bot.repository.ChatRepository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Презентер MVP — содержит всю бизнес-логику чат-рулетки.
 * Независим от платформы: работает с любой реализацией ChatPlatform.
 * Добавлено: вынесено из ConsoleInterface, логика отделена от представления.
 */
public class ChatPresenter {

    private static final int MAX_CHAT_SIZE = 10;

    private final ChatRepository repository;
    private final MessageTexts texts;
    private final ConcurrentMap<Long, Integer> activeChats = new ConcurrentHashMap<>();

    public ChatPresenter(ChatRepository repository) {
        this(repository, new MessageTexts());
    }

    public ChatPresenter(ChatRepository repository, MessageTexts texts) {
        this.repository = repository;
        this.texts = texts;
    }

    /** Доступ к себе для тестов. */
    ChatPresenter getSelf() {
        return this;
    }

    // ---------- обработчики команд ----------

    public String handleStart(long userId) {
        return texts.startMessage() + "\n" + loadRandomChat(userId);
    }

    public String handleHelp() {
        return texts.helpMessage();
    }

    public String handleNew(long userId) {
        activeChats.put(userId, repository.createChat());
        return texts.newChat();
    }

    public String handleSkip(long userId) {
        return texts.skipChat() + "\n" + loadRandomChat(userId);
    }

    public String handleMy(long userId) {
        return listUserChats(userId);
    }

    public String handleOpen(long userId, String arg) {
        int chatId;
        try {
            chatId = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return texts.openUsage();
        }

        List<MessageEntity> chat = repository.getChatById(chatId);
        if (chat.isEmpty()) {
            return texts.chatNotFound();
        }

        boolean participated = chat.stream().anyMatch(m -> m.getUserId() == userId);
        if (!participated) {
            return texts.notParticipated();
        }

        return renderChat(chat);
    }

    public String handleUnknownCommand() {
        return texts.unknownCommand();
    }

    public String handleMessage(long userId, String senderName, String messageText) {
        if (!activeChats.containsKey(userId)) {
            return texts.noActiveChat();
        }

        int chatId = activeChats.get(userId);
        if (repository.getChatSize(chatId) >= MAX_CHAT_SIZE) {
            activeChats.remove(userId);
            return texts.chatFull();
        }

        repository.addMessage(new MessageEntity(chatId, userId, senderName, messageText));
        activeChats.remove(userId);
        return texts.messageSaved() + "\n\n" + renderChat(repository.getChatById(chatId));
    }

    // ---------- вспомогательные методы ----------

    private String loadRandomChat(long userId) {
        List<MessageEntity> messages = repository.getRandomChat(userId);
        if (messages.isEmpty()) {
            return texts.noChatsAvailable();
        }
        activeChats.put(userId, messages.get(0).getChatId());
        return renderChat(messages);
    }

    private String listUserChats(long userId) {
        List<Integer> chats = repository.getUserChats(userId);
        if (chats.isEmpty()) {
            return texts.myChatsEmpty();
        }
        StringBuilder sb = new StringBuilder(texts.myChatsHeader());
        for (int id : chats) {
            sb.append("\n  /open ").append(id);
        }
        return sb.toString();
    }

    private String renderChat(List<MessageEntity> chat) {
        StringBuilder sb = new StringBuilder();
        for (MessageEntity m : chat) {
            sb.append(m.getFormattedMessage()).append('\n');
        }
        return sb.toString().trim();
    }
}
