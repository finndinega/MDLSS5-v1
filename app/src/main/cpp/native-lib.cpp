#include <jni.h>
#include <android/bitmap.h>
#include <android/asset_manager_jni.h>
#include <android/asset_manager.h>
#include <cstring>
#include <cstdio>
#include <vulkan/vulkan.h>
#include "net.h"

static ncnn::Net* make_net(AAssetManager* mgr, bool use_gpu) {
    auto* net = new ncnn::Net();
    net->opt.use_vulkan_compute = use_gpu;
    net->opt.num_threads = 2;
    if (net->load_param(mgr, "models/realesrgan-x4plus.param") != 0 ||
        net->load_model(mgr, "models/realesrgan-x4plus.bin") != 0) {
        delete net; return nullptr;
    }
    return net;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_fjdei_mdlss5_MainActivity_nativeVulkanInfo(JNIEnv* env, jobject) {
    uint32_t version = VK_API_VERSION_1_0;
    auto enumerateVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
        vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
    if (enumerateVersion) enumerateVersion(&version);
    VkApplicationInfo app{}; app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "MDLSS5"; app.apiVersion = version;
    VkInstanceCreateInfo create{}; create.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create.pApplicationInfo = &app;
    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&create, nullptr, &instance);
    char buffer[256];
    if (result == VK_SUCCESS) {
        snprintf(buffer, sizeof(buffer), "Vulkan loader: available (API %u.%u.%u)",
            VK_VERSION_MAJOR(version), VK_VERSION_MINOR(version), VK_VERSION_PATCH(version));
        vkDestroyInstance(instance, nullptr);
    } else snprintf(buffer, sizeof(buffer), "Vulkan instance unavailable (VkResult %d)", (int)result);
    return env->NewStringUTF(buffer);
}
extern "C" JNIEXPORT jobject JNICALL
Java_com_fjdei_mdlss5_MainActivity_nativeSuperResolve(JNIEnv* env, jobject,
    jobject inputBitmap, jobject assetManager, jboolean requestGpu) {
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, inputBitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || !info.width || !info.height) return nullptr;
    void* inputPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, inputBitmap, &inputPixels) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    bool gpu = requestGpu == JNI_TRUE;
    if (gpu) {
        ncnn::create_gpu_instance();
        gpu = ncnn::get_gpu_count() > 0;
    }
    ncnn::Net* net = make_net(mgr, gpu);
    if (!net && gpu) {
        ncnn::destroy_gpu_instance(); gpu = false; net = make_net(mgr, false);
    }
    if (!net) { AndroidBitmap_unlockPixels(env, inputBitmap); return nullptr; }
    const int w = (int)info.width, h = (int)info.height;
    ncnn::Mat in = ncnn::Mat::from_pixels((const unsigned char*)inputPixels,
        ncnn::Mat::PIXEL_RGBA2RGB, w, h);
    AndroidBitmap_unlockPixels(env, inputBitmap);
    const float norm[3] = {1.f/255.f,1.f/255.f,1.f/255.f};
    in.substract_mean_normalize(nullptr, norm);
    ncnn::Mat out;
    ncnn::Extractor ex = net->create_extractor();
    ex.input("data", in);
    int ret = ex.extract("output", out);
    delete net;
    if (gpu) ncnn::destroy_gpu_instance();
    if (ret != 0 || out.empty() || out.w > 8192 || out.h > 8192) return nullptr;
    const float denorm[3] = {255.f,255.f,255.f};
    out.substract_mean_normalize(nullptr, denorm);
    ncnn::Mat pixels;
    out.to_pixels(pixels, ncnn::Mat::PIXEL_RGB2RGBA);
    const int ow = out.w, oh = out.h;
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argbField = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb = env->GetStaticObjectField(configClass, argbField);
    jmethodID create = env->GetStaticMethodID(bitmapClass, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject result = env->CallStaticObjectMethod(bitmapClass, create, ow, oh, argb);
    AndroidBitmapInfo outInfo{}; void* outPixels = nullptr;
    if (!result || AndroidBitmap_getInfo(env, result, &outInfo) != ANDROID_BITMAP_RESULT_SUCCESS ||
        AndroidBitmap_lockPixels(env, result, &outPixels) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;
    for (int y=0; y<oh; ++y)
        memcpy((unsigned char*)outPixels + y*outInfo.stride, pixels.row(y), ow*4);
    AndroidBitmap_unlockPixels(env, result);
    return result;
}
