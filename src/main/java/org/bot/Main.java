package org.bot;

import org.bot.controller.ConsoleInterface;
import org.bot.model.DataBase;
import org.bot.model.interfaces.DataBaseInterface;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import io.github.cdimascio.dotenv.Dotenv;


public class Main
{
    public static void main(String[] args)
    {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
        if (!isTokenPresent(botToken)) {
            System.err.println("Ошибка: TELEGRAM_BOT_TOKEN не найден в .env файле!");
            System.exit(1);
        }

        var dataBase = new DataBase();
        var controller = createController(dataBase, botToken);

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication())
        {
            botsApplication.registerBot(botToken, controller);
            Thread.currentThread().join();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    static boolean isTokenPresent(String botToken)
    {
        return botToken != null && !botToken.isEmpty();
    }

    static ConsoleInterface createController(DataBaseInterface dataBase, String botToken)
    {
        return new ConsoleInterface(dataBase, botToken);
    }
}