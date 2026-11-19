// Run locally or from an authenticated maintenance environment once MongoDB is configured.
// Usage: ADMIN_EMAIL=... ADMIN_PASSWORD=... MONGODB_URI=... npm run seed:admin
import {MongoClient} from "mongodb";
import {hash} from "bcryptjs";
const {ADMIN_EMAIL,ADMIN_PASSWORD,MONGODB_URI,MONGODB_DB="sk_ship"}=process.env;
if(!ADMIN_EMAIL||!/^\S+@\S+\.\S+$/.test(ADMIN_EMAIL)||!ADMIN_PASSWORD||ADMIN_PASSWORD.length<16||!MONGODB_URI){
 console.error("Set ADMIN_EMAIL, ADMIN_PASSWORD (min 16 characters) and MONGODB_URI before running.");process.exit(1);
}
const client=new MongoClient(MONGODB_URI,{serverSelectionTimeoutMS:10000});
try{
 await client.connect();const db=client.db(MONGODB_DB);
 await db.collection("users").createIndex({email:1},{unique:true});
 const email=ADMIN_EMAIL.trim().toLowerCase();
 const existing=await db.collection("users").findOne({email});
 if(existing){console.error("Admin bootstrap refused: account already exists. Manual identity verification and role migration required.");process.exitCode=1;}
 else {await db.collection("users").insertOne({name:"SK Ship Administrator",company:"SK Ship",email,mobile:null,passwordHash:await hash(ADMIN_PASSWORD,12),role:"admin",active:true,createdAt:new Date()});console.log("Admin created successfully.");}
}finally{await client.close()}
