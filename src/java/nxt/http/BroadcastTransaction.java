package nxt.http;

import nxt.Blockchain;
import nxt.Transaction;
import nxt.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static nxt.http.JSONResponses.INCORRECT_TRANSACTION_BYTES;
import static nxt.http.JSONResponses.MISSING_TRANSACTION_BYTES;

final class BroadcastTransaction extends HttpRequestHandler {

    static final BroadcastTransaction instance = new BroadcastTransaction();

    private BroadcastTransaction() {}

    @Override
    public JSONStreamAware processRequest(HttpServletRequest req) {

        String transactionBytes = req.getParameter("transactionBytes");
        if (transactionBytes == null) {
            return MISSING_TRANSACTION_BYTES;
        }

        try {

            ByteBuffer buffer = ByteBuffer.wrap(Convert.convert(transactionBytes));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            Transaction transaction = Transaction.getTransaction(buffer);

            Blockchain.broadcast(transaction);

            JSONObject response = new JSONObject();
            response.put("transaction", transaction.getStringId());
            return response;

        } catch (RuntimeException e) {
            return INCORRECT_TRANSACTION_BYTES;
        }
    }

}
