package org.bot.controller;

import org.bot.model.DataBase;
import org.bot.view.Handlers;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.bot.model.Message;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConsoleInterface implements LongPollingSingleThreadUpdateConsumer
{
    private final DataBase dataBase;
    private final Handlers handlers;
    private final TelegramClient telegramClient;
    private final ConcurrentMap<Long, Integer> activeChats = new ConcurrentHashMap<>();

    public ConsoleInterface(DataBase dataBase, String botToken) {
        
        this.dataBase = dataBase;
        this.handlers = new Handlers();
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Override
    public void consume(Update update)
    {
        if (!update.hasMessage() || !update.getMessage().hasText())
        {
            return;
        }

        var incoming = update.getMessage();
        String messageText = incoming.getText().trim();
        long telegramChatId = incoming.getChatId();
        long userId = incoming.getFrom().getId();
        String response;

        if (messageText == "/start")
        {
            response = handlers.StartMessage() + "\n" + loadRandomChat(userId);
        }
        else if (messageText == "/help")
        {
            response = handlers.HelpMessage();
        }
        else if (messageText == "/new")
        {
            activeChats.put(userId, dataBase.createChat());
            response = handlers.NewChat();
        }
        else if (messageText == "/skip")
        {
            response = handlers.SkipChat() + "\n" + loadRandomChat(userId);
        }
        else if (messageText.startsWith("/"))
        {
            response = "Неизвестная команда. Используйте /help.";
        }
        else
        {
            if (!activeChats.containsKey(userId))
            {
                response = "Пока что нет чатов для вас, но вы можете начать новый с помощью /new";
            }
            else
            {
                int chatId = activeChats.get(userId);
                dataBase.saveNewMessage(new Message(
                        0,
                        chatId,
                        userId,
                        getSenderName(incoming.getFrom()),
                        messageText
                ));
                response = "Сообщение сохранено.";
            }
        }

        SendMessage reply = SendMessage.builder()
                .chatId(telegramChatId)
                .text(response)
                .build();

        try
        {
            telegramClient.execute(reply);
        }
        catch (TelegramApiException e)
        {
            throw new IllegalStateException("Не удалось отправить ответ пользователю", e);
        }
    }

    private String loadRandomChat(long userId)
    {
        List<Message> messages = dataBase.getRandomChat(userId);
        if (messages.isEmpty())
        {
            return "Пока нет доступных чатов, но вы можете начать новый с помощью /new";
        }

        activeChats.put(userId, messages.get(0).getChatId());
        StringBuilder history = new StringBuilder();
        for (Message message : messages)
        {
            history.append(message.getMessage()).append('\n');
        }
        return history.toString().trim();
    }

    private String getSenderName(org.telegram.telegrambots.meta.api.objects.User user)
    {
        if (user.getUserName() != null && !user.getUserName().isBlank())
        {
            return user.getUserName();
        }
        String firstName = user.getFirstName() == null ? "" : user.getFirstName();
        String lastName = user.getLastName() == null ? "" : user.getLastName();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? Long.toString(user.getId()) : fullName;
    }
}