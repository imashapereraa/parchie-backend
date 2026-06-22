package com.parchie.bdd;

import com.parchie.repository.SessionRepository;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;

public class Hooks {

    @Autowired
    SessionRepository sessionRepository;

    @Autowired
    ScenarioContext context;

    @Before
    public void cleanDatabase() {
        sessionRepository.deleteAll();
    }

    @After
    public void teardownConnections() {
        context.closeAll();
    }
}
