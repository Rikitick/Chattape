package org.bot;

import org.bot.controller.ConsoleInterface;
import org.bot.model.DataBase;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import io.github.cdimascio.dotenv.Dotenv;


public class Main
{
    public static void main(String[] args)
    {
        Dotenv dotenv = Dotenv.load();
        String botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
        if (botToken == null || botToken.isEmpty()) {
            System.err.println("Ошибка: TELEGRAM_BOT_TOKEN не найден в .env файле!");
            System.exit(1);
        }

        var dataBase = new DataBase();
        var controller = new ConsoleInterface(dataBase, botToken);
        
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
}