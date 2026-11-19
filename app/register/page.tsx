import {Nav} from "@/components/nav";
import {AuthForm} from "@/components/auth-form";
import {Info,PackageCheck} from "lucide-react";
export const metadata={title:"Create Merchant Account"};
export default function Register(){return <><Nav/><main className="auth-page"><div className="auth-layout"><div className="auth-card"><span className="kicker">MERCHANT ONBOARDING</span><h1>Start shipping smarter.</h1><p>Set up your business account to manage shipments when SK Ship launches.</p><div className="inline-warning"><Info size={18}/> Sign-ups are paused until the MongoDB database and session secret are configured.</div><AuthForm mode="register"/></div><aside className="auth-aside"><div className="aside-art"><div><PackageCheck size={42}/></div><h2>Built for your<br/>business goals.</h2><p>From your first order to repeat shipments, keep everything in one place.</p><span>Dedicated launch preview · No live charges</span></div></aside></div></main></>}
