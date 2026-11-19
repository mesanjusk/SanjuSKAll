import { NextRequest } from "next/server";
import { getDb } from "@/lib/mongo";
export function safeMutation(request: NextRequest): boolean {
  const origin = request.headers.get("origin");
  if (!origin) return false; // Browser forms must include Origin; deny cross-site and unknown clients.
  try {
    const requestHost = request.headers.get("x-forwarded-host") || request.headers.get("host");
    const originHost = new URL(origin).host;
    return Boolean(requestHost && originHost === requestHost);
  } catch {return false;}
}
export async function rateLimited(request:NextRequest, action:string, max=12): Promise<boolean> {
  // Conservative rate limit for auth endpoints. One document per action/IP/window; automatically expires.
  const ip=(request.headers.get("x-forwarded-for")||"unknown").split(",")[0].trim().slice(0,80);
  const db=await getDb();
  const windowStart=Math.floor(Date.now()/600000)*600000;
  const key=`${action}:${ip}:${windowStart}`;
  const result=await db.collection<{_id:string;count:number;expiresAt:Date}>("rate_limits").findOneAndUpdate(
    {_id:key},{$inc:{count:1},$setOnInsert:{expiresAt:new Date(windowStart+1200000)}},
    {upsert:true,returnDocument:"after"});
  return Number(result?.count||0)>max;
}
