package com.creata.poa.core;
}


public synchronized Transaction addTransaction(String from, String to, BigDecimal amount, long nonce, String memo) {
if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("amount must be >= 0");
// (تحقق nonce/الرصيد البسيط اختياري للتجربة)
Transaction t = new Transaction(from, to, amount, nonce, memo, null);
pending.add(t); return t;
}


public synchronized Block sealPending(String rewardAddress) {
pending.add(new Transaction("__system__", rewardAddress, new BigDecimal("0.01"), 0, "reward", null));
Block b = new Block(chain.size(), chain.get(chain.size()-1).getHash(), new ArrayList<>(pending));
pending.clear();
sealBlock(b); chain.add(b); return b;
}


public synchronized boolean isValid() {
if (chain.isEmpty()) return false;
for (int i=1;i<chain.size();i++){
Block prev = chain.get(i-1), cur = chain.get(i);
if (!Objects.equals(cur.getPreviousHash(), prev.getHash())) return false;
if (!authority.verify(cur)) return false;
List<String> txHashes = new ArrayList<>();
for (Transaction t : cur.getTransactions()) txHashes.add(t.getTxId());
if (!Objects.equals(CryptoUtils.merkleRoot(txHashes), cur.getMerkleRoot())) return false;
}
return true;
}


public synchronized BigDecimal getBalance(String address){
BigDecimal bal=BigDecimal.ZERO;
for (Block b: chain) for (Transaction t: b.getTransactions()){
if (address.equals(t.getFrom())) bal = bal.subtract(t.getAmount());
if (address.equals(t.getTo())) bal = bal.add(t.getAmount());
}
return bal;
}


public synchronized List<Block> getChain(){ return List.copyOf(chain); }
public synchronized List<Transaction> getPending(){ return List.copyOf(pending); }


// ---------- JSON Persistence ----------
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public static class State {
public String authorityName;
public String difficulty = ""; // احتياطي
public List<Block> chain;
public List<Transaction> pending;
}


public synchronized void save(Path path) throws Exception {
ObjectMapper om = new ObjectMapper();
State s = new State();
s.authorityName = authority.name();
s.chain = this.chain;
s.pending = this.pending;
om.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), s);
}


public static Blockchain load(Path path, CentralAuthority authority) throws Exception {
ObjectMapper om = new ObjectMapper();
State s = om.readValue(path.toFile(), State.class);
Blockchain bc = new Blockchain(authority);
synchronized (bc) {
bc.chain.clear(); bc.pending.clear();
bc.chain.addAll(s.chain);
bc.pending.addAll(s.pending);
if (!bc.isValid()) throw new IllegalStateException("Invalid chain or wrong HMAC key");
}
return bc;
}
}
