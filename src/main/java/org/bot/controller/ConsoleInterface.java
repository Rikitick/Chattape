package org.bot.controller;

import org.bot.view.RouletteBotLogic;
import java.util.Scanner;

public class ConsoleInterface {
    private final RouletteBotLogic botLogic;
    private final Scanner scanner;

    public ConsoleInterface(RouletteBotLogic botLogic) {
        this.botLogic = botLogic;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println(botLogic.getGreeting());

        System.out.print("Введите ваш ID / Имя для входа: ");
        String userId = scanner.nextLine().trim();
        if (userId.isBlank()) {
            userId = "User_" + System.currentTimeMillis() % 1000;
        }

        System.out.println("\nДобро пожаловать, " + userId + "!\n");
        System.out.println(botLogic.loadNextThread(userId));

        while (true) {
            System.out.print("\n[" + userId + "]> ");
            String input = scanner.nextLine();

            if ("\\exit".equalsIgnoreCase(input.trim())) {
                System.out.println("Вышли из чат-рулетки. До встречи!");
                break;
            }

            String response = botLogic.handleInput(input, userId);
            System.out.println(response);
        }
    }
}