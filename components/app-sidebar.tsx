import Link from "next/link";
import {Box,LayoutDashboard,Settings,ShieldCheck,Truck,Wallet,ChartNoAxesColumn,LifeBuoy} from "lucide-react";
import {Brand} from "@/components/brand";
import {LogoutButton} from "@/components/logout-button";
export function AppSidebar({admin=false}:{admin?:boolean}){
return <aside className="app-sidebar"><div className="sidebar-brand"><Brand small/></div><div className="sidebar-label">WORKSPACE</div><nav className="sidebar-links"><Link className="active" href={admin?"/admin":"/dashboard"}><LayoutDashboard size={19}/> Overview</Link><Link href="/dashboard#shipments"><Box size={19}/> Shipments</Link><Link href="/dashboard#shipping"><Truck size={19}/> New shipment</Link><div className="sidebar-muted"><Wallet size={19}/> Wallet <span>Soon</span></div><div className="sidebar-muted"><ChartNoAxesColumn size={19}/> Reports <span>Soon</span></div></nav><div className="sidebar-label sidebar-second">MANAGEMENT</div><nav className="sidebar-links">{admin&&<Link href="/admin"><ShieldCheck size={19}/> Admin centre</Link>}<Link href="/setup"><Settings size={19}/> Integration status</Link><Link href="/tracking"><LifeBuoy size={19}/> Track package</Link></nav><div className="sidebar-spacer"/><LogoutButton/></aside>
}
