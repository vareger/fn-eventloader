package ethereum.eventloader.impl;

import ethereum.eventloader.component.BlockchainAdapter;
import ethereum.eventloader.component.entity.Events;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@TestPropertySource("classpath:test.yml")
@ContextConfiguration(classes = ImplTC.class)
public class Web3jBlockchainTest extends Assert {

    @Autowired
    BlockchainAdapter blockchain;

    @Test
    public void test_get_latest_block_number() throws Exception {
        long num = blockchain.latestBlockNumber();
        assertTrue(num > 100);
    }

    @Test
    public void test_get_logs_for_single_block() throws Exception {
        long latest = blockchain.latestBlockNumber();
        Events events = blockchain.eventsLog(latest - 10, latest - 9);
        assertFalse(events.getLogs(0).isEmpty());
    }

    @Test
    public void test_get_logs_for_two_blocks() throws Exception {
        long latest = blockchain.latestBlockNumber();
        Events events = blockchain.eventsLog(latest - 10, latest - 8);
        assertFalse(events.getLogs(0).isEmpty());
    }

    @Test
    public void test_get_logs_for_many_blocks() throws Exception {
        long latest = blockchain.latestBlockNumber();
        Events events = blockchain.eventsLog(latest - 20, latest - 5);
        assertFalse(events.getLogs(0).isEmpty());
    }

}
