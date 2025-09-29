package com.creata.poa.core;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;


public class Transaction {
private final String txId;
private final String from;
private final String to;
private final BigDecimal amount;
private final long nonce;
private final String memo;


@JsonCreator
public Transaction(
@JsonProperty("from") String from,
@JsonProperty("to") String to,
@JsonProperty("amount") BigDecimal amount,
@JsonProperty("nonce") long nonce,
@JsonProperty("memo") String memo,
@JsonProperty("txId") String txId
) {
this.from = from; this.to = to; this.amount = amount; this.nonce = nonce; this.memo = memo;
String canonical = canonical();
this.txId = (txId != null) ? txId : CryptoUtils.sha256(canonical.getBytes(StandardCharsets.UTF_8));
}


public String canonical() {
String m = (memo==null?"null":"\""+memo+"\"");
return "{\"from\":\""+from+"\",\"to\":\""+to+"\",\"amount\":"+amount+","+
"\"nonce\":"+nonce+",\"memo\":"+m+"}";
}


public String getTxId(){ return txId; }
public String getFrom(){ return from; }
public String getTo(){ return to; }
public BigDecimal getAmount(){ return amount; }
public long getNonce(){ return nonce; }
public String getMemo(){ return memo; }
}
