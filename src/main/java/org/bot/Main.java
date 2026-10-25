package org.bot;

import org.bot.controller.RouletteBotLogic;
import org.bot.db.SqliteChatRepository;
import org.bot.model.ChatRepository;
import org.bot.view.ConsoleInterface;

public class Main
{
    public static void main(String[] args)
    {
        try (ChatRepository repository = new SqliteChatRepository())
        {
            RouletteBotLogic botLogic = new RouletteBotLogic(repository);
            ConsoleInterface consoleUI = new ConsoleInterface(botLogic);
            consoleUI.start();
        }
    }
}