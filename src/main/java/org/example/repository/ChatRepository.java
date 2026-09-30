package org.example.repository;

import org.example.model.ChatThread;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChatRepository {
    private final List<ChatThread> activeThreads = new ArrayList<>();
    private final Random random = new Random();

    public ChatRepository() {
        // Заготовим пару демо-цепочек для тестирования
        ChatThread demo1 = new ChatThread();
        demo1.addMessage("Незнакомец #42", "Привет! Какая у тебя погода?");
        activeThreads.add(demo1);

        ChatThread demo2 = new ChatThread();
        demo2.addMessage("Незнакомец #11", "Какой твой любимый фильм?");
        demo2.addMessage("Незнакомец #89", "Интерстеллар! А твой?");
        activeThreads.add(demo2);
    }

    public void saveThread(ChatThread thread) {
        activeThreads.add(thread);
    }

    public ChatThread getRandomThread() {
        if (activeThreads.isEmpty()) {
            return null;
        }
        int index = random.nextInt(activeThreads.size());
        return activeThreads.remove(index);
    }

    public int getActiveCount() {
        return activeThreads.size();
    }
}