package com.creata.poa.core;
}


// منشئ للتحميل من JSON
@JsonCreator
public Block(
@JsonProperty("index") int index,
@JsonProperty("timestamp") long timestamp,
@JsonProperty("previousHash") String previousHash,
@JsonProperty("transactions") List<Transaction> transactions,
@JsonProperty("merkleRoot") String merkleRoot,
@JsonProperty("authorizer") String authorizer,
@JsonProperty("authoritySignature") String authoritySignature,
@JsonProperty("hash") String hash,
@JsonProperty("_loaded") Boolean _loaded // حيلة لمنع تضارب مع المنشئ الآخر
) {
this.index = index;
this.timestamp = timestamp;
this.previousHash = previousHash;
this.transactions = Collections.unmodifiableList(new ArrayList<>(transactions));
this.merkleRoot = merkleRoot;
this.authorizer = authorizer;
this.authoritySignature = authoritySignature;
this.hash = hash;
}


public String headerWithoutSignature() {
StringBuilder txs = new StringBuilder();
for (int i=0;i<transactions.size();i++) {
if (i>0) txs.append(",");
txs.append(transactions.get(i).canonical());
}
return "{"+
"\"index\":"+index+","+
"\"timestamp\":"+timestamp+","+
"\"previousHash\":\""+previousHash+"\","+
"\"transactions\":["+txs+"],"+
"\"merkleRoot\":\""+merkleRoot+"\""+
"}";
}


public String headerWithSignature() {
return "{"+
"\"index\":"+index+","+
"\"timestamp\":"+timestamp+","+
"\"previousHash\":\""+previousHash+"\","+
"\"transactions\":[...],"+
"\"merkleRoot\":\""+merkleRoot+"\","+
"\"authorizer\":\""+authorizer+"\","+
"\"authoritySignature\":\""+authoritySignature+"\""+
"}";
}


public void setAuthorizer(String a){ this.authorizer=a; }
public void setAuthoritySignature(String s){ this.authoritySignature=s; }
public void setHash(String h){ this.hash=h; }


public int getIndex(){ return index; }
public long getTimestamp(){ return timestamp; }
public String getPreviousHash(){ return previousHash; }
public List<Transaction> getTransactions(){ return transactions; }
public String getMerkleRoot(){ return merkleRoot; }
public String getAuthorizer(){ return authorizer; }
public String getAuthoritySignature(){ return authoritySignature; }
public String getHash(){ return hash; }
}
