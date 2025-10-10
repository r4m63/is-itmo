package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Утилитный сервис для управления серверной HTTP-сессией.
 *
 * <h3>Назначение</h3>
 * <ul>
 *   <li>Старт и завершение сессии (login/logout);</li>
 *   <li>Проверка активности сессии;</li>
 *   <li>Чтение идентификатора текущего пользователя из атрибутов сессии.</li>
 * </ul>
 *
 * <h3>Рекомендации для продакшена</h3>
 * <ul>
 *   <li><b>Session fixation:</b> при успешном логине обязательно регенерируйте идентификатор сессии
 *       (например, через {@code request.changeSessionId()} или инвалидировать/создать новую).</li>
 *   <li><b>Cookie flags:</b> для session cookie выставляйте
 *       <code>HttpOnly</code>, <code>Secure</code> (только по HTTPS), и <code>SameSite</code> (Lax/Strict).</li>
 *   <li><b>Таймауты:</b> задайте idle timeout для сессии (например, в web.xml или программно
 *       {@code session.setMaxInactiveInterval(...)}) и при необходимости absolute timeout.</li>
 *   <li><b>Очистка данных:</b> при logout удаляйте/протухайте cookie и инвалидируйте серверную сессию.</li>
 *   <li><b>Минимизация данных в сессии:</b> храните только идентификаторы и минимально необходимые сведения.</li>
 * </ul>
 */
@ApplicationScoped
public class SessionService {

    /** Имя атрибута сессии, в котором хранится ID пользователя. */
    public static final String ATTR_USER_ID = "userId";

    /**
     * Стартует (или переиспользует) серверную сессию и записывает идентификатор пользователя.
     *
     * <p><b>Прод-заметка:</b> для защиты от session fixation после успешной аутентификации
     * стоит регенерировать session id (например, {@code req.changeSessionId()}).</p>
     *
     * @param req    текущий HTTP-запрос
     * @param userId идентификатор аутентифицированного пользователя (не {@code null})
     */
    public void startSession(HttpServletRequest req, Long userId) {
        var s = req.getSession(true);          // создаём сессию при отсутствии
        s.setAttribute(ATTR_USER_ID, userId);  // сохраняем ID пользователя
        // Рекомендуется: req.changeSessionId(); // регенерировать ID после логина (если контейнер поддерживает)
    }

    /**
     * Проверяет, активна ли сессия: существует ли сессия и установлен ли в ней {@link #ATTR_USER_ID}.
     *
     * @param req текущий HTTP-запрос
     * @return {@code true}, если пользователь залогинен; иначе {@code false}
     */
    public boolean isActive(HttpServletRequest req) {
        var s = req.getSession(false); // не создаём новую сессию
        if (s == null) return false;
        var uid = s.getAttribute(ATTR_USER_ID);
        return (uid instanceof Number);
    }

    /**
     * Возвращает ID текущего пользователя из сессии.
     *
     * @param req текущий HTTP-запрос
     * @return {@code Long} ID пользователя или {@code null}, если сессии нет или атрибут отсутствует
     */
    public Long getCurrentUserId(HttpServletRequest req) {
        var s = req.getSession(false);
        if (s == null) return null;
        var uid = s.getAttribute(ATTR_USER_ID);
        return (uid instanceof Number) ? ((Number) uid).longValue() : null;
    }

    /**
     * Завершает сессию: инвалидирует серверную сессию и удаляет session cookie у клиента.
     *
     * <p><b>Прод-заметки:</b>
     * <ul>
     *   <li>Если у вас кастомное имя cookie или несколько доменов/путей — удалите все соответствующие cookie.</li>
     *   <li>Стоит выставлять те же флаги (path/domain/Secure/SameSite), что и при установке, иначе некоторые браузеры
     *       могут не удалить cookie.</li>
     * </ul>
     * </p>
     *
     * @param req текущий HTTP-запрос
     * @param res текущий HTTP-ответ (для отправки «протухшей» cookie)
     */
    public void destroySession(HttpServletRequest req, HttpServletResponse res) {
        var s = req.getSession(false);
        if (s != null) s.invalidate(); // инвалидируем серверную сессию

        // Удаляем JSESSIONID на стороне клиента (протухаем cookie)
        Cookie c = new Cookie("JSESSIONID", "");
        c.setPath("/");           // должен совпадать с исходным path
        c.setMaxAge(0);           // удалить сразу
        c.setHttpOnly(true);      // защищаем от JS-доступа
        // Рекомендуется также:
        // c.setSecure(true);     // только по HTTPS
        // c.setDomain("example.com"); // если задавали при установке
        // c.setAttribute("SameSite", "Lax"); // или "Strict" в зависимости от UX
        res.addCookie(c);
    }
}
