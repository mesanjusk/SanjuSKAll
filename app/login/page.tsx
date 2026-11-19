import {Nav} from "@/components/nav";
import {AuthForm} from "@/components/auth-form";
import {ShieldCheck,PackageCheck} from "lucide-react";
export const metadata={title:"Merchant Login"};
export default function Login(){return <><Nav/><main className="auth-page"><div className="auth-layout"><div className="auth-card"><span className="kicker">WELCOME BACK</span><h1>Sign in to SK Ship.</h1><p>Access your shipping workspace and manage your business shipments.</p><AuthForm mode="login"/></div><aside className="auth-aside"><div className="aside-art"><div><PackageCheck size={42}/></div><h2>Every shipment,<br/>one dashboard.</h2><p>Stay organized at every stage of the shipping journey.</p><span><ShieldCheck size={17}/> Protected merchant access</span></div></aside></div></main></>}
