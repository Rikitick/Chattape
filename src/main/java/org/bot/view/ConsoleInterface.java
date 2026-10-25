package org.bot.view;

import org.bot.controller.RouletteBotLogic;

import java.util.Scanner;

public class ConsoleInterface
{
    private final RouletteBotLogic botLogic;
    private final Scanner scanner;

    public ConsoleInterface(RouletteBotLogic botLogic)
    {
        this.botLogic = botLogic;
        this.scanner = new Scanner(System.in);
    }

    public void start()
    {
        System.out.println(botLogic.getGreeting());
        System.out.println(botLogic.loadNextThread());
        System.out.print("\n");
        while (true)
        {
            System.out.print("> ");
            String input = scanner.nextLine();

            if ("\\exit".equalsIgnoreCase(input.trim()))
            {
                System.out.println("Вышли из чат-рулетки. До встречи!");
                break;
            }

            if (input.trim().isEmpty())
            {
                continue;
            }
            String response = botLogic.handleInput(input);
            System.out.println(response);
        }
    }
}