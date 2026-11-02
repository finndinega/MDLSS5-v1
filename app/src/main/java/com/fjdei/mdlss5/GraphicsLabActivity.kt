package com.fjdei.mdlss5
import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
class GraphicsLabActivity:Activity(){
 private external fun nativeEnhance(pixels:IntArray,width:Int,height:Int,scale:Int,sharpness:Float,contrast:Float):IntArray?
 private lateinit var preview:ImageView;private lateinit var info:TextView
 private var source:Bitmap?=null;private var scale=2;private var sharp=1f;private var contrast=1f
 companion object{init{System.loadLibrary("mdlss5")}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);buildUi();loadDemo()}
 private fun label(s:String,size:Float=15f,color:Int=Color.WHITE)=TextView(this).apply{text=s;textSize=size;setTextColor(color);setPadding(0,7,0,7)}
 private fun buildUi(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,24);setBackgroundColor(Color.rgb(10,16,32))}
 root.addView(label("MDLSS5 · GRAPHICS LAB",24f));root.addView(label("Native spatial reconstruction prototype. Sliders change the processed pixels in real time.",14f,Color.LTGRAY))
 preview=ImageView(this).apply{adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER};root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
 info=label("Preparing…",12f,Color.CYAN);root.addView(info)
 root.addView(Button(this).apply{text="Load a photo";setOnClickListener{startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"),41)}})
 root.addView(label("Reconstruction scale"))
 root.addView(Spinner(this).apply{adapter=ArrayAdapter(this@GraphicsLabActivity,android.R.layout.simple_spinner_dropdown_item,listOf("2× upscale","3× upscale"));onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){scale=if(position==0)2 else 3;render()}}})
 addSlider(root,"Detail enhancement",0,200,100){sharp=it/100f;render()};addSlider(root,"Local contrast",50,180,100){contrast=it/100f;render()}
 root.addView(Button(this).apply{text="Reset demo scene";setOnClickListener{loadDemo()}})
 root.addView(label("Status: spatial upscaling + edge-aware sharpening + contrast. Not yet a trained AI model, Vulkan compute pipeline, or universal game renderer.",12f,Color.LTGRAY));setContentView(root)}
 private fun addSlider(root:LinearLayout,title:String,min:Int,max:Int,initial:Int,changed:(Int)->Unit){root.addView(label(title,14f));root.addView(SeekBar(this).apply{this.max=max-min;progress=initial-min;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){changed(p+min)};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}})})}
 private fun loadDemo(){val w=320;val h=180;val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(Color.rgb(25,38,58));val p=Paint(Paint.ANTI_ALIAS_FLAG);p.color=Color.rgb(75,110,145);c.drawRect(0f,0f,w.toFloat(),90f,p);p.color=Color.rgb(210,150,85);c.drawCircle(250f,45f,22f,p);p.color=Color.rgb(25,32,42);c.drawRect(0f,125f,320f,180f,p);for(i in 0..11){val x=i*29f;p.color=Color.rgb(35+i*5,48+i*4,66+i*3);c.drawRect(x,75f,x+18,145f,p);p.color=Color.rgb(220,190,110);for(y in 0..3)c.drawRect(x+4,y*14f+82,x+8,y*14f+86,p)};p.color=Color.rgb(60,125,80);c.drawRect(0f,145f,320f,180f,p);source=b;render()}
 private fun render(){val src=source?:return;val w=src.width.coerceAtMost(720);val h=(src.height.toFloat()*w/src.width).toInt().coerceAtLeast(2);val small=if(w!=src.width)Bitmap.createScaledBitmap(src,w,h,true)else src;val pixels=IntArray(w*h);small.getPixels(pixels,0,w,0,0,w,h);val output=try{nativeEnhance(pixels,w,h,scale,sharp,contrast)}catch(e:Exception){null};if(output==null){info.text="Native processing failed.";return};preview.setImageBitmap(Bitmap.createBitmap(output,w*scale,h*scale,Bitmap.Config.ARGB_8888));info.text="Processed "+w+"×"+h+" → "+(w*scale)+"×"+(h*scale)+" | scale "+scale+"× | detail "+String.format("%.1f",sharp)+" | contrast "+String.format("%.2f",contrast)}
 @Deprecated("Legacy image picker callback") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==41&&resultCode==RESULT_OK){try{val uri:Uri=data?.data?:return;source=contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it)};render()}catch(e:Exception){info.text="Could not load photo: "+e.message}}}
}