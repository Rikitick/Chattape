package org.bot.controller;

import org.bot.model.interfaces.DataBaseInterface;
import org.bot.Handlers;
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
    private static final int MAX_CHAT_SIZE = 10;

    private final DataBaseInterface dataBase;
    private final Handlers handlers;
    private final TelegramClient telegramClient;
    private final ConcurrentMap<Long, Integer> activeChats = new ConcurrentHashMap<>();

    public ConsoleInterface(DataBaseInterface dataBase, String botToken) {
        this(dataBase, new OkHttpTelegramClient(botToken));
    }

    public ConsoleInterface(DataBaseInterface dataBase, TelegramClient telegramClient) {
        this.dataBase = dataBase;
        this.handlers = new Handlers();
        this.telegramClient = telegramClient;
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

        if (messageText.equals("/start"))
        {
            response = handlers.StartMessage() + "\n" + loadRandomChat(userId);
        }
        else if (messageText.equals("/help"))
        {
            response = handlers.HelpMessage();
        }
        else if (messageText.equals("/new"))
        {
            activeChats.put(userId, dataBase.createChat());
            response = handlers.NewChat();
        }
        else if (messageText.equals("/skip"))
        {
            response = handlers.SkipChat() + "\n" + loadRandomChat(userId);
        }
        else if (messageText.equals("/my"))
        {
            response = listUserChats(userId);
        }
        else if (messageText.startsWith("/open "))
        {
            response = openChat(userId, messageText.substring("/open ".length()).trim());
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
                if (dataBase.getChatSize(chatId) >= MAX_CHAT_SIZE)
                {
                    activeChats.remove(userId);
                    response = handlers.ChatFull();
                }
                else
                {
                    dataBase.addMessage(new Message(
                            0,
                            chatId,
                            userId,
                            getSenderName(incoming.getFrom()),
                            messageText
                    ));
                    // одно сообщение — чат уходит из активных
                    activeChats.remove(userId);
                    response = "Сообщение сохранено.\n\n" + renderChat(dataBase.getChatById(chatId));
                }
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
        return renderChat(messages);
    }

    private String listUserChats(long userId)
    {
        List<Integer> chats = dataBase.getUserChats(userId);
        if (chats.isEmpty())
        {
            return handlers.MyChatsEmpty();
        }
        StringBuilder sb = new StringBuilder(handlers.MyChatsHeader());
        for (int id : chats)
        {
            sb.append("\n  /open ").append(id);
        }
        return sb.toString();
    }

    private String openChat(long userId, String arg)
    {
        int chatId;
        try
        {
            chatId = Integer.parseInt(arg);
        }
        catch (NumberFormatException e)
        {
            return handlers.OpenUsage();
        }

        List<Message> chat = dataBase.getChatById(chatId);
        if (chat.isEmpty())
        {
            return handlers.ChatNotFound();
        }

        boolean participated = chat.stream().anyMatch(m -> m.getUserId() == userId);
        if (!participated)
        {
            return handlers.NotParticipated();
        }

        return renderChat(chat);
    }

    private String renderChat(List<Message> chat)
    {
        StringBuilder sb = new StringBuilder();
        for (Message m : chat)
        {
            sb.append(m.getMessage()).append('\n');
        }
        return sb.toString().trim();
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