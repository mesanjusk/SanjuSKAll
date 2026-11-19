import { NextRequest, NextResponse } from "next/server";
import {compare} from "bcryptjs";
import {getDb,isDatabaseConfigured} from "@/lib/mongo";
import {loginSchema} from "@/lib/validation";
import {safeMutation,rateLimited} from "@/lib/security";
import {SESSION_COOKIE,issueSession} from "@/lib/auth";
import {databaseUnavailable,apiError,apiOk} from "@/lib/http";
export async function POST(request:NextRequest){
  if(!safeMutation(request))return apiError("Invalid request origin",403);
  if(!isDatabaseConfigured())return databaseUnavailable();
  if(!process.env.SESSION_SECRET || process.env.SESSION_SECRET.length<32)return apiError("Session service is not configured",503);
  try{
    if(await rateLimited(request,"login",15))return apiError("Too many attempts. Try again later.",429);
    const value=loginSchema.safeParse(await request.json());
    if(!value.success)return apiError("Invalid email or password",401);
    const db=await getDb();
    const user=await db.collection("users").findOne({email:value.data.email,active:{$ne:false}});
    if(!user || !(await compare(value.data.password,user.passwordHash)))return apiError("Invalid email or password",401);
    if(user.role!=="merchant" && user.role!=="admin")return apiError("Invalid account role",403);
    const token=await issueSession({sub:user._id.toHexString(),email:user.email,role:user.role});
    const response:NextResponse=apiOk({user:{name:user.name,company:user.company,email:user.email,role:user.role}});
    response.cookies.set(SESSION_COOKIE,token,{httpOnly:true,sameSite:"lax",secure:process.env.NODE_ENV==="production",path:"/",maxAge:60*60*24*7});
    return response;
  }catch(e){console.error("Login error",e instanceof Error?e.message:"error");return apiError("Login temporarily unavailable",503);}
}
