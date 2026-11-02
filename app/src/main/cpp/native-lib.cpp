#include <jni.h>
#include <vulkan/vulkan.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdint>
#include <vector>
extern "C" JNIEXPORT jstring JNICALL Java_com_fjdei_mdlss5_MainActivity_nativeVulkanInfo(JNIEnv* env,jobject){
 VkApplicationInfo a{};a.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO;a.pApplicationName="MDLSS5";a.apiVersion=VK_API_VERSION_1_0;
 VkInstanceCreateInfo c{};c.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;c.pApplicationInfo=&a;VkInstance in=VK_NULL_HANDLE;
 if(vkCreateInstance(&c,nullptr,&in)!=VK_SUCCESS)return env->NewStringUTF("Vulkan instance creation failed.");
 uint32_t n=0;vkEnumeratePhysicalDevices(in,&n,nullptr);if(!n){vkDestroyInstance(in,nullptr);return env->NewStringUTF("No Vulkan GPU exposed.");}
 std::vector<VkPhysicalDevice>d(n);vkEnumeratePhysicalDevices(in,&n,d.data());char b[2048];int u=snprintf(b,sizeof(b),"Vulkan devices detected: %u\n",n);
 for(uint32_t i=0;i<n&&u<(int)sizeof(b)-256;i++){VkPhysicalDeviceProperties p{};vkGetPhysicalDeviceProperties(d[i],&p);u+=snprintf(b+u,sizeof(b)-u,"%s | Vulkan %u.%u.%u\n",p.deviceName,VK_VERSION_MAJOR(p.apiVersion),VK_VERSION_MINOR(p.apiVersion),VK_VERSION_PATCH(p.apiVersion));}
 vkDestroyInstance(in,nullptr);return env->NewStringUTF(b);
}
extern "C" JNIEXPORT jintArray JNICALL Java_com_fjdei_mdlss5_GraphicsLabActivity_nativeEnhance(JNIEnv*e,jobject,jintArray input,jint w,jint h,jint scale,jfloat sharp,jfloat contrast){
 if(!input||w<2||h<2||(scale!=2&&scale!=3))return nullptr;jsize n=e->GetArrayLength(input);if(n!=w*h||(int64_t)w*h*scale*scale>12000000)return nullptr;
 jint*s=e->GetIntArrayElements(input,nullptr);if(!s)return nullptr;int ow=w*scale,oh=h*scale;std::vector<float>base((size_t)ow*oh*3);
 auto ch=[](jint p,int sh){return(float)((p>>sh)&255);};
 for(int y=0;y<oh;y++)for(int x=0;x<ow;x++){float fx=(float)x/scale,fy=(float)y/scale;int x0=(int)fx,y0=(int)fy,x1=std::min(x0+1,w-1),y1=std::min(y0+1,h-1);float tx=fx-x0,ty=fy-y0;jint p[4]={s[y0*w+x0],s[y0*w+x1],s[y1*w+x0],s[y1*w+x1]};size_t k=((size_t)y*ow+x)*3;
 for(int c=0;c<3;c++){int sh=c==0?16:(c==1?8:0);float a=ch(p[0],sh)*(1-tx)+ch(p[1],sh)*tx,b=ch(p[2],sh)*(1-tx)+ch(p[3],sh)*tx;base[k+c]=a*(1-ty)+b*ty;}}
 e->ReleaseIntArrayElements(input,s,JNI_ABORT);std::vector<jint>out((size_t)ow*oh);float amt=std::clamp((float)sharp,0.f,2.f),gain=std::clamp((float)contrast,.5f,1.8f);
 for(int y=0;y<oh;y++)for(int x=0;x<ow;x++){size_t k=((size_t)y*ow+x)*3;unsigned rgb[3];for(int c=0;c<3;c++){float sum=0;int cnt=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=std::clamp(x+dx,0,ow-1),yy=std::clamp(y+dy,0,oh-1);sum+=base[((size_t)yy*ow+xx)*3+c];cnt++;}float center=base[k+c],detail=center-sum/cnt,edge=std::min(1.f,std::abs(detail)/32.f);rgb[c]=(unsigned)std::clamp((center+detail*amt*edge-127.5f)*gain+127.5f,0.f,255.f);}
 out[(size_t)y*ow+x]=(jint)(0xFF000000u|(rgb[0]<<16)|(rgb[1]<<8)|rgb[2]);}
 jintArray r=e->NewIntArray((jsize)out.size());if(r)e->SetIntArrayRegion(r,0,(jsize)out.size(),out.data());return r;
}