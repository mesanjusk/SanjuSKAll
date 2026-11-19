import {NextRequest} from "next/server";
import {safeMutation} from "@/lib/security";
import {apiError,apiOk} from "@/lib/http";
import {SESSION_COOKIE} from "@/lib/auth";
export async function POST(request:NextRequest){
  if(!safeMutation(request))return apiError("Invalid request origin",403);
  const response=apiOk({message:"Signed out"});
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
