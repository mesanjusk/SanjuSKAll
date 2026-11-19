import { NextRequest,NextResponse } from "next/server";
import { hash } from "bcryptjs";
import {getDb,isDatabaseConfigured,ensureIndexes} from "@/lib/mongo";
import {registrationSchema} from "@/lib/validation";
import {databaseUnavailable,apiError,apiOk} from "@/lib/http";
import {safeMutation,rateLimited} from "@/lib/security";
import {issueSession,SESSION_COOKIE} from "@/lib/auth";
export async function POST(request:NextRequest){
  if(!safeMutation(request))return apiError("Invalid request origin",403);
  if(!isDatabaseConfigured())return databaseUnavailable();
  if(!process.env.SESSION_SECRET || process.env.SESSION_SECRET.length<32)return apiError("Session service is not configured",503);
  try{
    if(await rateLimited(request,"register",8))return apiError("Too many attempts. Try again later.",429);
    const value=registrationSchema.safeParse(await request.json());
    if(!value.success)return apiError(value.error.issues[0]?.message||"Invalid registration details",422);
    await ensureIndexes();
    const db=await getDb(); const {name,company,email,mobile,password}=value.data;
    const existing=await db.collection("users").findOne({email});
    if(existing)return apiError("Email is already registered",409);
    const created=await db.collection("users").insertOne({name,company,email,mobile,passwordHash:await hash(password,12),role:"merchant",active:true,createdAt:new Date()});
    const token=await issueSession({sub:created.insertedId.toHexString(),role:"merchant",email});
    const response:NextResponse=apiOk({user:{name,company,email,role:"merchant"}},201);
    response.cookies.set(SESSION_COOKIE,token,{httpOnly:true,sameSite:"lax",secure:process.env.NODE_ENV==="production",path:"/",maxAge:60*60*24*7});
    return response;
  }catch(e){console.error("Registration failed",e instanceof Error?e.message:"error");return apiError("Registration is temporarily unavailable",503);}
}
