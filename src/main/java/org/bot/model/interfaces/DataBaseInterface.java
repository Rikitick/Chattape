package org.bot.model.interfaces;

import java.util.List;

import org.bot.model.Message;

public interface DataBaseInterface {
    // получить случайный чат, в котором последнее сообщение отправил НЕ user_id
    // и в котором user_id вообще ещё не писал
    List<Message> getRandomChat(long user_id);

    int createChat();

    // сохранить сообщение в новый или существующий чат
    void addMessage(Message message);

    // новое:
    int getChatSize(int chatId);
    List<Integer> getUserChats(long userId);
    List<Message> getChatById(int chatId);
}