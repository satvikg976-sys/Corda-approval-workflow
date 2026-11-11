
package com.template.webserver;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.utilities.NetworkHostAndPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.HashMap;
import java.util.Map;

@Component
public class NodeRPCConnection implements AutoCloseable {

    @Value("${config.rpc.host}")
    private String host;

    @Value("${config.rpc.username}")
    private String username;

    @Value("${config.rpc.password}")
    private String password;

    @Value("${config.rpc.port}")
    private int rpcPort;

    @Value("${config.rpc.partyB.port:10009}")
    private int partyBPort;

    @Value("${config.rpc.partyC.port:10012}")
    private int partyCPort;

    @Value("${config.rpc.partyD.port:10015}")
    private int partyDPort;

    private final Map<String, CordaRPCConnection> connections = new HashMap<>();
    private final Map<String, CordaRPCOps> proxies = new HashMap<>();

    // Existing PartyA proxy, retained for compatibility.
    CordaRPCOps proxy;

    @PostConstruct
    public void initialiseNodeRPCConnection() {
        proxy = connect("PartyA", rpcPort);
    }

    public synchronized CordaRPCOps getProxy(String node) {

        String name = node.trim().toLowerCase();

        if (proxies.containsKey(name)) {
            return proxies.get(name);
        }

        int port;

        switch (name) {
            case "partya":
                port = rpcPort;
                break;
            case "partyb":
                port = partyBPort;
                break;
            case "partyc":
                port = partyCPort;
                break;
            case "partyd":
                port = partyDPort;
                break;
            default:
                throw new IllegalArgumentException("Unknown Corda node: " + node);
        }

        return connect(name, port);
    }

    private synchronized CordaRPCOps connect(String node, int port) {

        String name = node.toLowerCase();

        if (proxies.containsKey(name)) {
            return proxies.get(name);
        }

        NetworkHostAndPort address = new NetworkHostAndPort(host, port);

        CordaRPCClient client = new CordaRPCClient(address);

        CordaRPCConnection connection = client.start(username, password);

        CordaRPCOps nodeProxy = connection.getProxy();

        connections.put(name, connection);
        proxies.put(name, nodeProxy);

        return nodeProxy;
    }

    @PreDestroy
    @Override
    public synchronized void close() {

        for (CordaRPCConnection connection : connections.values()) {
            try {
                connection.notifyServerAndClose();
            } catch (Exception e) {
                System.err.println("Error closing Corda RPC connection: " + e.getMessage());
            }
        }

        connections.clear();
        proxies.clear();
    }
}
