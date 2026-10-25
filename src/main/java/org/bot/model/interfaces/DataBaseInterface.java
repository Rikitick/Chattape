package org.bot.model.interfaces;

import java.util.List;

import org.bot.model.Message;

public interface DataBaseInterface {
    // получить случайный чат, в котором последнее сообщение отправил НЕ user_id
    List<Message> getRandomChat(long user_id); 

    int createChat();

    // сохранить сообщение в новый или существующий чат
    void addMessage(Message message);
}
