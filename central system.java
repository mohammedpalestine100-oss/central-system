package com.creata.poa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.Base64;

@SpringBootApplication
public class CreataApp {

  public static void main(String[] args) {
    SpringApplication.run(CreataApp.class, args);
  }

  // ===================== Utils =====================
  static class CryptoUtils {
    static String sha256(byte[] data) {
      try {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        return bytesToHex(d.digest(data));
      } catch (Exception e) { throw new RuntimeException(e); }
    }
    static String bytesToHex(byte[] b) {
      StringBuilder sb = new StringBuilder(b.length * 2);
      for (byte x : b) sb.append(String.format("%02x", x));
      return sb.toString();
    }
    static byte[] hexToBytes(String hex) {
      int n = hex.length(); byte[] out = new byte[n/2];
      for (int i=0;i<n;i+=2) out[i/2]=(byte)Integer.parseInt(hex.substring(i,i+2),16);
      return out;
    }
    static String merkleRoot(List<String> leavesHex) {
      if (leavesHex == null || leavesHex.isEmpty()) return sha256(new byte[0]);
      List<byte[]> layer = new ArrayList<>();
      for (String h : leavesHex) layer.add(hexToBytes(h));
      while (layer.size() > 1) {
        List<byte[]> next = new ArrayList<>();
        for (int i=0;i<layer.size();i+=2) {
          byte[] L = layer.get(i);
          byte[] R = (i+1<layer.size()) ? layer.get(i+1) : L;
          byte[] both = new byte[L.length+R.length];
          System.arraycopy(L,0,both,0,L.length);
          System.arraycopy(R,0,both,L.length,R.length);
          next.add(hexToBytes(sha256(both)));
        }
        layer = next;
      }
      return bytesToHex(layer.get(0));
    }
  }

  // ===================== Model: Transaction =====================
  static class Transaction {
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
      this.txId = (txId != null) ? txId
          : CryptoUtils.sha256(canonical.getBytes(StandardCharsets.UTF_8));
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

  // ===================== Model: Block =====================
  static class Block {
    private final int index;
    private final long timestamp;
    private final String previousHash;
    private final List<Transaction> transactions;
    private final String merkleRoot;

    private String authorizer;          // "creata"
    private String authoritySignature;  // HMAC على headerWithoutSignature
    private String hash;                // SHA256(headerWithSignature)

    // منشئ للاستخدام عند بناء بلوك جديد
    public Block(int index, String previousHash, List<Transaction> txs) {
      this.index = index;
      this.timestamp = Instant.now().getEpochSecond();
      this.previousHash = previousHash;
      this.transactions = Collections.unmodifiableList(new ArrayList<>(txs));
      List<String> txHashes = new ArrayList<>();
      for (Transaction t : txs) txHashes.add(t.getTxId());
      this.merkleRoot = CryptoUtils.merkleRoot(txHashes);
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
        @JsonProperty("_loaded") Boolean _loaded
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
      return "{"
        + "\"index\":"+index+","
        + "\"timestamp\":"+timestamp+","
        + "\"previousHash\":\""+previousHash+"\","
        + "\"transactions\":["+txs+"],"
        + "\"merkleRoot\":\""+merkleRoot+"\""
        + "}";
    }
    public String headerWithSignature() {
      return "{"
        + "\"index\":"+index+","
        + "\"timestamp\":"+timestamp+","
        + "\"previousHash\":\""+previousHash+"\","
        + "\"transactions\":[...],"
        + "\"merkleRoot\":\""+merkleRoot+"\","
        + "\"authorizer\":\""+authorizer+"\","
        + "\"authoritySignature\":\""+authoritySignature+"\""
        + "}";
    }

    // setters
    public void setAuthorizer(String a){ this.authorizer=a; }
    public void setAuthoritySignature(String s){ this.authoritySignature=s; }
    public void setHash(String h){ this.hash=h; }

    // getters
    public int getIndex(){ return index; }
    public long getTimestamp(){ return timestamp; }
    public String getPreviousHash(){ return previousHash; }
    public List<Transaction> getTransactions(){ return transactions; }
    public String getMerkleRoot(){ return merkleRoot; }
    public String getAuthorizer(){ return authorizer; }
    public String getAuthoritySignature(){ return authoritySignature; }
    public String getHash(){ return hash; }
  }

  // ===================== Core: CentralAuthority =====================
  static class CentralAuthority {
    private final String name;      // "creata"
    private byte[] secretKey;       // HMAC key

    public CentralAuthority(String name, byte[] secretKey) {
      this.name = name; this.secretKey = secretKey.clone();
    }
    public String name(){ return name; }
    public byte[] key(){ return secretKey; }
    public void importKey(byte[] k){ this.secretKey = k.clone(); }

    public String sign(String headerWithoutSig) {
      try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
        return CryptoUtils.bytesToHex(mac.doFinal(headerWithoutSig.getBytes(StandardCharsets.UTF_8)));
      } catch (Exception e){ throw new RuntimeException("HMAC failure", e); }
    }
    public boolean verify(Block b) {
      String sig = sign(b.headerWithoutSignature());
      if (!Objects.equals(sig, b.getAuthoritySignature())) return false;
      String recomputedHash = CryptoUtils.sha256(b.headerWithSignature().getBytes(StandardCharsets.UTF_8));
      return Objects.equals(recomputedHash, b.getHash());
    }
  }

  // ===================== Core: Blockchain =====================
  static class Blockchain {
    private final CentralAuthority authority;
    private final List<Block> chain = new ArrayList<>();
    private final List<Transaction> pending = new ArrayList<>();

    public Blockchain(CentralAuthority authority) {
      this.authority = authority;
      createGenesis();
    }

    private synchronized void createGenesis() {
      if (!chain.isEmpty()) return;
      Transaction gtx = new Transaction("__genesis__", authority.name(), BigDecimal.ZERO, 0, "genesis", null);
      Block g = new Block(0, "0".repeat(64), List.of(gtx));
      sealBlock(g);
      chain.add(g);
    }

    private void sealBlock(Block b) {
      b.setAuthorizer(authority.name());
      b.setAuthoritySignature(authority.sign(b.headerWithoutSignature()));
      b.setHash(CryptoUtils.sha256(b.headerWithSignature().getBytes(StandardCharsets.UTF_8)));
    }

    public synchronized Transaction addTransaction(String from, String to, BigDecimal amount, long nonce, String memo) {
      if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("amount must be >= 0");
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
        if (address.equals(t.getTo()))   bal = bal.add(t.getAmount());
      }
      return bal;
    }

    public synchronized List<Block> getChain(){ return List.copyOf(chain); }
    public synchronized List<Transaction> getPending(){ return List.copyOf(pending); }

    // ---------- JSON Persistence ----------
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class State {
      public String authorityName;
      public String difficulty = "";
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

  // ===================== API: Controller =====================
  @RestController
  @RequestMapping("/api/v1/")
  static class BlockchainController {

    private CentralAuthority authority;
    private Blockchain bc;

    public BlockchainController() {
      byte[] key = resolveKeyFromEnv();
      this.authority = new CentralAuthority("creata", key);
      this.bc = new Blockchain(authority);
    }

    private static byte[] resolveKeyFromEnv() {
      String base64 = System.getenv("CREATA_HMAC_KEY_BASE64");
      if (base64 != null && !base64.isBlank()) {
        return Base64.getDecoder().decode(base64);
      }
      byte[] k = new byte[32]; new SecureRandom().nextBytes(k); return k;
    }

    // ------ DTOs ------
    public record TxReq(@NotBlank String from, @NotBlank String to, @Min(0) BigDecimal amount, long nonce, String memo) {}
    public record SealReq(String rewardAddress) {}
    public record SaveReq(String path) {}
    public record LoadReq(String path) {}
    public record ImportKeyReq(String hmacKeyBase64) {}

    // ------ Chain & Balances ------
    @GetMapping("chain")
    public Map<String,Object> chain(){
      return Map.of(
        "authority", authority.name(),
        "valid", bc.isValid(),
        "chain", bc.getChain(),
        "pending", bc.getPending()
      );
    }

    @GetMapping("balance/{addr}")
    public Map<String,Object> balance(@PathVariable String addr){
      return Map.of("address", addr, "balance", bc.getBalance(addr));
    }

    // ------ Transactions ------
    @PostMapping("tx")
    public ResponseEntity<?> addTx(@RequestBody TxReq body){
      try {
        Transaction t = bc.addTransaction(body.from(), body.to(), body.amount(), body.nonce(), body.memo());
        return ResponseEntity.ok(Map.of("txId", t.getTxId()));
      } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      }
    }

    @PostMapping("seal")
    public ResponseEntity<?> seal(@RequestBody(required=false) SealReq body){
      String reward = (body!=null && body.rewardAddress()!=null && !body.rewardAddress().isBlank())
          ? body.rewardAddress() : "miner";
      Block b = bc.sealPending(reward);
      return ResponseEntity.ok(Map.of("index", b.getIndex(), "hash", b.getHash()));
    }

    // ------ Persistence ------
    @PostMapping("save")
    public ResponseEntity<?> save(@RequestBody(required=false) SaveReq body) {
      try {
        String path = (body!=null && body.path()!=null && !body.path().isBlank()) ? body.path() : "chain_state.json";
        bc.save(Path.of(path));
        return ResponseEntity.ok(Map.of("saved", true, "path", path));
      } catch (Exception e){
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      }
    }

    @PostMapping("load")
    public ResponseEntity<?> load(@RequestBody(required=false) LoadReq body) {
      try {
        String path = (body!=null && body.path()!=null && !body.path().isBlank()) ? body.path() : "chain_state.json";
        this.bc = Blockchain.load(Path.of(path), this.authority);
        return ResponseEntity.ok(Map.of("loaded", true, "path", path, "valid", bc.isValid()));
      } catch (Exception e){
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      }
    }

    // ------ HMAC Key export/import ------
    @GetMapping("key/export")
    public Map<String,Object> exportKey(){
      return Map.of("authority", authority.name(),
                    "hmacKeyBase64", Base64.getEncoder().encodeToString(authority.key()));
    }

    @PostMapping("key/import")
    public ResponseEntity<?> importKey(@RequestBody ImportKeyReq body){
      try {
        byte[] k = Base64.getDecoder().decode(body.hmacKeyBase64());
        authority.importKey(k);
        boolean ok = bc.isValid();
        return ResponseEntity.ok(Map.of("imported", true, "validWithNewKey", ok));
      } catch (Exception e){
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      }
    }
  }
}
