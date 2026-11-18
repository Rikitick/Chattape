package org.bot.presenter;

/**
 * Централизованные тексты сообщений для пользователя.
 * Добавлено: замена старого класса Handlers.
 */
public class MessageTexts {

    public String startMessage() {
        return """
               === Анонимная Чат-Рулетка (Chain Chat) ===
               Вы получаете случайное сообщение от незнакомца.
               Напишите ответ, и вся история чата отправится СЛЕДУЮЩЕМУ случайному человеку!

               Команды:
               /help - Инструкции
               /new  - Начать новую цепочку сообщений
               /skip - Пропустить текущий чат и получить другой
               /my   - Посмотреть свои чаты
               """;
    }

    public String helpMessage() {
        return """
               --- Справка ---
               - Введите любой текст, чтобы ответить на текущее сообщение и отправить чат дальше
               - /new  - Создать совершенно новый чат и написать первое сообщение
               - /skip - Сбросить текущую цепочку и взять новую из пула
               - /my   - Список ваших чатов (в которых вы уже писали)
               - /open <id> - Открыть свой чат по id для просмотра

               В каждом чате можно отправить только одно сообщение.
               Максимальная длина чата — 10 сообщений.
               """;
    }

    public String newChat() {
        return "Вы создали новый чат. Отправьте первое сообщение!";
    }

    public String skipChat() {
        return "скипаем...";
    }

    public String myChatsEmpty() {
        return "У вас пока нет чатов. Начните новый с помощью /new или получите чужой через /start.";
    }

    public String myChatsHeader() {
        return "Ваши чаты (откройте командой /open <id>):";
    }

    public String chatFull() {
        return "Этот чат уже заполнен (10 сообщений). Начните новый: /new.";
    }

    public String chatNotFound() {
        return "Чат не найден или в нём нет сообщений.";
    }

    public String notParticipated() {
        return "Вы не участвовали в этом чате, просмотр недоступен.";
    }

    public String openUsage() {
        return "Использование: /open <id>";
    }

    public String unknownCommand() {
        return "Неизвестная команда. Используйте /help.";
    }

    public String noActiveChat() {
        return "Пока что нет чатов для вас, но вы можете начать новый с помощью /new";
    }

    public String noChatsAvailable() {
        return "Пока нет доступных чатов, но вы можете начать новый с помощью /new";
    }

    public String messageSaved() {
        return "Сообщение сохранено.";
    }
}
