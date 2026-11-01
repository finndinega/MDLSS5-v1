package com.fjdei.mdlss5

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.GLES20

class MainActivity : Activity() {
    private external fun nativeVulkanInfo(): String
    companion object {
        init { System.loadLibrary("mdlss5") }
        const val GLOBAL_CODM="com.activision.callofduty.shooter"
        const val GARENA_CODM="com.garena.game.codm"
        const val ACTION_START="com.fjdei.mdlss5.START_FLOAT"
        const val ACTION_STOP="com.fjdei.mdlss5.STOP_FLOAT"
        private const val BG=0xFF0A1020.toInt()
        private const val PANEL=0xFF17243A.toInt()
        private const val MUTED=0xFFB7C5DA.toInt()
        private const val ACCENT=0xFF54C8FF.toInt()
    }
    private lateinit var status:TextView
    private lateinit var diagnostics:TextView
    private lateinit var profileInfo:TextView
    private lateinit var overlayStatus:TextView
    private var profile="Balanced"
    private var codm:String?=null

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);buildDashboard()}
    override fun onResume(){super.onResume();if(::status.isInitialized)refreshStatus()}
    private fun label(s:String,size:Float,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply{
        text=s;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD);setPadding(0,7,0,7)
    }
    private fun btn(s:String,action:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{action()}}
    private fun card(root:LinearLayout,heading:String,body:String):TextView{
        val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,12,16,12);setBackgroundColor(PANEL)}
        wrap.addView(label(heading,16f,Color.WHITE,true))
        val t=label(body,13f,MUTED);wrap.addView(t)
        root.addView(wrap,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=12});return t
    }
    private fun buildDashboard(){
        val scroll=ScrollView(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,26,20,30);setBackgroundColor(BG)}
        root.addView(label("MDLSS5",32f,Color.WHITE,true))
        root.addView(label("GAMING CONTROL CENTER  ·  ANDROID",12f,ACCENT,true))
        status=card(root,"APP STATUS","Checking installation…")
        root.addView(label("Your gaming companion",22f,Color.WHITE,true))
        root.addView(label("Hardware-aware profiles, device diagnostics and a floating quick-control panel for Call of Duty: Mobile.",14f,MUTED))
        root.addView(label("FLOATING IN-GAME CONTROL",18f,Color.WHITE,true))
        root.addView(label("Enable the floating shortcut to keep a small MDLSS5 button visible over CODM. Android will ask you to allow Display over other apps.",13f,MUTED))
        overlayStatus=card(root,"Floating control status","Not enabled")
        root.addView(btn("1 · Allow floating window permission"){requestOverlayPermission()})
        root.addView(btn("2 · Turn ON floating control"){startFloating()})
        root.addView(btn("Turn OFF floating control"){stopFloating()})
        root.addView(label("CALL OF DUTY: MOBILE",18f,Color.WHITE,true))
        val game=card(root,"Game detection","Checking…")
        root.addView(btn("Refresh game detection"){codm=findCodm();game.text=gameStatus()})
        root.addView(btn("Launch CODM"){launchCodm()})
        root.addView(label("DEVICE-OPTIMIZED PROFILE",18f,Color.WHITE,true))
        root.addView(label("Select the behavior you want. Recommendations are intended to be applied inside CODM.",13f,MUTED))
        val group=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL}
        listOf("Performance","Balanced","Quality").forEach{p->group.addView(RadioButton(this).apply{
            text=p;textSize=15f;setTextColor(Color.WHITE);isChecked=p==profile
            setOnCheckedChangeListener{_,checked->if(checked){profile=p;updateProfile()}}
        })}
        root.addView(group)
        profileInfo=card(root,"Profile recommendations","")
        root.addView(label("HARDWARE DIAGNOSTICS",18f,Color.WHITE,true))
        diagnostics=card(root,"Phone, GPU and system status","Tap refresh to scan your phone.")
        root.addView(btn("Refresh hardware diagnostics"){refreshDiagnostics()})
        root.addView(label("IMPORTANT LIMITATION",17f,ACCENT,true))
        root.addView(label("The floating switch controls the MDLSS5 companion panel only. It cannot inject a DLSS renderer into CODM, alter the game's render pipeline, upscale its frames or generate frames. Android and CODM do not expose those controls to ordinary third-party apps. No game files are modified and no anti-cheat bypass is used.",13f,MUTED))
        root.addView(label("INSTALLATION",16f,Color.WHITE,true))
        root.addView(label("If you can open this screen, MDLSS5 is installed. Android normally does not launch an app automatically after installation; tap Open in the installer or find MDLSS5 in your app drawer.",13f,MUTED))
        scroll.addView(root);setContentView(scroll)
        codm=findCodm();game.text=gameStatus();refreshStatus();updateProfile();refreshDiagnostics()
    }
    private fun refreshStatus(){
        status.text="✓ MDLSS5 is installed and running\nVersion 2.1 · Android gaming companion"
        overlayStatus.text=if(Settings.canDrawOverlays(this)){
            if(FloatingControlService.isRunning)"Permission granted. Floating shortcut is ON." else "Permission granted. Floating shortcut is OFF."
        }else "Permission not granted. Allow it to display controls over CODM."
    }
    private fun requestOverlayPermission(){
        if(Settings.canDrawOverlays(this)){refreshStatus();return}
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+packageName)))
    }
    private fun startFloating(){
        if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"First allow Display over other apps.",Toast.LENGTH_LONG).show();requestOverlayPermission();return}
        val i=Intent(this,FloatingControlService::class.java).setAction(ACTION_START)
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
        Toast.makeText(this,"MDLSS5 floating control enabled.",Toast.LENGTH_SHORT).show()
        refreshStatus()
    }
    private fun stopFloating(){
        startService(Intent(this,FloatingControlService::class.java).setAction(ACTION_STOP))
        Toast.makeText(this,"MDLSS5 floating control disabled.",Toast.LENGTH_SHORT).show();refreshStatus()
    }
    private fun findCodm():String?{
        for(p in listOf(GARENA_CODM,GLOBAL_CODM))try{packageManager.getApplicationInfo(p,0);return p}catch(_:Exception){}
        return null
    }
    private fun gameStatus()=when(codm){
        GARENA_CODM->"✓ Garena Call of Duty: Mobile installed"
        GLOBAL_CODM->"✓ Global Call of Duty: Mobile installed"
        else->"CODM not detected. Install the official version for your region."
    }
    private fun launchCodm(){
        codm=findCodm();val i=codm?.let{packageManager.getLaunchIntentForPackage(it)}
        if(i==null)Toast.makeText(this,"CODM is not installed or cannot be launched.",Toast.LENGTH_LONG).show() else startActivity(i)
    }
    private fun updateProfile(){
        if(!::profileInfo.isInitialized)return
        val advice=when(profile){
            "Performance"->"Graphics: Low. Frame rate: highest stable option. Disable optional effects if they cause heat or stutter. Prioritize consistent frame pacing."
            "Quality"->"Graphics: High only if CODM offers it and performance remains stable. Lower frame rate one step if heat or stutter appears."
            else->"Graphics: Medium. Frame rate: High or the highest consistently stable option. Keep optional effects moderate. Best starting point for balance."
        }
        profileInfo.text=profile+" profile\n"+advice+"\n\nRecommendations only; choose these options in CODM's own settings."
    }
    private fun glInfo():String{
        var d=EGL14.EGL_NO_DISPLAY;var c=EGL14.EGL_NO_CONTEXT;var s=EGL14.EGL_NO_SURFACE
        try{
            d=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);if(d==EGL14.EGL_NO_DISPLAY)return "OpenGL unavailable"
            val v=IntArray(2);if(!EGL14.eglInitialize(d,v,0,v,1))return "EGL initialization failed"
            val ca=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,4,EGL14.EGL_SURFACE_TYPE,1,EGL14.EGL_NONE)
            val cs=arrayOfNulls<android.opengl.EGLConfig>(1);val n=IntArray(1)
            if(!EGL14.eglChooseConfig(d,ca,0,cs,0,1,n,0)||n[0]==0)return "No EGL config"
            c=EGL14.eglCreateContext(d,cs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            s=EGL14.eglCreatePbufferSurface(d,cs[0],intArrayOf(EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE),0)
            if(c==EGL14.EGL_NO_CONTEXT||s==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(d,s,s,c))return "OpenGL context unavailable"
            return "OpenGL ES: "+GLES20.glGetString(GLES20.GL_VERSION)+"\nGPU vendor: "+GLES20.glGetString(GLES20.GL_VENDOR)+"\nGPU renderer: "+GLES20.glGetString(GLES20.GL_RENDERER)
        }catch(e:Exception){return "OpenGL: "+e.message}
        finally{if(d!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(d,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(s!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(d,s);if(c!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(d,c);EGL14.eglTerminate(d)}}
    }
    private fun refreshDiagnostics(){
        val am=getSystemService(ACTIVITY_SERVICE) as ActivityManager;val mi=ActivityManager.MemoryInfo();am.getMemoryInfo(mi)
        @Suppress("DEPRECATION") val hz=windowManager.defaultDisplay.refreshRate
        val bat=registerReceiver(null,android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp=bat?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE,-1)?:-1
        val therm=if(Build.VERSION.SDK_INT>=29)getSystemService(PowerManager::class.java).currentThermalStatus.toString() else "Unavailable"
        val vulkan=try{nativeVulkanInfo()}catch(e:Exception){"Vulkan: "+e.message}
        val soc=if(Build.VERSION.SDK_INT>=31)Build.SOC_MODEL else Build.HARDWARE
        val battery=if(temp>0)(temp/10f).toString()+"°C" else "Unavailable"
        diagnostics.text="Device: "+Build.MANUFACTURER+" "+Build.MODEL+"\nSoC: "+soc+"\nAndroid: "+Build.VERSION.RELEASE+
            "\nCPU cores: "+Runtime.getRuntime().availableProcessors()+"\nRAM: "+(mi.totalMem/1048576)+" MB total · "+(mi.availMem/1048576)+
            " MB free\nDisplay: "+hz+" Hz\nBattery temperature: "+battery+"\nThermal status: "+therm+"\n"+glInfo()+"\n\n"+vulkan
    }
}