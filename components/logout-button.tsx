"use client";
import {useRouter} from "next/navigation";
import {LogOut} from "lucide-react";
export function LogoutButton(){const router=useRouter();const logOut=async()=>{await fetch("/api/auth/logout",{method:"POST"});router.replace("/");router.refresh();};return <button onClick={logOut} type="button" className="sidebar-bottom logout-button"><LogOut size={19}/> Sign out</button>}
