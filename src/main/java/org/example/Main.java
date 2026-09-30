package org.example;

import org.example.logic.RouletteBotLogic;
import org.example.repository.ChatRepository;
import org.example.ui.ConsoleInterface;

public class Main {
    public static void main(String[] args) {
        ChatRepository repository = new ChatRepository();
        RouletteBotLogic botLogic = new RouletteBotLogic(repository);
        ConsoleInterface consoleUI = new ConsoleInterface(botLogic);

        consoleUI.start();
    }
}