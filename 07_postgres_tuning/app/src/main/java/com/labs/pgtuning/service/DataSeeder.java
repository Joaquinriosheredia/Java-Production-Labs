package com.labs.pgtuning.service;

import com.labs.pgtuning.entity.Event;
import com.labs.pgtuning.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final EventRepository repository;
    private final Random random = new Random(42);

    public DataSeeder(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void seed(int count) {
        log.info("Seeding {} events...", count);
        List<Event> batch = new ArrayList<>(1000);
        for (int i = 0; i < count; i++) {
            Event e = new Event();
            e.setUserId("user-" + (i % 1000));
            e.setEventType(i % 3 == 0 ? "LOGIN" : i % 3 == 1 ? "PURCHASE" : "LOGOUT");
            // ~5% PENDING, 90% PROCESSED, 5% FAILED (realistic distribution)
            int r = random.nextInt(100);
            e.setStatus(r < 5 ? Event.EventStatus.PENDING :
                        r < 95 ? Event.EventStatus.PROCESSED : Event.EventStatus.FAILED);
            e.setOccurredAt(Instant.now().minus(random.nextInt(365), ChronoUnit.DAYS));
            e.setPayload("{\"i\":" + i + "}");
            batch.add(e);
            if (batch.size() == 1000) {
                repository.saveAll(batch);
                batch.clear();
                log.debug("Seeded {} rows", i + 1);
            }
        }
        if (!batch.isEmpty()) repository.saveAll(batch);
        log.info("Seeding complete. Total rows: {}", repository.count());
    }
}
