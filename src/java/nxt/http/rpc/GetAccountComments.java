package nxt.http.rpc;

import nxt.Account;
import nxt.MofoQueries;
import nxt.Transaction;
import nxt.db.DbIterator;
import nxt.http.ParameterException;
import nxt.http.websocket.JSONData;
import nxt.http.websocket.RPCCall;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

public class GetAccountComments extends RPCCall {
    
    public static RPCCall instance = new GetAccountComments("getAccountComments");
    static int COUNT = 10;

    public GetAccountComments(String identifier) {
        super(identifier);
    }
  
    @SuppressWarnings("unchecked")
    @Override
    public JSONStreamAware call(JSONObject arguments) throws ParameterException {
        Account account = ParameterParser.getAccount(arguments);
        int timestamp = ParameterParser.getTimestamp(arguments);
        
        JSONArray transactions = new JSONArray();
        try (
            DbIterator<? extends Transaction> iterator = MofoQueries.getAccountComments(account.getId(), timestamp, COUNT);
        ) {          
            while (iterator.hasNext()) {
                transactions.add(JSONData.transaction(iterator.next(), false));
            }
        }        
        
        JSONObject response = new JSONObject();
        response.put("comments", transactions);
        return response;
    }  

}
