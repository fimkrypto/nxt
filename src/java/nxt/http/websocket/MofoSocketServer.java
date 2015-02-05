package nxt.http.websocket;

import static nxt.http.JSONResponses.ERROR_INCORRECT_REQUEST;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nxt.Block;
import nxt.NxtException;
import nxt.Trade;
import nxt.Transaction;
import nxt.crypto.Crypto;
import nxt.http.ParameterException;
import nxt.http.rpc.CallAPIFunction;
import nxt.http.rpc.GetAccount;
import nxt.http.rpc.GetAccountAssets;
import nxt.http.rpc.GetAccountComments;
import nxt.http.rpc.GetAccountCurrencies;
import nxt.http.rpc.GetAccounts;
import nxt.http.rpc.GetActivity;
import nxt.http.rpc.GetActivityStatistics;
import nxt.http.rpc.GetForgingStats;
import nxt.http.rpc.GetRecentTransactions;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

@SuppressWarnings("unchecked")
public class MofoSocketServer {
  
    static final ExecutorService threadPool = Executors.newFixedThreadPool(2);
    
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                threadPool.shutdownNow();
            }
        }));
    }    
    
    static final Map<String, Set<MofoWebSocketAdapter>> listeners  = new HashMap<String, Set<MofoWebSocketAdapter>>();
    static final Map<MofoWebSocketAdapter, Set<String>> reverse_listeners = new HashMap<MofoWebSocketAdapter, Set<String>>();
    
    static final Map<String, RPCCall> rpcCalls = new HashMap<String, RPCCall>();

    static JSONObject ERROR_NO_SUCH_METHOD;
    static JSONObject ERROR_SOCKET_IS_BUSY;
    
    static {
        ERROR_NO_SUCH_METHOD = new JSONObject();
        ERROR_NO_SUCH_METHOD.put("error", "No such method");
        
        ERROR_SOCKET_IS_BUSY = new JSONObject();
        ERROR_SOCKET_IS_BUSY.put("error", "Socket is busy");
        
        rpcCalls.put("callAPIFunction", CallAPIFunction.instance);
        rpcCalls.put("getActivity", GetActivity.instance);
        rpcCalls.put("getRecentTransactions", GetRecentTransactions.instance);
        rpcCalls.put("getAccountComments", GetAccountComments.instance);
        rpcCalls.put("getAccounts", GetAccounts.instance);
        rpcCalls.put("getForgingStats", GetForgingStats.instance);
        rpcCalls.put("getActivityStatistics", GetActivityStatistics.instance);
        rpcCalls.put("getAccount", GetAccount.instance);
        rpcCalls.put("getAccountAssets", GetAccountAssets.instance);
        rpcCalls.put("getAccountCurrencies", GetAccountCurrencies.instance);
        
    }
    
    static void socketClosed(MofoWebSocketAdapter socket) {
        synchronized (reverse_listeners) {
            Set<String> topics = reverse_listeners.get(socket);
            if (topics != null) {
                synchronized (listeners) {
                    for (String topic : topics) {
                        Set<MofoWebSocketAdapter> sockets = listeners.get(topic);
                        if (sockets != null) {
                            sockets.remove(socket);
                            if (sockets.isEmpty()) {
                                listeners.remove(topic);
                            }
                        }
                    }
                }                
                reverse_listeners.remove(socket);
            }
        }
    }
    
    /** 
     * Translates a topic like: topic-NXT-ABCD-EFGH-1111
     * Into this: topic-2673535762765625652765
     * 
     * @param topic
     * @return
     */
    static String translateTopic(String topic) {
        String[] parts = topic.split("-", 2);
        if (parts.length > 1) {
            if (parts[1].startsWith("NXT-")) {
                parts[1] = Convert.toUnsignedLong(Crypto.rsDecode(parts[1].substring(4)));
            }
            return parts[0] + '-' + parts[1];
        }
        return topic;
    }
    

    static void subscribe(MofoWebSocketAdapter socket, String topic) {
        topic = translateTopic(topic).toUpperCase();
      
        Logger.logDebugMessage("MofoSocketServer subscribe " + topic);
        
        synchronized (reverse_listeners) {
            synchronized (listeners) {        
                Set<MofoWebSocketAdapter> sockets = listeners.get(topic);
                if (sockets == null) {
                    sockets = new HashSet<MofoWebSocketAdapter>();
                    listeners.put(topic, sockets);
                }
                sockets.add(socket);
                
                Set<String> topics = reverse_listeners.get(socket);
                if (topics == null) {
                    topics = new HashSet<String>();
                    reverse_listeners.put(socket, topics);
                }
                topics.add(topic);
            }
        }
    }
    
    static void unsubscribe(MofoWebSocketAdapter socket, String topic) {
        topic = translateTopic(topic).toUpperCase();
        
        Logger.logDebugMessage("MofoSocketServer unsubscribe " + topic);
      
        synchronized (reverse_listeners) {
            synchronized (listeners) {
                Set<MofoWebSocketAdapter> sockets = listeners.get(topic);
                if (sockets != null) {
                    sockets.remove(socket);
                    if (sockets.isEmpty()) {
                        listeners.remove(topic);
                    }
                }
              
                Set<String> topics = reverse_listeners.get(socket);
                if (topics != null) {
                    topics.remove(topic);
                    if (topics.isEmpty()) {
                        reverse_listeners.remove(socket);
                    }
                }
            }            
        }
    }
    
    /**
     * Available topics..
     * 
     * # blockPushedNew
     * 
     *  Forwards event BLOCK_PUSHED for blocks less than 1 day old
     * 
     * # blockPushed
     * 
     *  Forwards event BLOCK_PUSHED, has limited data available. Response 
     *  consists of height and timestamp only. 
     *
     * # blockPushed-FIM-123-456-789-AAA
     * 
     *  Forwards event BLOCK_PUSHED for blocks generated by the appended account
     * 
     * # blockPoppedNew
     * 
     *  Forwards event BLOCK_POPPED for blocks less than 1 day old
     * 
     * # blockPopped
     * 
     *  Forwards event BLOCK_POPPED, has limited data available. Response 
     *  consists of height and timestamp only.
     *  
     * # blockPopped-FIM-123-456-789-AAA
     * 
     *  Forwards event BLOCK_POPPED for blocks generated by the appended account
     *    
     * # addedUnconfirmedTransactions
     *
     *  Forwards event ADDED_UNCONFIRMED_TRANSACTIONS
     * 
     * # addedUnconfirmedTransactions-FIM-123-456-789-AAA
     * 
     *  Forwards event ADDED_UNCONFIRMED_TRANSACTIONS but only for transactions
     *  that have the added account as recipient or sender.
     *  
     * # removedUnconfirmedTransactions
     * 
     *  Forwards event REMOVED_UNCONFIRMED_TRANSACTIONS
     *  
     * # removedUnconfirmedTransactions-FIM-123-456-789-AAA
     * 
     *  Forwards event REMOVED_UNCONFIRMED_TRANSACTIONS but only for transactions
     *  that have the added account as recipient or sender.
     * 
     * # addedConfirmedTransactions
     * 
     *  Forwards event ADDED_CONFIRMED_TRANSACTIONS but with rate limiter
     * 
     * # addedConfirmedTransactions-FIM-123-456-789-AAA
     * 
     *  Forwards event ADDED_CONFIRMED_TRANSACTIONS but only for transactions
     *  that have the added account as recipient or sender.
     *  
     * # addedTrades
     * 
     *  Collects all trades in between two blocks pushed less than 1 day old
     *  
     * # addedTrades-FIM-123-456-789-AAA
     * 
     *  Same as addedTrades but without a rate limiter and only reporting trades
     *  where the seller or buyer is the appended account.
     *  
     * # addedTrades*125363749459 
     *  
     *  Same as addedTrades-FIM-123-456-789-AAA but instead of limited to buyer
     *  or seller these trades are limited to an asset id.
     *  
     */
    static void notify(final String topic, final Object data) {
        threadPool.submit(new Runnable() {
  
            @Override
            public void run() {
              
                Logger.logDebugMessage("MofoSocketServer notify " + topic);
              
                Set<MofoWebSocketAdapter> sockets = listeners.get(topic);
                if (sockets != null && ! sockets.isEmpty()) {
                    JSONArray array = new JSONArray();
                    array.add("notify");
                    array.add(topic);
                    array.add(data);
                    String response = array.toJSONString();
                    for (MofoWebSocketAdapter socket : sockets) {
                        socket.sendAsync(response);
                    }
                }
            }
        });
    }
    
    static void notifyTransactions(final String topic, final List<? extends Transaction> transactions, final boolean unconfirmed) {
        if ( ! listeners.containsKey(topic)) {
            return;
        }
        MofoSocketServer.notify(topic, JSONData.transactions(transactions, unconfirmed));
    }
    
    static void notifyTrades(final String topic, final List<? extends Trade> trades) {
        if ( ! listeners.containsKey(topic)) {
            return;
        }      
        MofoSocketServer.notify(topic, JSONData.trades(trades));
    }
    
    static void notifyBlock(final String topic, final Block block) {
        if ( ! listeners.containsKey(topic)) {
            return;
        }
        MofoSocketServer.notify(topic, JSONData.minimalBlock(block));
    }
    
    static void notifyBlockMinimal(final String topic, final Block block) {
        if ( ! listeners.containsKey(topic)) {
            return;
        }
        MofoSocketServer.notify(topic, JSONData.minimalBlock(block));
    }    
    
    static void rpcCall(final MofoWebSocketAdapter socket, final String call_id, final String method, final JSONObject arguments) {
        if ( ! rpcCalls.containsKey(method)) {
            Logger.logDebugMessage("Calling non existing RPC method " + method);
            rpcResponse(socket, call_id, ERROR_NO_SUCH_METHOD);
            return;
        }
          
        threadPool.submit(new Runnable() {
          
            @Override
            public void run() {        
        
                JSONStreamAware response = JSON.emptyJSON;
                RPCCall callable = rpcCalls.get(method);                
                try {
                    if (callable == null) {
                        Logger.logDebugMessage("Calling non existing RPC method " + method);
                        response = ERROR_NO_SUCH_METHOD;
                    }
                    else {
                        try {
                            // TODO remove DEBUG statements
                            System.out.println("DOING CALL " + callable);
                            response = callable.call(arguments);
                            System.out.println("RESPONSE " + response);
                        } 
                        catch (ParameterException e) {
                            Logger.logDebugMessage("Invalid parameters", e);
                            response = e.getErrorResponse();
                        } 
                        catch (NxtException | RuntimeException e) {
                            Logger.logDebugMessage("Error processing API request", e);
                            response = ERROR_INCORRECT_REQUEST;
                        } 
                        catch (ExceptionInInitializerError err) {
                            Logger.logErrorMessage("Initialization Error", (Exception) err.getCause());
                            response = ERROR_INCORRECT_REQUEST;
                        }
                        catch (Exception e) {
                            Logger.logDebugMessage("Exception in RPC call", e);
                            response = ERROR_INCORRECT_REQUEST;
                        }
                    }
                }
                finally {
                    // TODO remove DEBUG statements
                    System.out.println("CALL COMPLETE " + callable);
                    rpcResponse(socket, call_id, response);
                }
            }
        });
    }
    
    static void rpcResponse(MofoWebSocketAdapter socket, String call_id, JSONStreamAware response) {
        // TODO remove DEBUG statements
        System.out.println("rpcResponse " + response);
      
        JSONArray array = new JSONArray();
        array.add("response");
        array.add(call_id);
        array.add(response);
        socket.sendAsync(array.toJSONString());
    }
    
    static {
        EventForwarder.init();
    }
}
