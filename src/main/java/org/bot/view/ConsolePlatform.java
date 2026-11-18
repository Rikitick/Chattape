package org.bot.view;

import org.bot.presenter.ChatPresenter;

import java.util.Scanner;

/**
 * Консольная реализация ChatPlatform — CLI-интерфейс чат-рулетки.
 * Каждая сессия эмулирует одного пользователя с фиксированным userId.
 * Добавлено: новый консольный режим для локального тестирования без Telegram.
 */
public class ConsolePlatform implements ChatPlatform {

    private final ChatPresenter presenter;
    private final long userId;
    private final String senderName;
    private volatile boolean running = false;

    public ConsolePlatform(ChatPresenter presenter) {
        this(presenter, 1L, "console_user");
    }

    public ConsolePlatform(ChatPresenter presenter, long userId, String senderName) {
        this.presenter = presenter;
        this.userId = userId;
        this.senderName = senderName;
    }

    @Override
    public void start() {
        running = true;
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Анонимная Чат-Рулетка (Консольный режим) ===");
        System.out.println("Введите /help для списка команд. Для выхода — /exit\n");

        while (running) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            if (input.equals("/exit")) {
                running = false;
                System.out.println("До свидания!");
                break;
            }

            String response;
            if (input.equals("/start")) {
                response = presenter.handleStart(userId);
            } else if (input.equals("/help")) {
                response = presenter.handleHelp();
            } else if (input.equals("/new")) {
                response = presenter.handleNew(userId);
            } else if (input.equals("/skip")) {
                response = presenter.handleSkip(userId);
            } else if (input.equals("/my")) {
                response = presenter.handleMy(userId);
            } else if (input.startsWith("/open ")) {
                response = presenter.handleOpen(userId, input.substring("/open ".length()).trim());
            } else if (input.startsWith("/")) {
                response = presenter.handleUnknownCommand();
            } else {
                response = presenter.handleMessage(userId, senderName, input);
            }

            System.out.println(response);
            System.out.println();
        }

        scanner.close();
    }

    @Override
    public void stop() {
        running = false;
    }
}
