import { cookies } from "next/headers";
import { SignJWT, jwtVerify } from "jose";
import { ObjectId } from "mongodb";
import { getDb } from "@/lib/mongo";

export const SESSION_COOKIE = "skship_session";
export type Session = {sub: string; role: "merchant" | "admin"; email: string};
const secret = () => {
  const value = process.env.SESSION_SECRET;
  if (!value || value.length < 32) throw new Error("SESSION_SECRET_NOT_CONFIGURED");
  return new TextEncoder().encode(value);
};
export async function issueSession(user: Session) {
  return new SignJWT({role: user.role, email: user.email})
    .setProtectedHeader({alg:"HS256"}).setSubject(user.sub)
    .setIssuedAt().setExpirationTime("7d").sign(secret());
}
export async function currentSession(): Promise<Session | null> {
  const token = (await cookies()).get(SESSION_COOKIE)?.value;
  if (!token) return null;
  try {
    const {payload} = await jwtVerify(token, secret());
    if (!payload.sub || (payload.role !== "merchant" && payload.role !== "admin") || typeof payload.email !== "string") return null;
    // Recheck role in the database, so deactivation/role changes take effect.
    if (!ObjectId.isValid(payload.sub)) return null;
    const db = await getDb();
    const user = await db.collection("users").findOne({_id:new ObjectId(payload.sub)}, {projection: {email:1,role:1,active:1}});
    if (!user || user.active === false || user.email !== payload.email || user.role !== payload.role) return null;
    return {sub:payload.sub,role:payload.role,email:payload.email};
  } catch {return null;}
}
export async function audit(actorId:string, action:string, details:Record<string, unknown>={}) {
  const db = await getDb();
  await db.collection("audit_events").insertOne({actorId,action,details,createdAt:new Date()});
}
