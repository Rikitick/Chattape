package org.bot.repository;

import org.bot.model.MessageEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.NativeQuery;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.List;

/**
 * Реализация репозитория на Hibernate/JPA.
 * Добавлено: замена raw JDBC на ORM (Hibernate 6) с JPA-аннотациями.
 */
public class HibernateChatRepository implements ChatRepository, AutoCloseable {

    private static final String DEFAULT_URL = "jdbc:sqlite:./chattape.db";
    private final SessionFactory sessionFactory;

    public HibernateChatRepository() {
        this(DEFAULT_URL);
    }

    public HibernateChatRepository(String dbUrl) {
        this.sessionFactory = createSessionFactory(dbUrl);
        initializeSchema();
    }

    private SessionFactory createSessionFactory(String dbUrl) {
        org.hibernate.cfg.Configuration config = new org.hibernate.cfg.Configuration();
        config.setProperty("hibernate.connection.url", dbUrl);
        config.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
        config.setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        config.setProperty("hibernate.hbm2ddl.auto", "update");
        config.setProperty("hibernate.show_sql", "false");
        config.setProperty("hibernate.format_sql", "true");
        config.setProperty("jakarta.persistence.sharedCache.mode", "DISABLE_SELECTIVE");
        config.setProperty("hibernate.cache.use_second_level_cache", "false");
        config.setProperty("hibernate.cache.use_query_cache", "false");
        config.setProperty("hibernate.jdbc.batch_size", "0");
        config.addAnnotatedClass(MessageEntity.class);
        return config.buildSessionFactory();
    }

    private void initializeSchema() {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS chats (id INTEGER PRIMARY KEY AUTOINCREMENT)"
            ).executeUpdate();
            session.createNativeQuery(
                    "INSERT OR IGNORE INTO chats (id) SELECT DISTINCT chat_id FROM messages WHERE chat_id > 0"
            ).executeUpdate();
            session.createNativeQuery(
                    "CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id)"
            ).executeUpdate();
            session.createNativeQuery(
                    "CREATE INDEX IF NOT EXISTS idx_messages_user_id ON messages(user_id)"
            ).executeUpdate();
            tx.commit();
        }
    }

    @Override
    public synchronized int createChat() {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.createNativeQuery("INSERT INTO chats DEFAULT VALUES").executeUpdate();
            Number rowId = (Number) session.createNativeQuery("SELECT last_insert_rowid()").getSingleResult();
            tx.commit();
            return rowId.intValue();
        }
    }

    @Override
    public List<MessageEntity> getRandomChat(long userId) {
        String sql = "SELECT id, chat_id, user_id, name_sender, text " +
                "FROM messages WHERE chat_id = (" +
                "SELECT latest.chat_id FROM messages AS latest " +
                "WHERE latest.rowid = (SELECT MAX(newer.rowid) FROM messages AS newer " +
                "WHERE newer.chat_id = latest.chat_id) " +
                "AND latest.user_id <> ? " +
                "AND latest.chat_id NOT IN (SELECT chat_id FROM messages WHERE user_id = ?) " +
                "ORDER BY RANDOM() LIMIT 1) ORDER BY rowid ASC";

        try (Session session = sessionFactory.openSession()) {
            NativeQuery<MessageEntity> query = session.createNativeQuery(sql, MessageEntity.class);
            query.setParameter(1, userId);
            query.setParameter(2, userId);
            return query.getResultList();
        }
    }

    @Override
    public synchronized void addMessage(MessageEntity message) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(message);
            tx.commit();
        }
    }

    @Override
    public int getChatSize(int chatId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<MessageEntity> root = cq.from(MessageEntity.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("chatId"), chatId));
            return session.createQuery(cq).getSingleResult().intValue();
        }
    }

    @Override
    public List<Integer> getUserChats(long userId) {
        String sql = "SELECT DISTINCT chat_id FROM messages WHERE user_id = ? ORDER BY chat_id";
        try (Session session = sessionFactory.openSession()) {
            List<Number> result = session.createNativeQuery(sql)
                    .setParameter(1, userId)
                    .getResultList();
            return result.stream().map(Number::intValue).toList();
        }
    }

    @Override
    public List<MessageEntity> getChatById(int chatId) {
        String sql = "SELECT id, chat_id, user_id, name_sender, text " +
                "FROM messages WHERE chat_id = ? ORDER BY rowid ASC";
        try (Session session = sessionFactory.openSession()) {
            return session.createNativeQuery(sql, MessageEntity.class)
                    .setParameter(1, chatId)
                    .getResultList();
        }
    }

    @Override
    public void close() {
        sessionFactory.close();
    }
}
