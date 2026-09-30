package org.example.logic;

import org.example.model.ChatThread;
import org.example.repository.ChatRepository;

public class RouletteBotLogic {
    private final ChatRepository repository;
    private ChatThread currentThread;

    public RouletteBotLogic(ChatRepository repository) {
        this.repository = repository;
    }

    public String getGreeting() {
        return """
               === Анонимная Чат-Рулетка (Chain Chat) ===
               Вы получаете случайное сообщение от незнакомца.
               Напишите ответ, и вся история чата отправится СЛЕДУЮЩЕМУ случайному человеку!

               Команды:
               \\help - Инструкции
               \\new  - Начать новую цепочку сообщений
               \\skip - Пропустить текущий чат и получить другой
               """;
    }

    public String getHelp() {
        return """
               --- Справка ---
               - Введите любой текст, чтобы ответить на текущее сообщение и отправить чат дальше.
               - \\new  - Создать совершенно новый чат и написать первое сообщение.
               - \\skip - Сбросить текущую цепочку и взять новую из пула.
               - \\exit - Выйти из программы.
               """;
    }

    public String loadNextThread() {
        this.currentThread = repository.getRandomThread();
        if (this.currentThread == null) {
            this.currentThread = new ChatThread();
            return "Анонимных чатов пока нет. Напишите сообщение, чтобы начать новую цепочку!";
        }
        return "Вам пришел анонимный чат!\n" + currentThread.getFormattedHistory() + "\nВведите ваш ответ:";
    }

    public String handleInput(String input) {
        if (input == null || input.isBlank()) {
            return "Сообщение не может быть пустым.";
        }

        String trimmed = input.trim();

        if (trimmed.equalsIgnoreCase("\\help")) {
            return getHelp();
        }

        if (trimmed.equalsIgnoreCase("\\new")) {
            this.currentThread = new ChatThread();
            return "Создан новый чат. Напишите первое сообщение:";
        }

        if (trimmed.equalsIgnoreCase("\\skip")) {
            if (this.currentThread != null && !this.currentThread.getMessages().isEmpty()) {
                repository.saveThread(this.currentThread);
            }
            return loadNextThread();
        }

        if (this.currentThread == null) {
            this.currentThread = new ChatThread();
        }

        this.currentThread.addMessage("Вы (Аноним)", trimmed);

        repository.saveThread(this.currentThread);
        this.currentThread = null;

        return "Ваше сообщение отправлено в эстафету случайному человеку!\n\n" + loadNextThread();
    }
}