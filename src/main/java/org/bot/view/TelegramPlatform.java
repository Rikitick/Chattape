package org.bot.view;

import org.bot.presenter.ChatPresenter;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram-реализация ChatPlatform.
 * Принимает обновления через long polling и передаёт всю логику ChatPresenter.
 * Добавлено: вынесено из ConsoleInterface, логика отделена от платформы.
 */
public class TelegramPlatform implements ChatPlatform, LongPollingSingleThreadUpdateConsumer {

    private final ChatPresenter presenter;
    private final String botToken;
    private final TelegramClient telegramClient;
    private TelegramBotsLongPollingApplication botsApplication;

    public TelegramPlatform(ChatPresenter presenter, String botToken) {
        this.presenter = presenter;
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    /** Доступ к презентеру для тестов. */
    public ChatPresenter getPresenter() {
        return presenter;
    }

    /** Формирование имени отправителя для тестов. */
    public String getSenderName(org.telegram.telegrambots.meta.api.objects.User user) {
        if (user.getUserName() != null && !user.getUserName().isBlank()) {
            return user.getUserName();
        }
        String firstName = user.getFirstName() == null ? "" : user.getFirstName();
        String lastName = user.getLastName() == null ? "" : user.getLastName();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? Long.toString(user.getId()) : fullName;
    }

    @Override
    public void start() {
        try {
            botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(botToken, this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start Telegram bot", e);
        }
    }

    @Override
    public void stop() {
        if (botsApplication != null) {
            try {
                botsApplication.close();
            } catch (Exception ignored) {
            }
        }
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
            response = presenter.handleStart(userId);
        } else if (messageText.equals("/help")) {
            response = presenter.handleHelp();
        } else if (messageText.equals("/new")) {
            response = presenter.handleNew(userId);
        } else if (messageText.equals("/skip")) {
            response = presenter.handleSkip(userId);
        } else if (messageText.equals("/my")) {
            response = presenter.handleMy(userId);
        } else if (messageText.startsWith("/open ")) {
            response = presenter.handleOpen(userId, messageText.substring("/open ".length()).trim());
        } else if (messageText.startsWith("/")) {
            response = presenter.handleUnknownCommand();
        } else {
            response = presenter.handleMessage(userId, getSenderName(incoming.getFrom()), messageText);
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
