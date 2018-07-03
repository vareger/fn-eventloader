package ethereum.eventloader;

import java.math.BigInteger;
import java.util.List;

import org.junit.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.http.HttpService;

/**
 * c:\soft\geth>geth --datadir=c:\work\geth_lt --syncmode=light --ipcpath=geth.ipc
 * c:\soft\geth>geth attach "\\.\pipe\geth.ipc"
 * 
 * geth attach rpc:https://ethfull.betex.io
 */
public class Web3jTest {

	@Test
	public void test_get_logs() throws Exception {
		Web3j w3 = Web3j.build(new HttpService("https://ethfull.betex.io"));
		try {
			EthBlockNumber bn = w3.ethBlockNumber().send();
			System.out.println("Latest block: " + bn.getBlockNumber());
			EthFilter filter = new EthFilter(
					DefaultBlockParameter.valueOf(bn.getBlockNumber().subtract(BigInteger.TEN)),
					DefaultBlockParameter.valueOf(bn.getBlockNumber().subtract(BigInteger.TEN)), 
					(List<String>) null);
			EthLog log = w3.ethGetLogs(filter).send();
			System.out.println("Logs: " + log.getLogs());
		} finally {
			w3.shutdown();
		}
	}
	
}
