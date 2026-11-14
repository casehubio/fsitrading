package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.service.OrderSemaphoreService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FsiOrderApiTest {

    @Test
    void haltPreservesAllRequestFields() {
        var api = new FsiOrderApi();
        api.semaphoreService = new OrderSemaphoreService();

        var ticket = api.halt(new FsiOrderApi.HaltRequest(
                "risk-limit", "INSTRUMENT", "AAPL", "playbook-1"));

        assertThat(ticket.reason()).isEqualTo("risk-limit");
        assertThat(ticket.scope()).isEqualTo("INSTRUMENT");
        assertThat(ticket.instrument()).isEqualTo("AAPL");
        assertThat(ticket.playbookInstanceId()).isEqualTo("playbook-1");
        assertThat(api.semaphoreService.haltCount()).isEqualTo(1);
    }
}
