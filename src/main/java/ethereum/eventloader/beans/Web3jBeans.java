package ethereum.eventloader.beans;

import ethereum.eventloader.config.Web3jConfig;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.ipc.UnixIpcService;
import org.web3j.protocol.ipc.WindowsIpcService;
import org.web3j.protocol.websocket.WebSocketService;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

@Component
public class Web3jBeans {

    private static final Logger log = LoggerFactory.getLogger(Web3jBeans.class);

    private final Web3jConfig config;

    @Autowired
    public Web3jBeans(Web3jConfig config) {
        this.config = config;
    }

    public Web3j web3j() {
        log.info("Building service for endpoint: " + config.getClientAddress());
        Web3jService web3jService = buildService(config.getClientAddress());
        return Web3j.build(web3jService);
    }

    private Web3jService buildService(String clientAddress) {
        Web3jService web3jService;

        if (clientAddress == null || clientAddress.equals("")) {
            web3jService = new HttpService(createOkHttpClient());
        } else if (clientAddress.startsWith("http") || clientAddress.startsWith("https")) {
            web3jService = new HttpService(clientAddress, createOkHttpClient(), false);
        } else if(clientAddress.startsWith("ws") || clientAddress.startsWith("wss")) {
            web3jService = new WebSocketService(clientAddress, false);
            try {
                ((WebSocketService) web3jService).connect();
            } catch (ConnectException e) {
                log.error("Cannot connect to web socket", e);
            }
        } else if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
            web3jService = new WindowsIpcService(clientAddress);
        } else {
            web3jService = new UnixIpcService(clientAddress);
        }

        return web3jService;
    }

    private OkHttpClient createOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        configureTimeouts(builder);
        return builder.build();
    }

    private void configureTimeouts(OkHttpClient.Builder builder) {
        Long tos = config.getHttpTimeoutSeconds();
        if (tos != null) {
            builder.connectTimeout(tos, TimeUnit.SECONDS);
            builder.readTimeout(tos, TimeUnit.SECONDS);  // Sets the socket timeout too
            builder.writeTimeout(tos, TimeUnit.SECONDS);
        }
    }

}
