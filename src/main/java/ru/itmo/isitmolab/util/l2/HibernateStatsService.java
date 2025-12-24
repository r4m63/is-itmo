package ru.itmo.isitmolab.util.l2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

@ApplicationScoped
public class HibernateStatsService {

    @PersistenceUnit
    EntityManagerFactory emf;

    public Statistics stats() {
        SessionFactory sf = emf.unwrap(SessionFactory.class);
        Statistics st = sf.getStatistics();
        if (!st.isStatisticsEnabled()) {
            st.setStatisticsEnabled(true);
        }
        return st;
    }
}
