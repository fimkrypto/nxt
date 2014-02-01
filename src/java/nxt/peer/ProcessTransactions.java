package nxt.peer;

import nxt.Blockchain;
import nxt.NxtException;
import nxt.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class ProcessTransactions extends HttpJSONRequestHandler {

    static final ProcessTransactions instance = new ProcessTransactions();

    private ProcessTransactions() {}


    @Override
    public JSONStreamAware processJSONRequest(JSONObject request, Peer peer) {

        try {

            Blockchain.processTransactions(request);

        } catch (NxtException.ValidationFailure e) {
            peer.blacklist(e);
        }

        return JSON.emptyJSON;
    }

}