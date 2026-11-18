package org.bot.repository;

import org.bot.model.MessageEntity;

import java.util.List;

/**
 * Интерфейс репозитория — абстракция над доступом к данным.
 * Добавлено: замена прямого использования JDBC на Repository паттерн.
 */
public interface ChatRepository {

    int createChat();

    List<MessageEntity> getRandomChat(long userId);

    void addMessage(MessageEntity message);

    int getChatSize(int chatId);

    List<Integer> getUserChats(long userId);

    List<MessageEntity> getChatById(int chatId);

    void close();
}
