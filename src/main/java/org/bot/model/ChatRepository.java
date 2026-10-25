package org.bot.model;

public interface ChatRepository extends AutoCloseable
{
    void saveThread(ChatThread thread);

    ChatThread getRandomThread();

    int getActiveCount();

    @Override
    void close();
}