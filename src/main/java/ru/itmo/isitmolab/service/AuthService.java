package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.AdminDao;
import ru.itmo.isitmolab.dto.CredsDto;

import java.util.Map;

/**
 * Сервис аутентификации/сессий для административной части.
 *
 * <h3>Задачи</h3>
 * <ul>
 *   <li>Проверка учетных данных и старт серверной сессии;</li>
 *   <li>Проверка активности сессии (health для фронта);</li>
 *   <li>Завершение сессии (logout) и очистка cookie;</li>
 *   <li>Получение текущего идентификатора пользователя из сессии.</li>
 * </ul>
 *
 * <h3>Безопасность (для продакшена)</h3>
 * <ul>
 *   <li><b>Хранение паролей:</b> в БД должны храниться только сильные хэши паролей
 *       (например, Argon2id/BCrypt/SCrypt с солью). Никаких «сырьевых» паролей.</li>
 *   <li><b>Сравнение хэшей:</b> используйте константное по времени сравнение (в DAO/утилите).</li>
 *   <li><b>Сессии:</b> при входе <i>обязательно</i> регенерировать session id
 *       (защита от session fixation) и задавать cookie флаги: HttpOnly, Secure, SameSite=strict|lax.</li>
 *   <li><b>CSRF:</b> для cookie-сессий включать CSRF-защиту (токен/заголовок, double-submit или SameSite).</li>
 *   <li><b>Брутфорс:</b> ограничивать частоту логинов (rate limit, backoff, captcha, IP-throttling).</li>
 *   <li><b>Аудит:</b> логировать успешные/неуспешные попытки входа (без логирования секретов).</li>
 * </ul>
 */
@ApplicationScoped
public class AuthService {

    @Inject
    AdminDao adminDao;

    @Inject
    SessionService sessionService;

    /**
     * Аутентифицирует пользователя по логину и паролю и запускает серверную сессию.
     *
     * <p><b>Контракт:</b>
     * <ul>
     *   <li>Ищет администратора по логину и хэшу пароля (см. {@link AdminDao#findByLoginAndPassHash(String, String)});</li>
     *   <li>При успехе — инициирует сессию через {@link SessionService#startSession(HttpServletRequest, Long)};</li>
     *   <li>При неуспехе — бросает 401 Unauthorized с телом <code>{"message":"Invalid credentials"}</code>.</li>
     * </ul>
     * </p>
     *
     * <p><b>Побочные эффекты:</b> Создает/обновляет серверную сессию и соответствующую session cookie.</p>
     *
     * @param creds DTO с логином и паролем (пароль — сырой ввод, хэширование/проверка — в DAO/утилите)
     * @param req   HTTP-запрос (используется для управления сессией/куки)
     *
     * @throws WebApplicationException 401 — если пара логин/пароль не валидна
     */
    public void login(CredsDto creds, HttpServletRequest req) {
        var admin = adminDao.findByLoginAndPassHash(creds.getLogin(), creds.getPassword())
                .orElseThrow(() -> new WebApplicationException(
                        Response.status(Response.Status.UNAUTHORIZED)
                                .entity(Map.of("message", "Invalid credentials"))
                                .build()
                ));
        // Важно: внутри startSession следует регенерировать session id и проставлять безопасные флаги cookie
        sessionService.startSession(req, admin.getId());
    }

    /**
     * Проверяет, активна ли сессия текущего запроса.
     *
     * <p>Полезно для фронтенда, чтобы понять, требуется ли редирект на форму логина.</p>
     *
     * @param req HTTP-запрос
     * @return {@code true} — если пользователь аутентифицирован; иначе {@code false}
     */
    public boolean isSessionActive(HttpServletRequest req) {
        return sessionService.isActive(req);
    }

    /**
     * Завершает сессию пользователя и очищает клиентские куки.
     *
     * <p><b>Побочные эффекты:</b> Инвалидирует серверную сессию и удаляет/протухает session cookie.</p>
     *
     * @param req HTTP-запрос (для доступа к текущей сессии)
     * @param res HTTP-ответ (для удаления cookie/заголовков)
     */
    public void logout(HttpServletRequest req, HttpServletResponse res) {
        sessionService.destroySession(req, res);
    }

    /**
     * Возвращает идентификатор текущего пользователя из серверной сессии.
     *
     * <p>Метод не инициирует сессию, а только читает состояние.
     * Используйте вместе с {@link #isSessionActive(HttpServletRequest)} либо обрабатывайте {@code null} на уровне вызова.</p>
     *
     * @param req HTTP-запрос с активной сессией
     * @return идентификатор пользователя (может быть {@code null}, если сессии нет)
     */
    public Long currentUserId(HttpServletRequest req) {
        return sessionService.getCurrentUserId(req);
    }
}
