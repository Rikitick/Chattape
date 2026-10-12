package org.bot;

import org.bot.model.ChatRepository;
import org.bot.view.RouletteBotLogic;
import org.bot.controller.ConsoleInterface;

public class Main
{
    public static void main(String[] args)
    {
        ChatRepository repository = new ChatRepository();
        RouletteBotLogic botLogic = new RouletteBotLogic(repository);
        ConsoleInterface consoleUI = new ConsoleInterface(botLogic);

        consoleUI.start();
    }
}