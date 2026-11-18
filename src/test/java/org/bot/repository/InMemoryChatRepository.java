package org.bot.repository;

import org.bot.model.MessageEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory реализация ChatRepository для тестов.
 * Добавлено: замена InMemoryDataBase для работы с новой архитектурой.
 */
public class InMemoryChatRepository implements ChatRepository {

    private final List<MessageEntity> messages = new ArrayList<>();
    private int nextChatId = 1;

    @Override
    public synchronized int createChat() {
        return nextChatId++;
    }

    @Override
    public synchronized void addMessage(MessageEntity message) {
        messages.add(message);
    }

    @Override
    public List<MessageEntity> getRandomChat(long userId) {
        Map<Integer, List<MessageEntity>> byChat = new LinkedHashMap<>();
        for (MessageEntity m : messages) {
            byChat.computeIfAbsent(m.getChatId(), k -> new ArrayList<>()).add(m);
        }

        for (List<MessageEntity> chat : byChat.values()) {
            boolean userAlreadyWrote = chat.stream().anyMatch(m -> m.getUserId() == userId);
            if (userAlreadyWrote) continue;

            MessageEntity last = chat.get(chat.size() - 1);
            if (last.getUserId() == userId) continue;

            return chat;
        }

        return List.of();
    }

    @Override
    public int getChatSize(int chatId) {
        int n = 0;
        for (MessageEntity m : messages) {
            if (m.getChatId() == chatId) n++;
        }
        return n;
    }

    @Override
    public List<Integer> getUserChats(long userId) {
        List<Integer> result = new ArrayList<>();
        for (MessageEntity m : messages) {
            if (m.getUserId() == userId && !result.contains(m.getChatId())) {
                result.add(m.getChatId());
            }
        }
        result.sort(Integer::compareTo);
        return result;
    }

    @Override
    public List<MessageEntity> getChatById(int chatId) {
        List<MessageEntity> result = new ArrayList<>();
        for (MessageEntity m : messages) {
            if (m.getChatId() == chatId) result.add(m);
        }
        return result;
    }

    @Override
    public void close() {
    }

    // Вспомогательные методы для тестов
    public List<MessageEntity> getAllMessages() {
        return new ArrayList<>(messages);
    }

    public int getCreatedChatsCount() {
        return nextChatId - 1;
    }
}
