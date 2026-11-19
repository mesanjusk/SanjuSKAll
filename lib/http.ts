import { NextResponse } from "next/server";
export function apiError(message:string,status=400){return NextResponse.json({ok:false,error:message},{status,headers:{"Cache-Control":"no-store"}});}
export function apiOk(data:Record<string,unknown>,status=200){return NextResponse.json({ok:true,...data},{status,headers:{"Cache-Control":"no-store"}});}
export function databaseUnavailable(){return apiError("Database connection is not configured yet. The platform is currently in preview mode.",503);}
