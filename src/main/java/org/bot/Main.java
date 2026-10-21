package org.bot;

import org.bot.controller.ConsoleInterface;
import org.bot.repository.ChatRepository;
import org.bot.repository.H2ChatRepository;
import org.bot.view.RouletteBotLogic;

public class Main {
    public static void main(String[] args) {
        ChatRepository repository = new H2ChatRepository();
        RouletteBotLogic botLogic = new RouletteBotLogic(repository);
        ConsoleInterface consoleUI = new ConsoleInterface(botLogic);

        consoleUI.start();
    }
}