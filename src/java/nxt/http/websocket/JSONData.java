package nxt.http.websocket;

import java.util.List;
import java.util.Map;

import nxt.Account;
import nxt.Appendix;
import nxt.Asset;
import nxt.Attachment.MonetarySystemAttachment;
import nxt.Block;
import nxt.Currency;
import nxt.Nxt;
import nxt.Trade;
import nxt.Transaction;
import nxt.util.Convert;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class JSONData {

    @SuppressWarnings("unchecked")
    public static JSONObject accountBalance(Account account) {
        JSONObject json = new JSONObject();
        if (account == null) {
            json.put("balanceNQT", "0");
            json.put("unconfirmedBalanceNQT", "0");
            json.put("effectiveBalanceNXT", "0");
            json.put("forgedBalanceNQT", "0");
            json.put("guaranteedBalanceNQT", "0");
        } else {
            json.put("balanceNQT", String.valueOf(account.getBalanceNQT()));
            json.put("unconfirmedBalanceNQT", String.valueOf(account.getUnconfirmedBalanceNQT()));
            json.put("effectiveBalanceNXT", account.getEffectiveBalanceNXT());
            json.put("forgedBalanceNQT", String.valueOf(account.getForgedBalanceNQT()));
            json.put("guaranteedBalanceNQT", String.valueOf(account.getGuaranteedBalanceNQT(1440)));
        }
        return json;
    }
  
    @SuppressWarnings("unchecked")
    public static void putAccount(JSONObject json, String name, long accountId) {
        Account account = Account.getAccount(accountId);
        if (account != null) {
            json.put(name + "Name", account.getName());
        }    
        json.put(name, Convert.toUnsignedLong(accountId));
        json.put(name + "RS", Convert.rsAccount(accountId));      
    }
  
    public static void putAsset(JSONObject response, String string, long id) {
      // TODO Auto-generated method stub
      
    }
    
    @SuppressWarnings("unchecked")
    public static JSONArray transactions(List<? extends Transaction> transactions, boolean unconfirmed) {
        JSONArray result = new JSONArray();
        for (Transaction t : transactions) {
            result.add(transaction(t, unconfirmed));
        }
        return result;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static JSONObject transaction(Transaction transaction, boolean unconfirmed) {
        JSONObject json = new JSONObject();
        json.put("transaction", transaction.getStringId());
        json.put("type", transaction.getType().getType());
        json.put("subtype", transaction.getType().getSubtype());
        json.put("timestamp", transaction.getTimestamp());
        json.put("amountNQT", transaction.getAmountNQT());
        json.put("feeNQT", transaction.getFeeNQT());
        json.put("deadline", transaction.getDeadline());
        
        if (unconfirmed) {
          json.put("confirmations", -1);
          json.put("height", Nxt.getBlockchain().getHeight() + 1);
        }
        else {
          json.put("confirmations", Nxt.getBlockchain().getHeight() - transaction.getHeight());
          json.put("height", transaction.getHeight());
        }        
        JSONData.putAccount(json, "sender", transaction.getSenderId());
        if (transaction.getRecipientId() != 0) {
            JSONData.putAccount(json, "recipient", transaction.getRecipientId());
        }
        JSONObject attachmentJSON = new JSONObject();
        for (Appendix appendage : transaction.getAppendages()) {
            attachmentJSON.putAll(appendage.getJSONObject());
            if (transaction.getType().getType() == 5 && appendage instanceof MonetarySystemAttachment) {
                final long currencyId = ((MonetarySystemAttachment) appendage).getCurrencyId(); 
                JSONData.putCurrencyInfo(attachmentJSON, currencyId);
            }
        }
        if (! attachmentJSON.isEmpty()) {
            for (Map.Entry entry : (Iterable<Map.Entry>) attachmentJSON.entrySet()) {
                if (entry.getValue() instanceof Long) {
                    entry.setValue(String.valueOf(entry.getValue()));
                }
            }
            json.put("attachment", attachmentJSON);
        }
        return json;
    }
  
    @SuppressWarnings("unchecked")
    private static void putCurrencyInfo(JSONObject json, long currencyId) {
        Currency currency = Currency.getCurrency(currencyId);
        if (currency == null) {
            return;
        }
        json.put("name", currency.getName());
        json.put("currentSupply", currency.getCurrentSupply());
        json.put("code", currency.getCode());
        json.put("type", currency.getType());
        json.put("decimals", currency.getDecimals());
        json.put("issuanceHeight", currency.getIssuanceHeight());
        putAccount(json, "issuerAccount", currency.getAccountId());     
    }

    @SuppressWarnings("unchecked")
    public static void putAssetInfo(JSONObject json, long assetId) {
        Asset asset = Asset.getAsset(assetId);
        json.put("name", asset.getName());
        json.put("decimals", asset.getDecimals());
    }    
    
    @SuppressWarnings("unchecked")
    public static JSONObject trade(Trade trade, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        json.put("timestamp", trade.getTimestamp());
        json.put("quantityQNT", String.valueOf(trade.getQuantityQNT()));
        json.put("priceNQT", String.valueOf(trade.getPriceNQT()));
        json.put("asset", Convert.toUnsignedLong(trade.getAssetId()));
        json.put("askOrder", Convert.toUnsignedLong(trade.getAskOrderId()));
        json.put("bidOrder", Convert.toUnsignedLong(trade.getBidOrderId()));
        json.put("askOrderHeight", trade.getAskOrderHeight());
        json.put("bidOrderHeight", trade.getBidOrderHeight());
        putAccount(json, "seller", trade.getSellerId());
        putAccount(json, "buyer", trade.getBuyerId());
        json.put("block", Convert.toUnsignedLong(trade.getBlockId()));
        json.put("height", trade.getHeight());
        json.put("tradeType", trade.isBuy() ? "buy" : "sell");
        json.put("confirmations", Nxt.getBlockchain().getHeight() - trade.getHeight());
        if (includeAssetInfo) {
            putAssetInfo(json, trade.getAssetId());
        }
        return json;
    }
  
    @SuppressWarnings("unchecked")
    public static JSONArray trades(List<? extends Trade> trades) {
        JSONArray result = new JSONArray();
        for (Trade t : trades) {
            result.add(trade(t, true));
        }
        return result;
    }
    
    @SuppressWarnings("unchecked")
    public static JSONObject minimalBlock(Block block) {
        JSONObject json = new JSONObject();
        json.put("block", block.getStringId());
        json.put("height", block.getHeight());
        json.put("generator", Convert.rsAccount(block.getGeneratorId())); 
        json.put("timestamp", block.getTimestamp());
        json.put("numberOfTransactions", block.getTransactions().size());
        json.put("totalAmountNQT", String.valueOf(block.getTotalAmountNQT()));
        json.put("totalFeeNQT", String.valueOf(block.getTotalFeeNQT()));
        return json;
    }

    @SuppressWarnings("unchecked")
    public static JSONObject accountAsset(Account.AccountAsset accountAsset, boolean includeAccount, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        if (includeAccount) {
            putAccount(json, "account", accountAsset.getAccountId());
        }
        json.put("asset", Convert.toUnsignedLong(accountAsset.getAssetId()));
        json.put("quantityQNT", String.valueOf(accountAsset.getQuantityQNT()));
        json.put("unconfirmedQuantityQNT", String.valueOf(accountAsset.getUnconfirmedQuantityQNT()));
        if (includeAssetInfo) {
            Asset asset = Asset.getAsset(accountAsset.getAssetId());
            json.put("name", asset.getName());
            json.put("decimals", asset.getDecimals());          
            json.put("totalQuantityQNT", asset.getQuantityQNT());
            json.put("issuer", Convert.rsAccount(asset.getAccountId()));
        }
        return json;
    }
    
    @SuppressWarnings("unchecked")
    public static JSONObject accountCurrency(Account.AccountCurrency accountCurrency, boolean includeCurrencyInfo) {
        JSONObject json = new JSONObject();
        json.put("currency", Convert.toUnsignedLong(accountCurrency.getCurrencyId()));
        json.put("units", String.valueOf(accountCurrency.getUnits()));
        json.put("unconfirmedUnits", String.valueOf(accountCurrency.getUnconfirmedUnits()));
        if (includeCurrencyInfo) {
            putCurrencyInfo(json, accountCurrency.getCurrencyId());
        }
        return json;
    }
}
