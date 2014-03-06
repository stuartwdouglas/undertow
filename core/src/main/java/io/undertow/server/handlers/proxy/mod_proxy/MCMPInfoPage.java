package io.undertow.server.handlers.proxy.mod_proxy;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.mod_proxy.container.Context;
import io.undertow.server.handlers.proxy.mod_proxy.container.Node;
import io.undertow.server.handlers.proxy.mod_proxy.container.SessionId;
import io.undertow.server.handlers.proxy.mod_proxy.container.VHost;
import io.undertow.util.HttpString;

import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that handles the creation of the mod cluster information page by generating raw HTML.
 *
 * This logic has been moved out of {@link MCMPHandler} to remove all the HTML clutter.
 *
 *
 * @author Stuart Douglas
 */
public class MCMPInfoPage {

    private final MCMPHandler mcmpHandler;

    public MCMPInfoPage(MCMPHandler mcmpHandler) {
        this.mcmpHandler = mcmpHandler;
    }

    /*
         * list the session informations.
         */
    void printInfoSessions(StringBuilder buf, List<SessionId> sessionids) {
        buf.append("<h1>SessionIDs:</h1>");
        buf.append("<pre>");
        for (SessionId s : sessionids) {
            buf.append("id: ").append(s.getSessionId()).append(" route: ").append(s.getJmvRoute()).append("\n");
        }
        buf.append("</pre>");
    }

    /* based on manager_info_hosts */
    void printInfoHost(StringBuilder buf, String uri, boolean reduceDisplay, boolean allowCmd, String jvmRoute) {
        for (VHost host : mcmpHandler.getConf().getHosts()) {
            if (host.getJVMRoute().equals(jvmRoute)) {
                if (!reduceDisplay) {
                    buf.append("<h2> Virtual Host ").append(host.getId()).append(":</h2>");
                }
                printInfoContexts(buf, uri, reduceDisplay, allowCmd, host.getId(), host.getAliases(), jvmRoute);
                if (reduceDisplay) {
                    buf.append("Aliases: ");
                    for (String alias : host.getAliases()) {
                        buf.append(alias).append(" ");
                    }
                } else {
                    buf.append("<h3>Aliases:</h3>");
                    buf.append("<pre>");
                    for (String alias : host.getAliases()) {
                        buf.append(alias).append("\n");
                    }
                    buf.append("</pre>");
                }

            }
        }

    }

    /* based on manager_info_contexts */
    private void printInfoContexts(StringBuilder buf, String uri, boolean reduceDisplay, boolean allowCmd, long host, String[] alias, String jvmRoute) {
        if (!reduceDisplay) {
            buf.append("<h3>Contexts:</h3>");
        }
        buf.append("<pre>");
        for (Context context : mcmpHandler.getConf().getContexts()) {
            if (context.getJVMRoute().equals(jvmRoute) && context.getHostid() == host) {
                String status = "REMOVED";
                switch (context.getStatus()) {
                    case ENABLED:
                        status = "ENABLED";
                        break;
                    case DISABLED:
                        status = "DISABLED";
                        break;
                    case STOPPED:
                        status = "STOPPED";
                        break;
                }
                buf.append(context.getPath()).append(" , Status: ").append(status).append(" Request: ").append(context.getNbRequests()).append(" ");
                if (allowCmd) {
                    contextCommandString(buf, uri, context.getStatus(), context.getPath(), alias, jvmRoute);
                }
                buf.append("\n");
            }
        }
        buf.append("</pre>");
    }

    /* generate a command URL for the context */
    private void contextCommandString(StringBuilder buf, String uri, Context.Status status, String path, String[] alias, String jvmRoute) {
        switch (status) {
            case DISABLED:
                buf.append("<a href=\"").append(uri).append("?").append(mcmpHandler.getNonce()).append("&Cmd=ENABLE-APP&Range=CONTEXT&");
                contextString(buf, path, alias, jvmRoute);
                buf.append("\">Enable</a> ");
                break;
            case ENABLED:
                buf.append("<a href=\"").append(uri).append("?").append(mcmpHandler.getNonce()).append("&Cmd=DISABLE-APP&Range=CONTEXT&");
                contextString(buf, path, alias, jvmRoute);
                buf.append("\">Disable</a> ");
                break;
        }
    }

    private void contextString(StringBuilder buf, String path, String[] alias, String jvmRoute) {
        buf.append("JVMRoute=").append(jvmRoute).append("&Alias=");
        boolean first = true;
        for (String a : alias) {
            if (first) {
                first = false;
            } else {
                buf.append(",");
            }
            buf.append(a);
        }
        buf.append("&Context=").append(path);
    }

    void printProxyStat(StringBuilder buf, Node node, boolean reduceDisplay) {
        String status = "NOTOK";
        if (node.getStatus() == Node.NodeStatus.NODE_UP) {
            status = "OK";
        }
        if (reduceDisplay) {
            buf.append(" ").append(status).append(" ");
        }
        else {
            buf.append(",Status: ").append(status).append(",Elected: ").append(node.getOldelected()).append(",Read: ").append(node.getRead()).append(",Transferred: ").append(node.getTransfered()).append(",Connected: ").append(node.getConnected()).append(",Load: ").append(node.getLoad());
        }
    }

    void nodeCommandString(StringBuilder buf, String uri, Context.Status status, String jvmRoute, MCMPHandler mcmpHandler) {
        switch (status) {
            case ENABLED:
                buf.append("<a href=\"").append(uri).append("?").append(mcmpHandler.getNonce()).append("&Cmd=ENABLE-APP&Range=NODE&JVMRoute=").append(jvmRoute).append("\">Enable Contexts</a> ");
                break;
            case DISABLED:
                buf.append("<a href=\"").append(uri).append("?").append(mcmpHandler.getNonce()).append("&Cmd=DISABLE-APP&Range=NODE&JVMRoute=").append(jvmRoute).append("\">Disable Contexts</a> ");
                break;
        }
    }

    void processManager(HttpServerExchange exchange) throws Exception {

        Map<String, Deque<String>> params = exchange.getQueryParameters();
        boolean hasNonce = params.containsKey("nonce");
        int refreshTime = 0;
        if (mcmpHandler.isCheckNonce()) {
            /* Check the nonce */
            if (hasNonce) {
                String receivedNonce = params.get("nonce").getFirst();
                if (receivedNonce.equals(mcmpHandler.getRawNonce())) {
                    boolean refresh = params.containsKey("refresh");
                    if (refresh) {
                        String sval = params.get("refresh").getFirst();
                        refreshTime = Integer.parseInt(sval);
                        if (refreshTime < 10) {
                            refreshTime = 10;
                        }
                        exchange.getResponseHeaders().add(Constants.REFRESH_HEADER, Integer.toString(refreshTime));
                    }
                    boolean cmd = params.containsKey("Cmd");
                    boolean range = params.containsKey("Range");
                    if (cmd) {
                        String scmd = params.get("Cmd").getFirst();
                        if (scmd.equals("INFO")) {
                            mcmpHandler.processInfo(exchange);
                            return;
                        } else if (scmd.equals("DUMP")) {
                            mcmpHandler.processDump(exchange);
                            return;
                        } else if (scmd.equals("ENABLE-APP") && range) {
                            String srange = params.get("Range").getFirst();
                            Map<String, String[]> mparams = buildMap(params);
                            if (srange.equals("NODE")) {
                                mcmpHandler.processNodeCmd(exchange, mparams, Context.Status.ENABLED);
                            }
                            if (srange.equals("DOMAIN")) {
                                boolean domain = params.containsKey("Domain");
                                if (domain) {
                                    String sdomain = params.get("Domain").getFirst();
                                    processDomainCmd(exchange, sdomain, Context.Status.ENABLED);
                                }
                            }
                            if (srange.equals("CONTEXT")) {
                                mcmpHandler.processCmd(exchange, mparams, Context.Status.ENABLED);
                            }
                        } else if (scmd.equals("DISABLE-APP") && range) {
                            String srange = params.get("Range").getFirst();
                            Map<String, String[]> mparams = buildMap(params);
                            if (srange.equals("NODE")) {
                                mcmpHandler.processNodeCmd(exchange, mparams, Context.Status.DISABLED);
                            }
                            if (srange.equals("DOMAIN")) {
                                boolean domain = params.containsKey("Domain");
                                if (domain) {
                                    String sdomain = params.get("Domain").getFirst();
                                    processDomainCmd(exchange, sdomain, Context.Status.DISABLED);
                                }
                            }
                            if (srange.equals("CONTEXT")) {
                                mcmpHandler.processCmd(exchange, mparams, Context.Status.DISABLED);
                            }

                        }
                    }
                }
            }
        }

        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/html; charset=ISO-8859-1");
        StringBuilder buf = new StringBuilder();
        buf.append("<html><head>\n<title>Mod_cluster Status</title>\n</head><body>\n");
        buf.append("<h1>").append(MCMPHandler.MOD_CLUSTER_EXPOSED_VERSION).append("</h1>");

        String uri = exchange.getRequestPath();
        String nonce = mcmpHandler.getNonce();
        if (refreshTime <= 0) {
            buf.append("<a href=\"").append(uri).append("?").append(nonce).append("&refresh=10\">Auto Refresh</a>");
        }

        buf.append(" <a href=\"").append(uri).append("?").append(nonce).append("&Cmd=DUMP&Range=ALL\">show DUMP output</a>");

        buf.append(" <a href=\"").append(uri).append("?").append(nonce).append("&Cmd=INFO&Range=ALL\">show INFO output</a>");

        buf.append("\n");

        /* TODO sort the node by LBGroup (domain) */
        String lbgroup = "";
        for (Node node : mcmpHandler.getConf().getNodes()) {
            if (!lbgroup.equals(node.getDomain())) {
                lbgroup = node.getDomain();
                if (mcmpHandler.isReduceDisplay()) {
                    buf.append("<br/><br/>LBGroup ").append(lbgroup).append(": ");
                } else {
                    buf.append("<h1> LBGroup ").append(lbgroup).append(": ");
                }
                if (mcmpHandler.isAllowCmd()) {
                    domainCommandString(buf, uri, Context.Status.ENABLED, lbgroup);
                    domainCommandString(buf, uri, Context.Status.DISABLED, lbgroup);
                }
            }
            if (mcmpHandler.isReduceDisplay()) {
                buf.append("<br/><br/>Node ").append(node.getJvmRoute());
                printProxyStat(buf, node, mcmpHandler.isReduceDisplay());
            } else
                buf.append("<h1> Node ").append(node.getJvmRoute()).append(" (").append(node.getType()).append("://").append(node.getHostname()).append(":").append(node.getPort()).append("): </h1>\n");


            if (mcmpHandler.isAllowCmd()) {
                nodeCommandString(buf, uri, Context.Status.ENABLED, node.getJvmRoute(), mcmpHandler);
                nodeCommandString(buf, uri, Context.Status.DISABLED, node.getJvmRoute(), mcmpHandler);
            }
            if (!mcmpHandler.isReduceDisplay()) {
                buf.append("<br/>\n");
                buf.append("Balancer: ").append(node.getBalancer()).append(",LBGroup: ").append(node.getDomain());
                String flushpackets = "off";
                if (node.isFlushpackets()) {
                    flushpackets = "Auto";
                }
                buf.append(",Flushpackets: ").append(flushpackets).append(",Flushwait: ").append(node.getFlushwait()).append(",Ping: ").append(node.getPing()).append(" ,Smax: ").append(node.getPing()).append(",Ttl: ").append(node.getTtl());
                printProxyStat(buf, node, mcmpHandler.isReduceDisplay());
            } else {
                buf.append("<br/>\n");
            }
            // the sessionid list is mostly for demos.
            if (mcmpHandler.isDisplaySessionIds()) {
                buf.append(",Num sessions: ").append(mcmpHandler.getConf().getJVMRouteSessionCount(node.getJvmRoute()));
            }
            buf.append("\n");

            // Process the virtual-host of the node
            printInfoHost(buf, uri, mcmpHandler.isReduceDisplay(), mcmpHandler.isAllowCmd(), node.getJvmRoute());
        }

        // Display the all the actives sessions
        if (mcmpHandler.isDisplaySessionIds()) {
            printInfoSessions(buf, mcmpHandler.getConf().getSessionids());
        }

        buf.append("</body></html>\n");
        exchange.getResponseSender().send(buf.toString(), MCMPHandler.ISO_8859_1);
    }


    private void processDomainCmd(HttpServerExchange exchange, String domain, Context.Status status) throws Exception {
        for (Node node : mcmpHandler.getConf().getNodes()) {
            if (node.getDomain().equals(domain)) {
                Map<String, String[]> params = new HashMap<String, String[]>();
                String[] values = new String[1];
                values[0] = node.getJvmRoute();
                params.put("JVMRoute", values);
                mcmpHandler.processNodeCmd(exchange, params, status);
            }
        }
    }

    private Map<String, String[]> buildMap(Map<String, Deque<String>> params) {
        Map<String, String[]> sparams = new HashMap<String, String[]>();
        for (String key : params.keySet()) {
            // In fact we only have one
            String[] values = new String[1];
            values[0] = params.get(key).getFirst();
            sparams.put(key, values);
        }
        return sparams;
    }

    /* based on domain_command_string */
    private void domainCommandString(StringBuilder buf, String uri, Context.Status status, String lbgroup) {
        switch (status) {
            case ENABLED:
                buf.append("<a href=\"").append(uri).append("?").append(mcmpHandler.getNonce()).append("&Cmd=ENABLE-APP&Range=DOMAIN&Domain=").append(lbgroup).append("\">Enable Nodes</a>");
                break;
            case DISABLED:
                buf.append("<a href=\"").append(uri).append("?").append(mcmpHandler.getNonce()).append("&Cmd=DISABLE-APP&Range=DOMAIN&Domain=").append(lbgroup).append("\">Disable Nodes</a>");
                break;
        }
    }
}
