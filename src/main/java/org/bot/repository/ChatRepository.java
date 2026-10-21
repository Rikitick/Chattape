package org.bot.repository;

import org.bot.model.ChatThread;
import java.util.List;

public interface ChatRepository {
    void saveThread(ChatThread thread);
    ChatThread getRandomThread();
    ChatThread findAvailableThreadForUser(String userId);
    List<ChatThread> findThreadsByParticipant(String userId);
    int getActiveCount();
}