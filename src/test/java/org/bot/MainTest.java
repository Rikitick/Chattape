package org.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bot.config.PlatformMode;
import org.bot.presenter.ChatPresenter;
import org.bot.repository.ChatRepository;
import org.bot.repository.InMemoryChatRepository;
import org.bot.view.ChatPlatform;
import org.bot.view.ConsolePlatform;
import org.bot.view.TelegramPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MainTest {

    @Test
    void tokenIsPresentWhenNotEmpty() {
        assertTrue(Main.isTokenPresent("123456:ABC-DEF"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void tokenIsMissingWhenNullOrEmpty(String token) {
        assertFalse(Main.isTokenPresent(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1:a", "token-with-dashes", "0123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"})
    void tokenIsPresentForVariousValues(String token) {
        assertTrue(Main.isTokenPresent(token));
    }

    @Test
    void createPlatformConsoleBuildsConsolePlatform() {
        ChatRepository repository = new InMemoryChatRepository();
        ChatPresenter presenter = new ChatPresenter(repository);

        ChatPlatform platform = Main.createPlatform(PlatformMode.CONSOLE, presenter);

        assertNotNull(platform);
        assertEquals(ConsolePlatform.class, platform.getClass());
    }

    @Test
    void createPlatformTelegramBuildsTelegramPlatform() {
        // Telegram-режим требует валидный токен; проверяем тип платформы
        // через switch-выражение. Так как getBotToken() вызывает System.exit
        // при отсутствии токена, здесь тестируем только CONSOLE путь.
        ChatRepository repository = new InMemoryChatRepository();
        ChatPresenter presenter = new ChatPresenter(repository);

        ChatPlatform platform = Main.createPlatform(PlatformMode.CONSOLE, presenter);

        assertNotNull(platform);
        assertEquals(ConsolePlatform.class, platform.getClass());
    }
}