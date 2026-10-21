package org.bot.view;

import org.bot.model.ChatThread;
import org.bot.repository.ChatRepository;
import java.util.List;

public class RouletteBotLogic {
    private final ChatRepository repository;
    private ChatThread currentThread;

    public RouletteBotLogic(ChatRepository repository) {
        this.repository = repository;
    }

    public String getGreeting() {
        return """
               === Анонимная Чат-Рулетка (Story Chain) ===
               Вы получаете случайное сообщение от незнакомца.
               Напишите ответ, и вся история чата отправится СЛЕДУЮЩЕМУ человек!

               Команды:
               \\help    - Инструкции
               \\new     - Начать новую цепочку сообщений
               \\skip    - Пропустить текущий чат и получить другой
               \\history - Посмотреть истории ваших чатов
               """;
    }

    public String getHelp() {
        return """
               --- Справка ---
               - Введите любой текст, чтобы ответить на текущее сообщение.
               - \\new     - Создать совершенно новый чат.
               - \\skip    - Взять другой чат из пула.
               - \\history - Посмотреть все истории, в которых вы участвовали.
               - \\exit    - Выйти из программы.
               """;
    }

    public String loadNextThread(String userId) {
        this.currentThread = repository.findAvailableThreadForUser(userId);
        if (this.currentThread == null) {
            this.currentThread = new ChatThread();
            return "Свободных анонимных чатов нет. Напишите сообщение, чтобы начать новую цепочку!";
        }
        return "Вам пришел анонимный чат!\n" + currentThread.getFormattedHistory() + "\nВведите ваш ответ:";
    }

    public String handleInput(String input, String userId) {
        if (input == null || input.isBlank()) {
            return "Сообщение не может быть пустым.";
        }

        String trimmed = input.trim();

        if (trimmed.equalsIgnoreCase("\\help")) {
            return getHelp();
        }

        if (trimmed.equalsIgnoreCase("\\history")) {
            return getUserHistory(userId);
        }

        if (trimmed.equalsIgnoreCase("\\new")) {
            this.currentThread = new ChatThread();
            return "Создан новый чат. Напишите первое сообщение:";
        }

        if (trimmed.equalsIgnoreCase("\\skip")) {
            if (this.currentThread != null && !this.currentThread.getMessages().isEmpty()) {
                repository.saveThread(this.currentThread);
            }
            return loadNextThread(userId);
        }

        if (this.currentThread == null) {
            this.currentThread = new ChatThread();
        }

        boolean added = this.currentThread.addMessage(userId, trimmed);
        if (!added) {
            return "Вы не можете отправить сообщение в этот чат (возможно, вы писали последним или чат завершен).";
        }

        repository.saveThread(this.currentThread);
        this.currentThread = null;

        return "Ваше сообщение отправлено в эстафету!\n\n" + loadNextThread(userId);
    }

    private String getUserHistory(String userId) {
        List<ChatThread> history = repository.findThreadsByParticipant(userId);
        if (history.isEmpty()) {
            return "У вас пока нет сохранённых историй.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Ваши истории (всего: ").append(history.size()).append(") ===\n\n");
        for (ChatThread thread : history) {
            sb.append(thread.getFormattedHistory()).append("\n");
        }
        return sb.toString();
    }
}