package org.example.ui;

import org.example.logic.RouletteBotLogic;
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
        System.out.println(botLogic.loadNextThread());

        while (true) {
            System.out.print("\n> ");
            String input = scanner.nextLine();

            if ("\\exit".equalsIgnoreCase(input.trim())) {
                System.out.println("Вышли из чат-рулетки. До встречи!");
                break;
            }

            String response = botLogic.handleInput(input);
            System.out.println(response);
        }
    }
}