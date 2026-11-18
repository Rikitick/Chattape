package org.bot;

import org.bot.config.PlatformMode;
import org.bot.presenter.ChatPresenter;
import org.bot.repository.ChatRepository;
import org.bot.repository.HibernateChatRepository;
import org.bot.view.ChatPlatform;
import org.bot.view.ConsolePlatform;
import org.bot.view.TelegramPlatform;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Точка входа в приложение — чат-рулетка Chattape.
 *
 * Переключение платформы через константу {@link #MODE} или аргумент командной строки:
 *   java Main CONSOLE
 *   java Main TELEGRAM
 *
 * Изменено: переписано на MVP, добавлено переключение платформ, удалён неиспользуемый код.
 */
public class Main {

    /** Константа для быстрого переключения между режимами. */
    private static final PlatformMode MODE = PlatformMode.TELEGRAM;

    public static void main(String[] args) {
        // Аргумент командной строки переопределяет константу
        PlatformMode mode = args.length > 0 ? PlatformMode.valueOf(args[0].toUpperCase()) : MODE;

        ChatRepository repository = new HibernateChatRepository();
        ChatPresenter presenter = new ChatPresenter(repository);
        ChatPlatform platform = createPlatform(mode, presenter);

        platform.start();

        // Держим главный поток живым в Telegram-режиме
        if (mode == PlatformMode.TELEGRAM) {
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static ChatPlatform createPlatform(PlatformMode mode, ChatPresenter presenter) {
        return switch (mode) {
            case CONSOLE -> new ConsolePlatform(presenter);
            case TELEGRAM -> {
                String botToken = getBotToken();
                yield new TelegramPlatform(presenter, botToken);
            }
        };
    }

    static String getBotToken() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
        if (botToken == null || botToken.isEmpty()) {
            System.err.println("Ошибка: TELEGRAM_BOT_TOKEN не найден в .env файле!");
            System.exit(1);
        }
        return botToken;
    }

    static boolean isTokenPresent(String botToken) {
        return botToken != null && !botToken.isEmpty();
    }
}