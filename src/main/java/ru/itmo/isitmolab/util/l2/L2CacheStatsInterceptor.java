package ru.itmo.isitmolab.util.l2;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.hibernate.stat.Statistics;

import java.util.logging.Level;
import java.util.logging.Logger;

@L2CacheStats
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 50)
public class L2CacheStatsInterceptor {

    private static final Logger log = Logger.getLogger(L2CacheStatsInterceptor.class.getName());

    @Inject
    L2CacheStatsToggle toggle;
    @Inject
    HibernateStatsService statsService;

    @AroundInvoke
    public Object around(InvocationContext ctx) throws Exception {
        if (!toggle.isEnabled()) {
            return ctx.proceed();
        }

        Statistics st;
        try {
            st = statsService.stats();
        } catch (Exception e) {
            log.log(Level.WARNING, "L2 stats unavailable (no Hibernate SessionFactory?)", e);
            return ctx.proceed();
        }

        long hitsBefore = st.getSecondLevelCacheHitCount();
        long missBefore = st.getSecondLevelCacheMissCount();
        long putsBefore = st.getSecondLevelCachePutCount();

        Object res = ctx.proceed();

        long hitsAfter = st.getSecondLevelCacheHitCount();
        long missAfter = st.getSecondLevelCacheMissCount();
        long putsAfter = st.getSecondLevelCachePutCount();

        long dh = hitsAfter - hitsBefore;
        long dm = missAfter - missBefore;
        long dp = putsAfter - putsBefore;

        if (dh != 0 || dm != 0 || dp != 0) {
            log.info(() -> "[L2] " + ctx.getMethod() + ": hits +" + dh + ", miss +" + dm + ", puts +" + dp);
        }

        return res;
    }
}
