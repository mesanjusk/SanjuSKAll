import { MongoClient, type Db } from "mongodb";

const globalCache = globalThis as unknown as { skShipMongo?: Promise<MongoClient> };

export function isDatabaseConfigured() {
  return Boolean(process.env.MONGODB_URI?.trim());
}
export async function getDb(): Promise<Db> {
  const uri = process.env.MONGODB_URI?.trim();
  if (!uri) throw new Error("MONGODB_NOT_CONFIGURED");
  if (!globalCache.skShipMongo) {
    const client = new MongoClient(uri, {maxPoolSize: 10, serverSelectionTimeoutMS: 6500});
    globalCache.skShipMongo = client.connect().catch(error => {
      globalCache.skShipMongo = undefined;
      throw error;
    });
  }
  const client = await globalCache.skShipMongo;
  return client.db(process.env.MONGODB_DB || "sk_ship");
}
export async function ensureIndexes() {
  const db = await getDb();
  await Promise.all([
    db.collection("users").createIndex({email: 1}, {unique: true}),
    db.collection("shipments").createIndex({merchantId: 1, createdAt: -1}),
    db.collection("shipments").createIndex({trackingNumber: 1}, {unique: true, partialFilterExpression: {trackingNumber: {$type: "string"}}}),
    db.collection("audit_events").createIndex({createdAt: -1}),
    db.collection("rate_limits").createIndex({expiresAt:1},{expireAfterSeconds:0})
  ]);
}
