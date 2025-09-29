package com.creata.poa.api;
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
