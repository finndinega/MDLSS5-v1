package com.fjdei.mdlss5
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*

class FloatingControlService:Service(){
 companion object{@Volatile var isRunning=false;private const val CHANNEL="mdlss5_controls";private const val NOTIFY_ID=5105}
 private lateinit var wm:WindowManager
 private var bubble:TextView?=null
 private var panel:LinearLayout?=null
 private var enabled=true
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action==MainActivity.ACTION_STOP){removeViews();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY}
  if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
  startNotice();wm=getSystemService(WINDOW_SERVICE) as WindowManager
  if(bubble==null)showBubble()
  isRunning=true;return START_STICKY
 }
 private fun startNotice(){
  val nm=getSystemService(NotificationManager::class.java)
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(CHANNEL,"MDLSS5 floating controls",NotificationManager.IMPORTANCE_LOW))
  val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL) else Notification.Builder(this)
  val n=b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("MDLSS5 controls active").setContentText("Tap floating M5 for quick controls.").setContentIntent(pi).setOngoing(true).build()
  if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFY_ID,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIFY_ID,n)
 }
 private fun lp(w:Int,h:Int,g:Int=Gravity.TOP or Gravity.START)=WindowManager.LayoutParams(w,h,if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=g;x=18;y=180}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun showBubble(){
  val b=TextView(this).apply{
   text="M5";textSize=16f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER
   setPadding(dp(15),dp(12),dp(15),dp(12));setBackgroundColor(0xFF087DB5.toInt());setOnClickListener{togglePanel()}
   var dx=0f;var dy=0f;var ox=0;var oy=0
   setOnTouchListener{_,e->
    val p=bubble?.layoutParams as? WindowManager.LayoutParams
    when(e.action){
     MotionEvent.ACTION_DOWN->{dx=e.rawX;dy=e.rawY;ox=p?.x?:18;oy=p?.y?:180;true}
     MotionEvent.ACTION_MOVE->{if(p!=null){p.x=ox+(e.rawX-dx).toInt();p.y=oy+(e.rawY-dy).toInt();wm.updateViewLayout(this,p)};true}
     MotionEvent.ACTION_UP->{if(kotlin.math.abs(e.rawX-dx)<8&&kotlin.math.abs(e.rawY-dy)<8)performClick();true}
     else->false
    }
   }
  }
  bubble=b;try{wm.addView(b,lp(-2,-2))}catch(_:Exception){bubble=null}
 }
 private fun togglePanel(){if(panel==null)showPanel()else hidePanel()}
 private fun hidePanel(){try{panel?.let{wm.removeView(it)}}catch(_:Exception){};panel=null}
 private fun showPanel(){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));setBackgroundColor(0xFF101B2D.toInt())}
  box.addView(TextView(this).apply{text="MDLSS5 QUICK CONTROL";textSize=17f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)})
  box.addView(TextView(this).apply{text="Floating gaming companion";textSize=12f;setTextColor(0xFFB7C5DA.toInt());setPadding(0,dp(5),0,dp(8))})
  val toggle=Switch(this).apply{text="MDLSS5 shortcut ON";setTextColor(Color.WHITE);isChecked=enabled}
  toggle.setOnCheckedChangeListener{_,checked->enabled=checked;bubble?.text=if(checked)"M5 ON" else "M5 OFF";toggle.text=if(checked)"MDLSS5 shortcut ON" else "MDLSS5 shortcut OFF"}
  box.addView(toggle)
  box.addView(Button(this).apply{
   text="Launch Call of Duty: Mobile";isAllCaps=false
   setOnClickListener{
    val pkg=listOf(MainActivity.GARENA_CODM,MainActivity.GLOBAL_CODM).firstOrNull{p->try{packageManager.getApplicationInfo(p,0);true}catch(_:Exception){false}}
    val game=pkg?.let{packageManager.getLaunchIntentForPackage(it)}
    if(game!=null){game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(game)}else Toast.makeText(this@FloatingControlService,"CODM not detected",Toast.LENGTH_SHORT).show()
   }
  })
  box.addView(Button(this).apply{text="Open full dashboard";isAllCaps=false;setOnClickListener{startActivity(Intent(this@FloatingControlService,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}})
  box.addView(Button(this).apply{text="Close control panel";isAllCaps=false;setOnClickListener{hidePanel()}})
  panel=box;try{val pp=lp(dp(290),-2,Gravity.TOP or Gravity.END);pp.flags=pp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv();wm.addView(box,pp)}catch(_:Exception){panel=null}
 }
 private fun removeViews(){hidePanel();try{bubble?.let{wm.removeView(it)}}catch(_:Exception){};bubble=null;isRunning=false}
 override fun onDestroy(){removeViews();super.onDestroy()}
}